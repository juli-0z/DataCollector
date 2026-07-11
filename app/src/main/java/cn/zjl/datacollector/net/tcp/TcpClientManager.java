package cn.zjl.datacollector.net.tcp;

/**
 * 阅读提示：TCP 连接管理器：负责真实设备 Socket、模拟设备模式、报文收发线程以及现场诊断信息记录。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import javax.net.SocketFactory;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

/**
 * 设备 TCP 通信管理器，负责：
 * 1. 真机 / 模拟设备连接
 * 2. 0x8000 协议包收发、帧头/长度/校验处理
 * 3. 基础协议状态机维护
 * 4. TCP 联调诊断数据上报
 *
 * <p>阅读建议：先看 {@link #connect()} 和 {@link #sendCommand(int, byte[])}，
 * 再看接收线程如何通过 {@link GeoDspProtocol#tryParse(byte[], int)} 从 TCP 字节流中拆出完整帧。</p>
 */
public class TcpClientManager {

    private static final String TAG = "TcpClientManager";

    private static final int CMD_START_COLLECTION = GeoDspProtocol.MSG_TEM_START;
    private static final int CMD_STOP_COLLECTION = GeoDspProtocol.MSG_TEM_STOP;
    private static final int CMD_SET_PARAMETERS = GeoDspProtocol.MSG_TEM_CONFIG;
    private static final int CMD_GET_STATUS = GeoDspProtocol.MSG_DEVICE_INFO;
    private static final int CMD_WAVEFORM_RECV = GeoDspProtocol.MSG_TEM_SEND_DATA;
    private static final int CMD_WAVEFORM_SEND = GeoDspProtocol.MSG_TEM_GET_SEND_DATA;
    private static final int CMD_WAVEFORM_OFF = GeoDspProtocol.MSG_TEM_GET_OFF_DATA;
    private static final int CMD_DEVICE_STATUS = GeoDspProtocol.MSG_TEM_SEND_MONITOR;

    private static final int STATUS_PAYLOAD_MAGIC = 0x44534D31;
    private static final int STATUS_PAYLOAD_VERSION = 1;
    private static final int EXTENDED_STATUS_PAYLOAD_LENGTH = 72;

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int RECONNECT_DELAY_MS = 2000;

    private static final long SIMULATED_CONNECT_DELAY_MS = 350L;
    private static final long SIMULATED_FRAME_INTERVAL_MS = 320L;
    private static final String SIMULATED_TARGET = "内置模拟设备";

    public enum ConnectionState {
        /** 未建立 Socket。 */
        DISCONNECTED,
        /** 正在建立 Socket。 */
        CONNECTING,
        /** Socket 已连接，可以收发二进制报文。 */
        CONNECTED,
        /** 连接异常后正在自动重连。 */
        RECONNECTING
    }

    public enum ProtocolState {
        IDLE(0, "空闲"),
        REQUESTING_STATUS(1, "查询状态"),
        READY(2, "就绪"),
        CONFIGURING(3, "下发参数"),
        STARTING(4, "启动采集"),
        COLLECTING(5, "采集中"),
        STOPPING(6, "停止中"),
        ERROR(7, "异常");

        private final int code;
        private final String displayName;

        ProtocolState(int code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public int getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static ProtocolState fromCode(int code) {
            for (ProtocolState state : values()) {
                if (state.code == code) {
                    return state;
                }
            }
            return IDLE;
        }
    }

    public enum HandshakeStage {
        IDLE("未开始"),
        SOCKET_CONNECTING("建立 Socket"),
        SOCKET_CONNECTED("Socket 已连接"),
        WAITING_STATUS_FRAME("等待状态帧"),
        READY("设备就绪"),
        PARAMETERS_SENT("已下发参数"),
        START_COMMAND_SENT("已发送启动采集"),
        COLLECTING("采集中"),
        STOP_COMMAND_SENT("已发送停止采集"),
        ERROR("异常");

        private final String displayName;

        HandshakeStage(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public interface ConnectionListener {
        void onConnectionStateChanged(ConnectionState state);

        /** 收到完整 0x8000 帧后回调给 CollectionManager 继续解析业务 payload。 */
        void onDataReceived(byte[] data);

        void onError(String error);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Socket socket;
    private BufferedSource bufferedSource;
    private BufferedSink bufferedSink;

    private String serverIp = "";
    private int serverPort;
    @Nullable
    private SocketFactory socketFactory;

    private volatile boolean isRunning;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private volatile ProtocolState protocolState = ProtocolState.IDLE;
    private volatile HandshakeStage handshakeStage = HandshakeStage.IDLE;

    private ConnectionListener listener;
    private Thread receiveThread;
    private Runnable reconnectRunnable;
    private boolean reconnectScheduled;
    private int reconnectAttempts;

    private boolean simulatedDeviceEnabled;
    private Runnable simulatedConnectRunnable;
    private Runnable simulatedCollectionRunnable;
    private byte[] simulatedParameters = new byte[0];
    private boolean simulatedCollectionActive;
    private int simulatedFrameIndex;
    private byte[] receiveFrameBuffer = new byte[8192];
    private int receiveFrameBufferLength;
    private int nextOutgoingMessageNo;
    private int nextSimulatedMessageNo;

    public void setConnectionParams(String ip, int port) {
        serverIp = safeText(ip);
        serverPort = port;
        refreshDiagnosticsTarget();
    }

    public void setSimulatedDeviceEnabled(boolean enabled) {
        simulatedDeviceEnabled = enabled;
        refreshDiagnosticsTarget();
    }

    public void setSocketFactory(@Nullable SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
    }

    public boolean isSimulatedDeviceEnabled() {
        return simulatedDeviceEnabled;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public void connect() {
        if (connectionState == ConnectionState.CONNECTED
                || connectionState == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connected or connecting");
            return;
        }

        if (simulatedDeviceEnabled) {
            // 模拟模式不创建真实 Socket，而是在主线程定时生成状态帧和波形帧，方便无真机演示。
            connectSimulated();
            return;
        }

        if (serverIp.isEmpty() || serverPort <= 0) {
            notifyError("IP 地址或端口无效");
            return;
        }

        isRunning = true;
        resetProtocolBuffers();
        updateProtocolState(ProtocolState.IDLE);
        updateHandshakeStage(HandshakeStage.SOCKET_CONNECTING);
        updateConnectionState(ConnectionState.CONNECTING);
        openSocketAsync();
    }

    private void openSocketAsync() {
        new Thread(() -> {
            try {
                socket = socketFactory != null
                        ? socketFactory.createSocket()
                        : new Socket();
                socket.setSoTimeout(TIMEOUT_MS);
                // 使用 connect timeout 避免设备热点不可达时界面长时间无响应。
                socket.connect(new InetSocketAddress(serverIp, serverPort), TIMEOUT_MS);

                Source source = Okio.source(socket);
                Sink sink = Okio.sink(socket);
                bufferedSource = Okio.buffer(source);
                bufferedSink = Okio.buffer(sink);

                reconnectAttempts = 0;
                onConnected(serverIp + ":" + serverPort);
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                closeStreams();
                updateProtocolState(ProtocolState.ERROR);
                if (isRunning && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    // 连接失败时最多自动重试 3 次，现场网络波动时能自动恢复。
                    attemptReconnect();
                } else {
                    isRunning = false;
                    updateConnectionState(ConnectionState.DISCONNECTED);
                    notifyError("连接失败：" + safeTextOrDefault(e.getMessage(), e.getClass().getSimpleName()));
                }
            }
        }, "tcp-connect").start();
    }

    private void connectSimulated() {
        cancelReconnectTask();
        cancelSimulatedTasks();
        simulatedCollectionActive = false;
        simulatedFrameIndex = 0;
        reconnectAttempts = 0;
        isRunning = true;
        resetProtocolBuffers();
        refreshDiagnosticsTarget();
        updateProtocolState(ProtocolState.IDLE);
        updateHandshakeStage(HandshakeStage.SOCKET_CONNECTING);
        updateConnectionState(ConnectionState.CONNECTING);
        TcpDiagnosticsStore.getInstance().recordEvent("已启用采集设备模拟模式");
        simulatedConnectRunnable = () -> {
            simulatedConnectRunnable = null;
            if (!isRunning) {
                return;
            }
            onConnected(SIMULATED_TARGET);
        };
        mainHandler.postDelayed(simulatedConnectRunnable, SIMULATED_CONNECT_DELAY_MS);
    }

    private void onConnected(String targetLabel) {
        updateConnectionState(ConnectionState.CONNECTED);
        updateHandshakeStage(HandshakeStage.SOCKET_CONNECTED);
        updateProtocolState(ProtocolState.REQUESTING_STATUS);
        TcpDiagnosticsStore.getInstance().recordEvent("连接成功 " + targetLabel);
        startReceiveThread();
        // 建立连接后立即查询一次设备状态，用来验证链路和协议是否真正可用。
        sendCommand(CMD_GET_STATUS, null);
    }

    private void attemptReconnect() {
        reconnectAttempts++;
        TcpDiagnosticsStore.getInstance().recordReconnectAttempt(
                reconnectAttempts,
                MAX_RECONNECT_ATTEMPTS);
        updateProtocolState(ProtocolState.IDLE);
        updateConnectionState(ConnectionState.RECONNECTING);
        cancelReconnectTask();
        reconnectRunnable = () -> {
            reconnectScheduled = false;
            if (!isRunning || connectionState == ConnectionState.CONNECTED) {
                return;
            }
            if (simulatedDeviceEnabled) {
                connectSimulated();
            } else {
                connect();
            }
        };
        reconnectScheduled = true;
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
    }

    private void startReceiveThread() {
        if (receiveThread != null && receiveThread.isAlive()) {
            return;
        }
        receiveThread = new Thread(this::receiveDataLoop, "tcp-receive");
        receiveThread.start();
    }

    private void receiveDataLoop() {
        byte[] chunk = new byte[4096];
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                if (bufferedSource == null) {
                    return;
                }

                int bytesRead = bufferedSource.read(chunk);
                if (bytesRead == -1) {
                    if (isRunning) {
                        handleDisconnection();
                    }
                    return;
                }
                if (bytesRead == 0) {
                    continue;
                }
                // TCP 只保证字节顺序，不保证一次 read 就是一帧，所以先追加到接收缓冲区。
                appendReceivedBytes(chunk, bytesRead);
                drainReceivedFrames();
            } catch (SocketTimeoutException ignored) {
                // 读超时不是连接失败，继续等待下一段设备数据。
            } catch (IOException e) {
                Log.e(TAG, "Receive error", e);
                if (isRunning) {
                    handleFrameAnomaly(
                            "Receive error: " + safeTextOrDefault(e.getMessage(), e.getClass().getSimpleName()),
                            "接收 I/O 异常",
                            null,
                            false,
                            true);
                }
                return;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in receive loop", e);
                handleFrameAnomaly(
                        "Unexpected receive error: " + safeTextOrDefault(e.getMessage(), e.getClass().getSimpleName()),
                        "报文解析异常",
                        null,
                        false,
                        true);
                return;
            }
        }
    }

    private void appendReceivedBytes(byte[] chunk, int bytesRead) {
        ensureReceiveBufferCapacity(receiveFrameBufferLength + bytesRead);
        System.arraycopy(chunk, 0, receiveFrameBuffer, receiveFrameBufferLength, bytesRead);
        receiveFrameBufferLength += bytesRead;
    }

    private void ensureReceiveBufferCapacity(int requiredLength) {
        if (requiredLength <= receiveFrameBuffer.length) {
            return;
        }
        // 采用倍增扩容，兼顾大帧和频繁小包的性能。
        int newLength = receiveFrameBuffer.length;
        while (newLength < requiredLength) {
            newLength *= 2;
        }
        byte[] newBuffer = new byte[newLength];
        System.arraycopy(receiveFrameBuffer, 0, newBuffer, 0, receiveFrameBufferLength);
        receiveFrameBuffer = newBuffer;
    }

    private void drainReceivedFrames() {
        while (receiveFrameBufferLength > 0) {
            GeoDspProtocol.ParseResult result =
                    GeoDspProtocol.tryParse(receiveFrameBuffer, receiveFrameBufferLength);
            if (result.needsMoreData) {
                // 当前缓冲区还不够一帧，等待下一次 socket read 继续补齐。
                return;
            }
            if (result.packet != null) {
                // 成功拆出一帧后，先从缓冲区移除对应字节，再把完整帧交给业务层。
                consumeReceiveBytes(result.bytesConsumed);
                handleIncomingPacket(result.packet);
                continue;
            }
            if (result.error != null && !result.error.isEmpty()) {
                handleParsedFrameAnomaly(result);
            }
            if (result.bytesConsumed <= 0) {
                return;
            }
            consumeReceiveBytes(result.bytesConsumed);
        }
    }

    private void consumeReceiveBytes(int byteCount) {
        if (byteCount <= 0) {
            return;
        }
        if (byteCount >= receiveFrameBufferLength) {
            receiveFrameBufferLength = 0;
            return;
        }
        System.arraycopy(receiveFrameBuffer, byteCount, receiveFrameBuffer, 0, receiveFrameBufferLength - byteCount);
        receiveFrameBufferLength -= byteCount;
    }

    private void handleIncomingPacket(GeoDspProtocol.Packet packet) {
        String label = commandLabel(packet.id);
        String summary = String.format(
                Locale.US,
                "RX %s %s(0x%02X) no=%d payload=%dB parity OK",
                GeoDspProtocol.labelForType(packet.type),
                label,
                packet.id,
                packet.no,
                packet.payload.length);
        TcpDiagnosticsStore.getInstance().recordIncomingPacket(
                summary,
                TcpDiagnosticsStore.formatHexPreview(packet.frameBytes),
                label,
                isStatusMessage(packet.id),
                isWaveformMessage(packet.id));
        // 更新本地协议状态机后，再通知 CollectionManager 解析业务数据。
        onIncomingCommand(packet.id);
        notifyDataReceived(packet.frameBytes);
    }

    private void handleParsedFrameAnomaly(GeoDspProtocol.ParseResult result) {
        if (result.error == null || result.error.isEmpty()) {
            return;
        }
        if (result.error.startsWith("Discarded ")) {
            // 前导噪声不一定是严重错误，只记录事件，避免诊断页被无效字节刷屏。
            TcpDiagnosticsStore.getInstance().recordEvent(result.error);
            return;
        }
        handleFrameAnomaly(
                result.error,
                result.error,
                result.previewBytes,
                result.checksumError,
                false);
    }

    private void handleDisconnection() {
        closeStreams();
        updateProtocolState(ProtocolState.ERROR);
        if (isRunning && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            attemptReconnect();
        } else {
            isRunning = false;
            updateConnectionState(ConnectionState.DISCONNECTED);
            notifyError("连接断开");
        }
    }

    public void sendData(byte[] data) {
        if (!isConnected()) {
            notifyError("未连接到设备");
            return;
        }

        byte[] safeData = data == null ? new byte[0] : data;

        if (simulatedDeviceEnabled) {
            TcpDiagnosticsStore.getInstance().recordEvent(
                    "模拟设备忽略原始数据发送 " + safeData.length + "B");
            return;
        }

        new Thread(() -> {
            try {
                if (bufferedSink == null) {
                    notifyError("未连接到设备");
                    return;
                }
                bufferedSink.write(safeData);
                bufferedSink.flush();
                TcpDiagnosticsStore.getInstance().recordEvent(
                        "发送原始数据 " + safeData.length + "B");
            } catch (IOException e) {
                Log.e(TAG, "Send error", e);
                updateProtocolState(ProtocolState.ERROR);
                notifyError("发送失败：" + safeTextOrDefault(e.getMessage(), e.getClass().getSimpleName()));
            }
        }, "tcp-send-raw").start();
    }

    public void sendCommand(int commandType, @Nullable byte[] payload) {
        if (!isConnected()) {
            notifyError("未连接到设备，无法发送命令");
            return;
        }

        updateProtocolStateForCommand(commandType);
        byte[] safePayload = payload == null ? new byte[0] : payload.clone();
        int messageNo = nextOutgoingMessageNo++;
        // 所有业务命令统一套 0x8000 外层帧，便于和 GeoDsp_Android/设备端协议保持一致。
        byte[] protocolPacket = GeoDspProtocol.encodeRequest(commandType, messageNo, safePayload);
        String summary = String.format(
                Locale.US,
                "TX %s %s(0x%02X) no=%d payload=%dB",
                GeoDspProtocol.labelForType(GeoDspProtocol.TYPE_REQUEST),
                commandLabel(commandType),
                commandType,
                messageNo,
                safePayload.length);
        TcpDiagnosticsStore.getInstance().recordOutgoingCommand(
                summary,
                TcpDiagnosticsStore.formatHexPreview(protocolPacket),
                commandLabel(commandType));

        if (simulatedDeviceEnabled) {
            handleSimulatedCommand(commandType, safePayload);
            return;
        }

        new Thread(() -> {
            try {
                if (bufferedSink == null) {
                    notifyError("未连接到设备，无法发送命令");
                    return;
                }
                bufferedSink.write(protocolPacket);
                bufferedSink.flush();
                Log.d(TAG, "Command sent: " + summary);
            } catch (IOException e) {
                Log.e(TAG, "Failed to send command", e);
                updateProtocolState(ProtocolState.ERROR);
                notifyError("发送命令失败：" + safeTextOrDefault(e.getMessage(), e.getClass().getSimpleName()));
            }
        }, "tcp-send-command").start();
    }

    private void handleSimulatedCommand(int commandType, byte[] payload) {
        switch (commandType) {
            case CMD_SET_PARAMETERS:
                simulatedParameters = payload == null ? new byte[0] : payload.clone();
                TcpDiagnosticsStore.getInstance().recordEvent("模拟设备已接收采集参数");
                dispatchSimulatedStatusFrame();
                break;
            case CMD_START_COLLECTION:
                TcpDiagnosticsStore.getInstance().recordEvent("模拟采集已开始");
                startSimulatedCollection();
                break;
            case CMD_STOP_COLLECTION:
                TcpDiagnosticsStore.getInstance().recordEvent("模拟采集已停止");
                stopSimulatedCollection();
                dispatchSimulatedStatusFrame();
                break;
            case CMD_GET_STATUS:
                dispatchSimulatedStatusFrame();
                break;
            default:
                TcpDiagnosticsStore.getInstance().recordEvent(
                        "模拟设备收到未处理命令 " + commandLabel(commandType));
                break;
        }
    }

    private void startSimulatedCollection() {
        stopSimulatedCollection();
        simulatedCollectionActive = true;
        simulatedFrameIndex = 0;
        simulatedCollectionRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning || !simulatedCollectionActive || connectionState != ConnectionState.CONNECTED) {
                    return;
                }
                emitSimulatedCollectionFrame();
                mainHandler.postDelayed(this, SIMULATED_FRAME_INTERVAL_MS);
            }
        };
        mainHandler.post(simulatedCollectionRunnable);
    }

    private void stopSimulatedCollection() {
        simulatedCollectionActive = false;
        if (simulatedCollectionRunnable != null) {
            mainHandler.removeCallbacks(simulatedCollectionRunnable);
            simulatedCollectionRunnable = null;
        }
    }

    private void emitSimulatedCollectionFrame() {
        SimulatedCollectionConfig config = decodeSimulatedConfig(simulatedParameters);
        dispatchIncomingPacket(CMD_DEVICE_STATUS, buildSimulatedDeviceStatusPayload(config, simulatedFrameIndex));

        float[] recvTimes = buildRecvTimesUs(config);
        dispatchIncomingPacket(
                CMD_WAVEFORM_RECV,
                buildWaveformPayload(0, recvTimes, buildRecvValues(config, simulatedFrameIndex)));

        float[] sendTimes = buildUniformTimesUs(48, Math.max(4f, config.sampleTimeUs * 0.5f));
        dispatchIncomingPacket(
                CMD_WAVEFORM_SEND,
                buildWaveformPayload(1, sendTimes, buildSendValues(config, simulatedFrameIndex)));

        float[] offTimes = buildUniformTimesUs(64, Math.max(4f, config.sampleTimeUs));
        dispatchIncomingPacket(
                CMD_WAVEFORM_OFF,
                buildWaveformPayload(2, offTimes, buildOffValues(config, simulatedFrameIndex)));

        simulatedFrameIndex++;
    }

    private void dispatchSimulatedStatusFrame() {
        dispatchIncomingPacket(
                CMD_DEVICE_STATUS,
                buildSimulatedDeviceStatusPayload(
                        decodeSimulatedConfig(simulatedParameters),
                        simulatedFrameIndex));
    }

    private void dispatchIncomingPacket(int commandType, byte[] payload) {
        int messageNo = nextSimulatedMessageNo++;
        byte[] protocolPacket = GeoDspProtocol.encodeAnswer(commandType, messageNo, payload);
        String summary = String.format(
                Locale.US,
                "RX %s %s(0x%02X) no=%d payload=%dB [SIM]",
                GeoDspProtocol.labelForType(GeoDspProtocol.TYPE_ANSWER),
                commandLabel(commandType),
                commandType,
                messageNo,
                payload == null ? 0 : payload.length);
        TcpDiagnosticsStore.getInstance().recordIncomingPacket(
                summary,
                TcpDiagnosticsStore.formatHexPreview(protocolPacket),
                commandLabel(commandType),
                isStatusMessage(commandType),
                isWaveformMessage(commandType));
        onIncomingCommand(commandType);
        notifyDataReceived(protocolPacket);
    }

    private byte[] buildSimulatedDeviceStatusPayload(SimulatedCollectionConfig config, int frameIndex) {
        TcpDiagnosticsStore.Snapshot snapshot = TcpDiagnosticsStore.getInstance().getSnapshot();
        float batteryVoltage = 12.1f - 0.03f * (frameIndex % 6);
        float current = Math.max(0.2f, config.transmitCurrent * 0.92f);
        float temperature = 27.5f + (frameIndex % 8) * 0.35f;
        float signalStrength = 82f + (float) Math.sin(frameIndex * 0.4f) * 6f;
        float gpsAccuracy = 1.6f + (frameIndex % 5) * 0.4f;
        float sendCurrent = Math.max(0.2f, config.transmitCurrent);
        float offTime = Math.max(1f, config.sampleTimeUs * 64f);
        float dataRate = simulatedCollectionActive ? Math.max(1f, config.sampleFrequency * 0.18f) : 0f;
        float packetLoss = simulatedCollectionActive
                ? Math.max(0f, (float) Math.sin(frameIndex * 0.35f)) * 0.6f
                : 0f;
        float batteryLevel = clampFloat((batteryVoltage - 10.8f) / 1.8f * 100f, 0f, 100f);

        ByteBuffer buffer = ByteBuffer.allocate(EXTENDED_STATUS_PAYLOAD_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(STATUS_PAYLOAD_MAGIC);
        buffer.putInt(STATUS_PAYLOAD_VERSION);
        buffer.putFloat(batteryVoltage);
        buffer.putFloat(current);
        buffer.putFloat(temperature);
        buffer.putFloat(signalStrength);
        buffer.putFloat(gpsAccuracy);
        buffer.putFloat(sendCurrent);
        buffer.putFloat(offTime);
        buffer.putFloat(dataRate);
        buffer.putFloat(packetLoss);
        buffer.putFloat(batteryLevel);
        buffer.putInt(protocolState.getCode());
        buffer.putInt(resolveSimulatedSystemStatus());
        buffer.putInt(snapshot.statusFrameCount);
        buffer.putInt(snapshot.waveformFrameCount);
        buffer.putLong(System.currentTimeMillis());
        return buffer.array();
    }

    private byte[] buildWaveformPayload(int waveformType, float[] timeUs, float[] values) {
        int sampleCount = Math.min(timeUs.length, values.length);
        ByteBuffer buffer = ByteBuffer.allocate(8 + sampleCount * 12).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(waveformType);
        buffer.putInt(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            buffer.putDouble(timeUs[i]);
            buffer.putFloat(values[i]);
        }
        return buffer.array();
    }

    private float[] buildRecvTimesUs(SimulatedCollectionConfig config) {
        int pointCount = clamp(Math.max(config.sampleFrequency / 4, 72), 72, 180);
        return buildUniformTimesUs(pointCount, Math.max(5f, config.sampleTimeUs));
    }

    private float[] buildUniformTimesUs(int count, float stepUs) {
        float[] times = new float[Math.max(0, count)];
        for (int i = 0; i < times.length; i++) {
            times[i] = (i + 1) * stepUs;
        }
        return times;
    }

    private float[] buildRecvValues(SimulatedCollectionConfig config, int frameIndex) {
        int count = clamp(Math.max(config.sampleFrequency / 4, 72), 72, 180);
        float[] values = new float[count];
        double amplitude = Math.max(0.08d, config.transmitCurrent * 0.04d);
        for (int i = 0; i < count; i++) {
            double decay = Math.exp(-i / Math.max(18d, count / 3.8d));
            double ripple = 1.0d + 0.12d * Math.sin((i + frameIndex * 1.2d) * 0.42d);
            double noise = 0.02d * Math.sin(i * 1.35d + frameIndex * 0.5d);
            values[i] = (float) Math.max(1.0e-6d, amplitude * decay * ripple + amplitude * noise);
        }
        return values;
    }

    private float[] buildSendValues(SimulatedCollectionConfig config, int frameIndex) {
        int count = 48;
        float[] values = new float[count];
        float peak = Math.max(1f, config.transmitCurrent);
        for (int i = 0; i < count; i++) {
            if (i < 10) {
                values[i] = peak;
            } else if (i < 18) {
                values[i] = peak * 0.55f;
            } else if (i < 24) {
                values[i] = -peak * 0.18f;
            } else if (i < 30) {
                values[i] = peak * 0.08f * (float) Math.sin((i - 24 + frameIndex) * 0.8f);
            } else {
                values[i] = 0f;
            }
        }
        return values;
    }

    private float[] buildOffValues(SimulatedCollectionConfig config, int frameIndex) {
        int count = 64;
        float[] values = new float[count];
        float baseAmplitude = Math.max(0.02f, config.transmitCurrent * 0.012f);
        for (int i = 0; i < count; i++) {
            double decay = Math.exp(-i / 14d);
            double oscillation = Math.sin((i + frameIndex * 0.5d) * 0.55d);
            values[i] = (float) (baseAmplitude * decay * oscillation);
        }
        return values;
    }

    private SimulatedCollectionConfig decodeSimulatedConfig(@Nullable byte[] payload) {
        SimulatedCollectionConfig config = new SimulatedCollectionConfig();
        if (payload == null || payload.length < 20) {
            return config;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            config.transmitCurrent = sanitizePositive(buffer.getFloat(), config.transmitCurrent);
            config.sampleFrequency = sanitizePositive(buffer.getInt(), config.sampleFrequency);
            config.collectionCount = sanitizePositive(buffer.getInt(), config.collectionCount);
            config.sampleTimeUs = sanitizePositive(buffer.getFloat(), config.sampleTimeUs);
            config.electrodeDistance = sanitizePositive(buffer.getFloat(), config.electrodeDistance);
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode simulated parameters", e);
        }
        return config;
    }

    private float sanitizePositive(float value, float fallback) {
        return Float.isFinite(value) && value > 0f ? value : fallback;
    }

    private int sanitizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void disconnect() {
        Log.i(TAG, "Disconnecting...");
        isRunning = false;
        stopSimulatedCollection();
        cancelSimulatedTasks();
        cancelReconnectTask();

        if (receiveThread != null) {
            receiveThread.interrupt();
            try {
                receiveThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for receive thread to stop", e);
                Thread.currentThread().interrupt();
            }
            receiveThread = null;
        }

        closeStreams();
        resetProtocolBuffers();
        updateProtocolState(ProtocolState.IDLE);
        updateHandshakeStage(HandshakeStage.IDLE);
        updateConnectionState(ConnectionState.DISCONNECTED);
        Log.i(TAG, "Disconnected successfully");
    }

    private void cancelReconnectTask() {
        if (reconnectScheduled && reconnectRunnable != null) {
            mainHandler.removeCallbacks(reconnectRunnable);
            reconnectScheduled = false;
            reconnectRunnable = null;
        }
    }

    private void cancelSimulatedTasks() {
        if (simulatedConnectRunnable != null) {
            mainHandler.removeCallbacks(simulatedConnectRunnable);
            simulatedConnectRunnable = null;
        }
        if (simulatedCollectionRunnable != null) {
            mainHandler.removeCallbacks(simulatedCollectionRunnable);
            simulatedCollectionRunnable = null;
        }
    }

    private void closeStreams() {
        if (bufferedSource != null) {
            try {
                bufferedSource.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing buffered source", e);
            }
            bufferedSource = null;
        }

        if (bufferedSink != null) {
            try {
                bufferedSink.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing buffered sink", e);
            }
            bufferedSink = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
            socket = null;
        }
    }

    private void updateConnectionState(ConnectionState state) {
        connectionState = state == null ? ConnectionState.DISCONNECTED : state;
        TcpDiagnosticsStore.getInstance().recordConnectionState(connectionState);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onConnectionStateChanged(connectionState);
            }
        });
    }

    private void updateProtocolState(@Nullable ProtocolState state) {
        ProtocolState safeState = state == null ? ProtocolState.IDLE : state;
        if (protocolState == safeState) {
            return;
        }
        protocolState = safeState;
        TcpDiagnosticsStore.getInstance().recordProtocolState(safeState);
        syncHandshakeStageWithProtocolState(safeState);
    }

    public void updateProtocolStateFromDevice(@Nullable ProtocolState state) {
        if (state != null) {
            updateProtocolState(state);
        }
    }

    private void updateProtocolStateForCommand(int commandType) {
        switch (commandType) {
            case CMD_SET_PARAMETERS:
                updateProtocolState(ProtocolState.CONFIGURING);
                break;
            case CMD_START_COLLECTION:
                updateProtocolState(ProtocolState.STARTING);
                break;
            case CMD_STOP_COLLECTION:
                updateProtocolState(ProtocolState.STOPPING);
                break;
            case CMD_GET_STATUS:
                if (protocolState != ProtocolState.COLLECTING) {
                    updateProtocolState(ProtocolState.REQUESTING_STATUS);
                }
                break;
            default:
                break;
        }
    }

    private void onIncomingCommand(int commandType) {
        switch (commandType) {
            case CMD_DEVICE_STATUS:
            case CMD_GET_STATUS:
                if (protocolState == ProtocolState.REQUESTING_STATUS
                        || protocolState == ProtocolState.CONFIGURING
                        || protocolState == ProtocolState.STOPPING
                        || protocolState == ProtocolState.IDLE
                        || protocolState == ProtocolState.ERROR) {
                    updateProtocolState(ProtocolState.READY);
                }
                break;
            case CMD_WAVEFORM_RECV:
            case CMD_WAVEFORM_SEND:
            case CMD_WAVEFORM_OFF:
                updateProtocolState(ProtocolState.COLLECTING);
                break;
            default:
                break;
        }
    }

    private int resolveSimulatedSystemStatus() {
        if (protocolState == ProtocolState.ERROR) {
            return 4;
        }
        if (protocolState == ProtocolState.STOPPING) {
            return 3;
        }
        if (simulatedCollectionActive || protocolState == ProtocolState.COLLECTING) {
            return 2;
        }
        if (connectionState == ConnectionState.CONNECTED) {
            return 1;
        }
        return 0;
    }

    private void notifyDataReceived(byte[] data) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDataReceived(data);
            }
        });
    }

    private void notifyError(String error) {
        notifyError(error, false);
    }

    private void notifyError(String error, boolean checksumError) {
        if (checksumError) {
            TcpDiagnosticsStore.getInstance().recordChecksumError(error);
        } else {
            TcpDiagnosticsStore.getInstance().recordError(error);
        }
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(error);
            }
        });
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public ProtocolState getProtocolState() {
        return protocolState;
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }

    private void refreshDiagnosticsTarget() {
        if (simulatedDeviceEnabled) {
            TcpDiagnosticsStore.getInstance().setConnectionTarget(SIMULATED_TARGET, 0);
        } else {
            TcpDiagnosticsStore.getInstance().setConnectionTarget(serverIp, serverPort);
        }
    }

    private String safeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private String safeTextOrDefault(@Nullable String value, String fallback) {
        String trimmed = safeText(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String commandLabel(int commandType) {
        switch (commandType) {
            case CMD_START_COLLECTION:
                return GeoDspProtocol.labelForMessageId(CMD_START_COLLECTION);
            case CMD_STOP_COLLECTION:
                return GeoDspProtocol.labelForMessageId(CMD_STOP_COLLECTION);
            case CMD_SET_PARAMETERS:
                return GeoDspProtocol.labelForMessageId(CMD_SET_PARAMETERS);
            case CMD_GET_STATUS:
                return GeoDspProtocol.labelForMessageId(CMD_GET_STATUS);
            case CMD_WAVEFORM_RECV:
                return GeoDspProtocol.labelForMessageId(CMD_WAVEFORM_RECV);
            case CMD_WAVEFORM_SEND:
                return GeoDspProtocol.labelForMessageId(CMD_WAVEFORM_SEND);
            case CMD_WAVEFORM_OFF:
                return GeoDspProtocol.labelForMessageId(CMD_WAVEFORM_OFF);
            case CMD_DEVICE_STATUS:
                return GeoDspProtocol.labelForMessageId(CMD_DEVICE_STATUS);
            default:
                return GeoDspProtocol.labelForMessageId(commandType);
        }
    }

    private boolean isWaveformMessage(int commandType) {
        return commandType == CMD_WAVEFORM_RECV
                || commandType == CMD_WAVEFORM_SEND
                || commandType == CMD_WAVEFORM_OFF;
    }

    private boolean isStatusMessage(int commandType) {
        return commandType == CMD_DEVICE_STATUS || commandType == CMD_GET_STATUS;
    }

    private void updateHandshakeStage(@Nullable HandshakeStage stage) {
        HandshakeStage safeStage = stage == null ? HandshakeStage.IDLE : stage;
        if (handshakeStage == safeStage) {
            return;
        }
        handshakeStage = safeStage;
        TcpDiagnosticsStore.getInstance().recordHandshakeStage(safeStage.getDisplayName());
    }

    private void syncHandshakeStageWithProtocolState(@Nullable ProtocolState state) {
        if (state == null) {
            return;
        }
        switch (state) {
            case REQUESTING_STATUS:
                updateHandshakeStage(HandshakeStage.WAITING_STATUS_FRAME);
                break;
            case READY:
                updateHandshakeStage(HandshakeStage.READY);
                break;
            case CONFIGURING:
                updateHandshakeStage(HandshakeStage.PARAMETERS_SENT);
                break;
            case STARTING:
                updateHandshakeStage(HandshakeStage.START_COMMAND_SENT);
                break;
            case COLLECTING:
                updateHandshakeStage(HandshakeStage.COLLECTING);
                break;
            case STOPPING:
                updateHandshakeStage(HandshakeStage.STOP_COMMAND_SENT);
                break;
            case ERROR:
                updateHandshakeStage(HandshakeStage.ERROR);
                break;
            case IDLE:
            default:
                updateHandshakeStage(HandshakeStage.IDLE);
                break;
        }
    }

    private void handleFrameAnomaly(String error,
                                    @Nullable String frameSummary,
                                    @Nullable byte[] frameBytes,
                                    boolean checksumError,
                                    boolean fatal) {
        Log.w(TAG, error);
        updateProtocolState(ProtocolState.ERROR);
        TcpDiagnosticsStore.getInstance().recordFrameAnomaly(
                error,
                frameSummary,
                TcpDiagnosticsStore.formatHexPreview(frameBytes),
                checksumError);
        dispatchError(error);
        if (fatal) {
            recoverFromFatalProtocolError();
        }
    }

    private void recoverFromFatalProtocolError() {
        closeStreams();
        if (isRunning && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            attemptReconnect();
        } else if (isRunning) {
            isRunning = false;
            updateConnectionState(ConnectionState.DISCONNECTED);
        }
    }

    private void dispatchError(String error) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(error);
            }
        });
    }

    private void resetProtocolBuffers() {
        receiveFrameBufferLength = 0;
        nextOutgoingMessageNo = 0;
        nextSimulatedMessageNo = 0;
    }

    private static final class SimulatedCollectionConfig {
        float transmitCurrent = 25f;
        int sampleFrequency = 300;
        int collectionCount = 2;
        float sampleTimeUs = 10f;
        float electrodeDistance = 0f;
    }
}
