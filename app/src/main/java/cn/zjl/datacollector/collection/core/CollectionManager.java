package cn.zjl.datacollector.collection.core;

/**
 * 阅读提示：采集流程核心管理器：负责下发采集命令、解析设备返回报文，并把波形和监控信息回调给界面层。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.net.tcp.GeoDspProtocol;
import cn.zjl.datacollector.net.tcp.TcpClientManager;
import cn.zjl.datacollector.net.tcp.TcpDiagnosticsStore;

/**
 * 负责设备采集流程、协议数据解析以及向 UI 分发实时结果。
 *
 * <p>阅读本类时可以按这条主线走：
 * 设置参数 {@link #setParameters(CollectionParameterEntity)} ->
 * 开始采集 {@link #startCollection()} ->
 * TCP 收到完整 0x8000 帧后调用 {@link #processReceivedData(byte[])} ->
 * 解析波形/监控信息 ->
 * 通过 {@link DataCallback} 回到 Activity/ViewModel。</p>
 */
public class CollectionManager {

    private static final String TAG = "CollectionManager";

    private static final int CMD_START_COLLECTION = GeoDspProtocol.MSG_TEM_START;
    private static final int CMD_STOP_COLLECTION = GeoDspProtocol.MSG_TEM_STOP;
    private static final int CMD_SET_PARAMETERS = GeoDspProtocol.MSG_TEM_CONFIG;
    private static final int CMD_GET_STATUS = GeoDspProtocol.MSG_DEVICE_INFO;
    private static final int CMD_WAVEFORM_RECV = GeoDspProtocol.MSG_TEM_SEND_DATA;
    private static final int CMD_WAVEFORM_SEND = GeoDspProtocol.MSG_TEM_GET_SEND_DATA;
    private static final int CMD_WAVEFORM_OFF = GeoDspProtocol.MSG_TEM_GET_OFF_DATA;
    private static final int CMD_DEVICE_STATUS = GeoDspProtocol.MSG_TEM_SEND_MONITOR;

    private static final int WAVEFORM_TYPE_RECV = 0;
    private static final int WAVEFORM_TYPE_SEND = 1;
    private static final int WAVEFORM_TYPE_OFF = 2;

    private static final int STATUS_PAYLOAD_MAGIC = 0x44534D31;
    /** 旧版监控 payload 只有电池、电流、温度、信号强度 4 个 float。 */
    private static final int LEGACY_STATUS_LENGTH = 16;
    /** 扩展监控 payload 增加 GPS、协议状态、帧计数、设备时间戳等现场联调字段。 */
    private static final int EXTENDED_STATUS_LENGTH = 72;
    /** 预分配波形缓存，避免实时采集过程中频繁创建大数组。 */
    private static final int DEFAULT_BUFFER_SIZE = 10_000;

    public enum CollectionStatus {
        /** 未采集或采集流程已复位。 */
        IDLE,
        /** 正在向设备发送参数配置。 */
        CONFIGURING,
        /** 已进入采集状态，等待或正在接收波形。 */
        COLLECTING,
        /** 预留状态：后续可用于暂停采集。 */
        PAUSED,
        /** 本次采集已结束。 */
        COMPLETED
    }

    /**
     * 采集层向界面层暴露的回调。
     *
     * <p>所有回调都会切回主线程执行，Activity 可以安全地更新控件和图表。</p>
     */
    public interface DataCallback {
        void onWaveformData(float[] timePoints, float[] recvValues, float[] sendValues, float[] offValues);

        void onMonitorInfo(DeviceMonitorEntity monitor);

        void onCollectionComplete();

        void onError(String error);
    }

    private final TcpClientManager tcpClient;
    private final Handler mainHandler;
    private final List<DataCallback> dataCallbacks = new ArrayList<>();

    private volatile CollectionStatus status = CollectionStatus.IDLE;
    private CollectionParameterEntity currentParameters;
    private DeviceMonitorEntity currentMonitorInfo;

    private final float[] recvWaveform = new float[DEFAULT_BUFFER_SIZE];
    private final float[] sendWaveform = new float[DEFAULT_BUFFER_SIZE];
    private final float[] offWaveform = new float[DEFAULT_BUFFER_SIZE];
    private final double[] timePoints = new double[DEFAULT_BUFFER_SIZE];

    public CollectionManager(TcpClientManager tcpClient) {
        this.tcpClient = tcpClient;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void addDataCallback(DataCallback callback) {
        if (callback != null && !dataCallbacks.contains(callback)) {
            dataCallbacks.add(callback);
        }
    }

    public void removeDataCallback(DataCallback callback) {
        dataCallbacks.remove(callback);
    }

    public void setParameters(CollectionParameterEntity parameters) {
        if (status == CollectionStatus.COLLECTING) {
            notifyError("正在采集中，无法修改参数");
            return;
        }
        // 参数会先保存到内存中，开始采集前如果未配置参数会被拦截。
        currentParameters = parameters;
        sendParametersToDevice(parameters);
    }

    public void startCollection() {
        if (!tcpClient.isConnected()) {
            notifyError("TCP 未连接，无法开始采集");
            return;
        }
        if (currentParameters == null) {
            notifyError("请先设置采集参数");
            return;
        }

        status = CollectionStatus.COLLECTING;
        // 当前外层协议已经切换为 0x8000；payload 暂为空，真实设备如需附带业务结构需在这里扩展。
        tcpClient.sendCommand(CMD_START_COLLECTION, null);
        Log.i(TAG, "Collection started");
    }

    public void stopCollection() {
        if (status != CollectionStatus.COLLECTING) {
            return;
        }
        tcpClient.sendCommand(CMD_STOP_COLLECTION, null);
        status = CollectionStatus.COMPLETED;
        notifyCollectionComplete();
        Log.i(TAG, "Collection stopped");
    }

    public void processReceivedData(byte[] data) {
        if (data == null || data.length < GeoDspProtocol.HEADER_SIZE) {
            return;
        }

        try {
            // TcpClientManager 已经负责流式拆包，这里通常拿到的是一帧完整的 0x8000 报文。
            GeoDspProtocol.Packet packet = GeoDspProtocol.decode(data);
            if (packet == null) {
                Log.w(TAG, "Invalid 0x8000 packet");
                return;
            }

            ByteBuffer buffer = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN);
            int dataLength = packet.payload.length;

            switch (packet.id) {
                case CMD_WAVEFORM_RECV:
                case CMD_WAVEFORM_SEND:
                case CMD_WAVEFORM_OFF:
                    // 目前项目内部仍使用简化波形 payload：类型 + 点数 + (时间, 数值) 序列。
                    parseWaveformData(buffer, dataLength);
                    break;
                case CMD_DEVICE_STATUS:
                case CMD_GET_STATUS:
                    // 监控数据兼容旧版 16 字节 payload 与新版扩展 payload。
                    parseDeviceStatus(buffer, dataLength);
                    break;
                default:
                    Log.d(TAG, "Unknown 0x8000 message id: " + packet.id);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing received data", e);
        }
    }

    private void sendParametersToDevice(CollectionParameterEntity params) {
        if (!tcpClient.isConnected()) {
            notifyError("TCP 未连接");
            return;
        }
        if (params == null) {
            notifyError("采集参数为空");
            return;
        }

        status = CollectionStatus.CONFIGURING;
        // 首版参数 payload 是本项目内部格式，字段顺序为：电流、频率、次数、采样时间、极距。
        // 如果后续拿到真实设备 ST_TEMSendInfo/ST_RecvParam 结构，需要优先在这里对齐。
        ByteBuffer buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(params.getTransmitCurrent());
        buffer.putInt(params.getSampleFrequency());
        buffer.putInt(params.getCollectionCount());
        buffer.putFloat(params.getSampleTime());
        buffer.putFloat(params.getElectrodeDistance());
        tcpClient.sendCommand(CMD_SET_PARAMETERS, buffer.array());
        Log.i(TAG, "Parameters sent to device");
    }

    private void parseWaveformData(ByteBuffer buffer, int length) {
        try {
            if (length < 8) {
                return;
            }

            // payload 前 8 字节说明波形类型和采样点数量，后续每个点占 12 字节：double 时间 + float 数值。
            int waveformType = buffer.getInt();
            int sampleCount = buffer.getInt();
            if (sampleCount < 0) {
                return;
            }

            float[] values = new float[sampleCount];
            double[] times = new double[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                if (buffer.remaining() < 12) {
                    // 遇到半包或异常长度时保留已经读出的部分，避免直接崩溃。
                    break;
                }
                times[i] = buffer.getDouble();
                values[i] = buffer.getFloat();
            }

            switch (waveformType) {
                case WAVEFORM_TYPE_RECV:
                    // RECV 波形同时更新横轴时间；SEND/OFF 默认复用同一时间轴显示。
                    System.arraycopy(values, 0, recvWaveform, 0, Math.min(values.length, recvWaveform.length));
                    System.arraycopy(times, 0, timePoints, 0, Math.min(times.length, timePoints.length));
                    break;
                case WAVEFORM_TYPE_SEND:
                    System.arraycopy(values, 0, sendWaveform, 0, Math.min(values.length, sendWaveform.length));
                    break;
                case WAVEFORM_TYPE_OFF:
                    System.arraycopy(values, 0, offWaveform, 0, Math.min(values.length, offWaveform.length));
                    break;
                default:
                    return;
            }

            status = CollectionStatus.COLLECTING;
            notifyWaveformData(timePoints, recvWaveform, sendWaveform, offWaveform);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing waveform data", e);
        }
    }

    private void parseDeviceStatus(ByteBuffer buffer, int length) {
        try {
            DeviceMonitorEntity monitor = new DeviceMonitorEntity();
            TcpDiagnosticsStore.Snapshot snapshot = TcpDiagnosticsStore.getInstance().getSnapshot();
            long now = System.currentTimeMillis();

            // 扩展 payload 通过 magic 区分，避免把旧设备的 16 字节状态误读成新版结构。
            if (length >= EXTENDED_STATUS_LENGTH && isExtendedStatusPayload(buffer)) {
                parseExtendedStatus(buffer, monitor);
            } else if (length >= LEGACY_STATUS_LENGTH) {
                parseLegacyStatus(buffer, monitor);
            } else {
                return;
            }

            // 部分字段设备可能不返回，这里用诊断仓库和当前时间补齐，保证 UI/数据库可展示。
            fillDerivedMonitorFields(monitor, snapshot, now);
            currentMonitorInfo = monitor;
            TcpDiagnosticsStore.getInstance().recordStatusSummary(buildStatusSummary(monitor));
            notifyMonitorInfo(monitor);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing device status", e);
        }
    }

    private boolean isExtendedStatusPayload(ByteBuffer buffer) {
        int start = buffer.position();
        if (buffer.remaining() < 8) {
            return false;
        }
        int magic = buffer.getInt();
        buffer.position(start);
        return magic == STATUS_PAYLOAD_MAGIC;
    }

    private void parseLegacyStatus(ByteBuffer buffer, DeviceMonitorEntity monitor) {
        monitor.setBatteryVoltage(buffer.getFloat());
        monitor.setCurrent(buffer.getFloat());
        monitor.setTemperature(buffer.getFloat());
        monitor.setSignalStrength(buffer.getFloat());
    }

    private void parseExtendedStatus(ByteBuffer buffer, DeviceMonitorEntity monitor) {
        int magic = buffer.getInt();
        int version = buffer.getInt();
        if (magic != STATUS_PAYLOAD_MAGIC || version <= 0 || buffer.remaining() < EXTENDED_STATUS_LENGTH - 8) {
            throw new IllegalStateException("Invalid extended status payload");
        }

        monitor.setBatteryVoltage(buffer.getFloat());
        monitor.setCurrent(buffer.getFloat());
        monitor.setTemperature(buffer.getFloat());
        monitor.setSignalStrength(buffer.getFloat());
        monitor.setGpsAccuracy(buffer.getFloat());
        monitor.setSendCurrent(buffer.getFloat());
        monitor.setOffTime(buffer.getFloat());
        monitor.setDataRate(buffer.getFloat());
        monitor.setPacketLoss(buffer.getFloat());
        monitor.setBatteryLevel(buffer.getFloat());
        monitor.setProtocolStateCode(buffer.getInt());
        monitor.setSystemStatus(buffer.getInt());
        monitor.setStatusFrameCount(buffer.getInt());
        monitor.setWaveformFrameCount(buffer.getInt());
        monitor.setDeviceTimestamp(buffer.getLong());

        // 设备上报的协议状态会同步到 TCP 管理器，诊断页的“握手阶段”依赖这个状态。
        tcpClient.updateProtocolStateFromDevice(
                TcpClientManager.ProtocolState.fromCode(monitor.getProtocolStateCode()));
    }

    private void fillDerivedMonitorFields(DeviceMonitorEntity monitor,
                                          TcpDiagnosticsStore.Snapshot snapshot,
                                          long now) {
        if (!Float.isFinite(monitor.getBatteryVoltage())) {
            monitor.setBatteryVoltage(0f);
        }
        if (!Float.isFinite(monitor.getCurrent())) {
            monitor.setCurrent(0f);
        }
        if (!Float.isFinite(monitor.getTemperature())) {
            monitor.setTemperature(0f);
        }
        if (!Float.isFinite(monitor.getSignalStrength())) {
            monitor.setSignalStrength(0f);
        }
        if (!Float.isFinite(monitor.getGpsAccuracy())) {
            monitor.setGpsAccuracy(0f);
        }
        if (!Float.isFinite(monitor.getSendCurrent()) || monitor.getSendCurrent() <= 0f) {
            monitor.setSendCurrent(monitor.getCurrent());
        }
        if (!Float.isFinite(monitor.getOffTime())) {
            monitor.setOffTime(0f);
        }
        if (!Float.isFinite(monitor.getDataRate())) {
            monitor.setDataRate(0f);
        }
        if (!Float.isFinite(monitor.getPacketLoss())) {
            monitor.setPacketLoss(0f);
        }
        if (!Float.isFinite(monitor.getBatteryLevel()) || monitor.getBatteryLevel() <= 0f) {
            monitor.setBatteryLevel(estimateBatteryLevel(monitor.getBatteryVoltage()));
        }

        if (monitor.getProtocolStateCode() <= 0) {
            monitor.setProtocolStateCode(tcpClient.getProtocolState().getCode());
        }
        if (monitor.getSystemStatus() <= 0) {
            monitor.setSystemStatus(deriveSystemStatus(monitor.getProtocolStateCode()));
        }
        if (monitor.getStatusFrameCount() <= 0) {
            monitor.setStatusFrameCount(snapshot.statusFrameCount);
        }
        if (monitor.getWaveformFrameCount() <= 0) {
            monitor.setWaveformFrameCount(snapshot.waveformFrameCount);
        }
        if (monitor.getDeviceTimestamp() <= 0L) {
            monitor.setDeviceTimestamp(now);
        }
        monitor.setTimestamp(now);
    }

    private int deriveSystemStatus(int protocolStateCode) {
        TcpClientManager.ProtocolState protocolState =
                TcpClientManager.ProtocolState.fromCode(protocolStateCode);
        switch (protocolState) {
            case COLLECTING:
                return 2;
            case STOPPING:
                return 3;
            case ERROR:
                return 4;
            case READY:
            case REQUESTING_STATUS:
            case CONFIGURING:
            case STARTING:
                return 1;
            case IDLE:
            default:
                return 0;
        }
    }

    private float estimateBatteryLevel(float batteryVoltage) {
        if (!Float.isFinite(batteryVoltage) || batteryVoltage <= 0f) {
            return 0f;
        }
        // 简单线性估算：10.8V 近似 0%，12.6V 近似满电。真实电池曲线后续可按设备型号修正。
        float normalized = (batteryVoltage - 10.8f) / 1.8f * 100f;
        return Math.max(0f, Math.min(100f, normalized));
    }

    private String buildStatusSummary(DeviceMonitorEntity monitor) {
        String protocol = TcpClientManager.ProtocolState.fromCode(monitor.getProtocolStateCode()).getDisplayName();
        return String.format(
                Locale.getDefault(),
                "协议 %s | 电池 %.2fV | 温度 %.1f℃ | 信号 %.0f | GPS %.1fm",
                protocol,
                monitor.getBatteryVoltage(),
                monitor.getTemperature(),
                monitor.getSignalStrength(),
                monitor.getGpsAccuracy());
    }

    private void notifyWaveformData(double[] timePoints, float[] recv, float[] send, float[] off) {
        float[] axis = convertDoubleToFloat(timePoints);
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onWaveformData(axis, recv, send, off);
            }
        });
    }

    private float[] convertDoubleToFloat(double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            floats[i] = (float) doubles[i];
        }
        return floats;
    }

    private void notifyMonitorInfo(DeviceMonitorEntity monitor) {
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onMonitorInfo(monitor);
            }
        });
    }

    private void notifyCollectionComplete() {
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onCollectionComplete();
            }
        });
    }

    private void notifyError(String error) {
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onError(error);
            }
        });
    }

    public CollectionStatus getStatus() {
        return status;
    }

    public CollectionParameterEntity getCurrentParameters() {
        return currentParameters;
    }

    public DeviceMonitorEntity getCurrentMonitorInfo() {
        return currentMonitorInfo;
    }

    public float[] getRecvWaveform() {
        return recvWaveform.clone();
    }

    public float[] getSendWaveform() {
        return sendWaveform.clone();
    }

    public float[] getOffWaveform() {
        return offWaveform.clone();
    }

    public double[] getTimePoints() {
        return timePoints.clone();
    }
}
