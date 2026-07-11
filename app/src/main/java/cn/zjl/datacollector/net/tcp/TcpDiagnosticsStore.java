package cn.zjl.datacollector.net.tcp;

/**
 * 阅读提示：设备联调诊断状态仓库：集中保存连接阶段、最后命令、异常帧、握手状态和最近收发记录。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.zjl.datacollector.net.wifi.DeviceWifiState;

/**
 * 保存最近一次 TCP / 协议联调诊断信息，供诊断页和采集页共享。
 */
public final class TcpDiagnosticsStore {

    private static final int MAX_EVENTS = 20;
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 3;
    private static final int HEX_PREVIEW_BYTES = 128;

    private static final TcpDiagnosticsStore INSTANCE = new TcpDiagnosticsStore();

    public static final class Snapshot {
        public final DeviceWifiState wifiState;
        public final String wifiSsid;
        public final String wifiDetail;
        public final TcpClientManager.ConnectionState connectionState;
        public final TcpClientManager.ProtocolState protocolState;
        public final String serverTarget;
        public final int reconnectAttempts;
        public final int maxReconnectAttempts;
        public final long updatedAt;
        public final long lastOutgoingAt;
        public final long lastIncomingAt;
        public final long lastErrorAt;
        public final String lastOutgoingSummary;
        public final String lastOutgoingHex;
        public final String lastIncomingSummary;
        public final String lastIncomingHex;
        public final String lastError;
        public final String lastEvent;
        public final String lastCommand;
        public final String handshakeStage;
        public final String lastStatusSummary;
        public final long lastErrorFrameAt;
        public final String lastErrorFrameSummary;
        public final String lastErrorFrameHex;
        public final int sentCommandCount;
        public final int receivedPacketCount;
        public final int statusFrameCount;
        public final int waveformFrameCount;
        public final int checksumErrorCount;
        public final List<String> recentEvents;

        private Snapshot(DeviceWifiState wifiState,
                         String wifiSsid,
                         String wifiDetail,
                         TcpClientManager.ConnectionState connectionState,
                         TcpClientManager.ProtocolState protocolState,
                         String serverTarget,
                         int reconnectAttempts,
                         int maxReconnectAttempts,
                         long updatedAt,
                         long lastOutgoingAt,
                         long lastIncomingAt,
                         long lastErrorAt,
                         String lastOutgoingSummary,
                         String lastOutgoingHex,
                         String lastIncomingSummary,
                         String lastIncomingHex,
                         String lastError,
                         String lastEvent,
                         String lastCommand,
                         String handshakeStage,
                         String lastStatusSummary,
                         long lastErrorFrameAt,
                         String lastErrorFrameSummary,
                         String lastErrorFrameHex,
                         int sentCommandCount,
                         int receivedPacketCount,
                         int statusFrameCount,
                         int waveformFrameCount,
                         int checksumErrorCount,
                         List<String> recentEvents) {
            this.wifiState = wifiState;
            this.wifiSsid = wifiSsid;
            this.wifiDetail = wifiDetail;
            this.connectionState = connectionState;
            this.protocolState = protocolState;
            this.serverTarget = serverTarget;
            this.reconnectAttempts = reconnectAttempts;
            this.maxReconnectAttempts = maxReconnectAttempts;
            this.updatedAt = updatedAt;
            this.lastOutgoingAt = lastOutgoingAt;
            this.lastIncomingAt = lastIncomingAt;
            this.lastErrorAt = lastErrorAt;
            this.lastOutgoingSummary = lastOutgoingSummary;
            this.lastOutgoingHex = lastOutgoingHex;
            this.lastIncomingSummary = lastIncomingSummary;
            this.lastIncomingHex = lastIncomingHex;
            this.lastError = lastError;
            this.lastEvent = lastEvent;
            this.lastCommand = lastCommand;
            this.handshakeStage = handshakeStage;
            this.lastStatusSummary = lastStatusSummary;
            this.lastErrorFrameAt = lastErrorFrameAt;
            this.lastErrorFrameSummary = lastErrorFrameSummary;
            this.lastErrorFrameHex = lastErrorFrameHex;
            this.sentCommandCount = sentCommandCount;
            this.receivedPacketCount = receivedPacketCount;
            this.statusFrameCount = statusFrameCount;
            this.waveformFrameCount = waveformFrameCount;
            this.checksumErrorCount = checksumErrorCount;
            this.recentEvents = recentEvents;
        }

        private static Snapshot empty() {
            return new Snapshot(
                    DeviceWifiState.IDLE,
                    "",
                    "",
                    TcpClientManager.ConnectionState.DISCONNECTED,
                    TcpClientManager.ProtocolState.IDLE,
                    "--",
                    0,
                    DEFAULT_MAX_RECONNECT_ATTEMPTS,
                    0L,
                    0L,
                    0L,
                    0L,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    0,
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    Collections.emptyList());
        }
    }

    private final Object lock = new Object();
    private final MutableLiveData<Snapshot> liveData = new MutableLiveData<>(Snapshot.empty());
    private final ArrayList<String> eventBuffer = new ArrayList<>();

    private DeviceWifiState wifiState = DeviceWifiState.IDLE;
    private String wifiSsid = "";
    private String wifiDetail = "";
    private TcpClientManager.ConnectionState connectionState = TcpClientManager.ConnectionState.DISCONNECTED;
    private TcpClientManager.ProtocolState protocolState = TcpClientManager.ProtocolState.IDLE;
    private String serverIp = "";
    private int serverPort = 0;
    private int reconnectAttempts;
    private int maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS;
    private long updatedAt;
    private long lastOutgoingAt;
    private long lastIncomingAt;
    private long lastErrorAt;
    private String lastOutgoingSummary = "";
    private String lastOutgoingHex = "";
    private String lastIncomingSummary = "";
    private String lastIncomingHex = "";
    private String lastError = "";
    private String lastEvent = "";
    private String lastCommand = "";
    private String handshakeStage = "";
    private String lastStatusSummary = "";
    private long lastErrorFrameAt;
    private String lastErrorFrameSummary = "";
    private String lastErrorFrameHex = "";
    private int sentCommandCount;
    private int receivedPacketCount;
    private int statusFrameCount;
    private int waveformFrameCount;
    private int checksumErrorCount;
    private Snapshot snapshot = Snapshot.empty();

    private TcpDiagnosticsStore() {
    }

    public static TcpDiagnosticsStore getInstance() {
        return INSTANCE;
    }

    public LiveData<Snapshot> getLiveData() {
        return liveData;
    }

    public Snapshot getSnapshot() {
        synchronized (lock) {
            return snapshot;
        }
    }

    public void setConnectionTarget(@Nullable String ip, int port) {
        synchronized (lock) {
            serverIp = safeText(ip);
            serverPort = port;
            appendEventUnlocked("目标设备 " + getServerTargetLocked());
            updateSnapshotLocked();
        }
    }

    public void recordWifiState(@Nullable DeviceWifiState state,
                                @Nullable String ssid,
                                @Nullable String detail) {
        synchronized (lock) {
            DeviceWifiState safeState = state == null ? DeviceWifiState.IDLE : state;
            String safeSsid = safeText(ssid);
            String safeDetail = safeText(detail);
            if (wifiState == safeState
                    && wifiSsid.equals(safeSsid)
                    && wifiDetail.equals(safeDetail)) {
                return;
            }
            wifiState = safeState;
            wifiSsid = safeSsid;
            wifiDetail = safeDetail;

            StringBuilder builder = new StringBuilder("Wi-Fi -> ")
                    .append(labelForWifiState(safeState));
            if (!wifiSsid.isEmpty()) {
                builder.append(" (").append(wifiSsid).append(')');
            }
            if (!wifiDetail.isEmpty()) {
                builder.append(" / ").append(wifiDetail);
            }
            appendEventUnlocked(builder.toString());
            updateSnapshotLocked();
        }
    }

    public void recordConnectionState(@Nullable TcpClientManager.ConnectionState state) {
        synchronized (lock) {
            connectionState = state == null
                    ? TcpClientManager.ConnectionState.DISCONNECTED
                    : state;
            appendEventUnlocked("连接状态 -> " + labelForState(connectionState));
            updateSnapshotLocked();
        }
    }

    public void recordProtocolState(@Nullable TcpClientManager.ProtocolState state) {
        synchronized (lock) {
            TcpClientManager.ProtocolState safeState = state == null
                    ? TcpClientManager.ProtocolState.IDLE
                    : state;
            if (protocolState == safeState) {
                return;
            }
            protocolState = safeState;
            appendEventUnlocked("协议阶段 -> " + protocolState.getDisplayName());
            updateSnapshotLocked();
        }
    }

    public void recordReconnectAttempt(int attempt, int maxAttempts) {
        synchronized (lock) {
            reconnectAttempts = Math.max(0, attempt);
            maxReconnectAttempts = Math.max(1, maxAttempts);
            appendEventUnlocked("重连尝试 " + reconnectAttempts + "/" + maxReconnectAttempts);
            updateSnapshotLocked();
        }
    }

    public void recordOutgoingCommand(String summary,
                                      String hexPreview,
                                      @Nullable String commandLabel) {
        synchronized (lock) {
            lastOutgoingSummary = safeText(summary);
            lastOutgoingHex = safeText(hexPreview);
            lastOutgoingAt = System.currentTimeMillis();
            sentCommandCount++;
            if (!safeText(commandLabel).isEmpty()) {
                lastCommand = safeText(commandLabel);
            }
            appendEventUnlocked("发送 " + lastOutgoingSummary);
            updateSnapshotLocked();
        }
    }

    public void recordIncomingPacket(String summary,
                                     String hexPreview,
                                     @Nullable String commandLabel,
                                     boolean statusFrame,
                                     boolean waveformFrame) {
        synchronized (lock) {
            lastIncomingSummary = safeText(summary);
            lastIncomingHex = safeText(hexPreview);
            lastIncomingAt = System.currentTimeMillis();
            receivedPacketCount++;
            if (statusFrame) {
                statusFrameCount++;
            }
            if (waveformFrame) {
                waveformFrameCount++;
            }
            if (!safeText(commandLabel).isEmpty()) {
                lastCommand = safeText(commandLabel);
            }
            appendEventUnlocked("接收 " + lastIncomingSummary);
            updateSnapshotLocked();
        }
    }

    public void recordStatusSummary(@Nullable String summary) {
        synchronized (lock) {
            lastStatusSummary = safeText(summary);
            updateSnapshotLocked();
        }
    }

    public void recordHandshakeStage(@Nullable String stage) {
        synchronized (lock) {
            String safeStage = safeText(stage);
            if (handshakeStage.equals(safeStage)) {
                return;
            }
            handshakeStage = safeStage;
            appendEventUnlocked("握手阶段 -> "
                    + (safeStage.isEmpty() ? "暂无" : safeStage));
            updateSnapshotLocked();
        }
    }

    public void recordFrameAnomaly(String error,
                                   @Nullable String frameSummary,
                                   @Nullable String frameHexPreview,
                                   boolean checksumError) {
        synchronized (lock) {
            String safeError = safeText(error);
            String safeSummary = safeText(frameSummary);
            String safeHexPreview = safeText(frameHexPreview);

            lastError = safeError;
            lastErrorAt = System.currentTimeMillis();
            lastErrorFrameAt = lastErrorAt;
            lastErrorFrameSummary = safeSummary;
            lastErrorFrameHex = safeHexPreview;

            if (checksumError) {
                checksumErrorCount++;
            }

            StringBuilder builder = new StringBuilder("报文异常 ");
            if (!safeSummary.isEmpty()) {
                builder.append(safeSummary);
            } else {
                builder.append(safeError);
            }
            appendEventUnlocked(builder.toString());
            updateSnapshotLocked();
        }
    }

    public void recordChecksumError(String error) {
        synchronized (lock) {
            checksumErrorCount++;
            lastError = safeText(error);
            lastErrorAt = System.currentTimeMillis();
            appendEventUnlocked("校验错误 " + lastError);
            updateSnapshotLocked();
        }
    }

    public void recordEvent(String event) {
        synchronized (lock) {
            appendEventUnlocked(safeText(event));
            updateSnapshotLocked();
        }
    }

    public void recordError(String error) {
        synchronized (lock) {
            lastError = safeText(error);
            lastErrorAt = System.currentTimeMillis();
            appendEventUnlocked("错误 " + lastError);
            updateSnapshotLocked();
        }
    }

    public void clearHistory() {
        synchronized (lock) {
            eventBuffer.clear();
            reconnectAttempts = 0;
            updatedAt = System.currentTimeMillis();
            lastOutgoingAt = 0L;
            lastIncomingAt = 0L;
            lastErrorAt = 0L;
            lastOutgoingSummary = "";
            lastOutgoingHex = "";
            lastIncomingSummary = "";
            lastIncomingHex = "";
            lastError = "";
            lastEvent = "";
            lastCommand = "";
            lastStatusSummary = "";
            lastErrorFrameAt = 0L;
            lastErrorFrameSummary = "";
            lastErrorFrameHex = "";
            sentCommandCount = 0;
            receivedPacketCount = 0;
            statusFrameCount = 0;
            waveformFrameCount = 0;
            checksumErrorCount = 0;
            appendEventUnlocked("诊断记录已清空");
            updateSnapshotLocked();
        }
    }

    public static String formatHexPreview(@Nullable byte[] data) {
        return formatHexPreview(data, HEX_PREVIEW_BYTES);
    }

    public static String formatHexPreview(@Nullable byte[] data, int maxBytes) {
        if (data == null || data.length == 0) {
            return "";
        }
        int previewLength = Math.min(data.length, Math.max(1, maxBytes));
        StringBuilder builder = new StringBuilder(previewLength * 4);
        for (int i = 0; i < previewLength; i++) {
            if (i % 16 == 0) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(String.format(Locale.US, "%04X: ", i));
            }
            builder.append(String.format(Locale.US, "%02X ", data[i] & 0xFF));
        }
        if (data.length > previewLength) {
            if (previewLength % 16 != 0) {
                builder.append('\n');
            }
            builder.append("... (+").append(data.length - previewLength).append(" bytes)");
        }
        return builder.toString().trim();
    }

    private void updateSnapshotLocked() {
        updatedAt = System.currentTimeMillis();
        snapshot = new Snapshot(
                wifiState,
                wifiSsid,
                wifiDetail,
                connectionState,
                protocolState,
                getServerTargetLocked(),
                reconnectAttempts,
                maxReconnectAttempts,
                updatedAt,
                lastOutgoingAt,
                lastIncomingAt,
                lastErrorAt,
                lastOutgoingSummary,
                lastOutgoingHex,
                lastIncomingSummary,
                lastIncomingHex,
                lastError,
                lastEvent,
                lastCommand,
                handshakeStage,
                lastStatusSummary,
                lastErrorFrameAt,
                lastErrorFrameSummary,
                lastErrorFrameHex,
                sentCommandCount,
                receivedPacketCount,
                statusFrameCount,
                waveformFrameCount,
                checksumErrorCount,
                Collections.unmodifiableList(new ArrayList<>(eventBuffer)));
        liveData.postValue(snapshot);
    }

    private void appendEventUnlocked(String event) {
        String normalized = safeText(event);
        if (normalized.isEmpty()) {
            return;
        }
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date(System.currentTimeMillis()));
        lastEvent = normalized;
        eventBuffer.add(timestamp + "  " + normalized);
        while (eventBuffer.size() > MAX_EVENTS) {
            eventBuffer.remove(0);
        }
    }

    private String getServerTargetLocked() {
        if (serverIp.isEmpty()) {
            return "--";
        }
        if (serverPort <= 0) {
            return serverIp;
        }
        return serverIp + ":" + serverPort;
    }

    private String labelForState(@Nullable TcpClientManager.ConnectionState state) {
        if (state == null) {
            return "未连接";
        }
        switch (state) {
            case CONNECTING:
                return "正在连接";
            case CONNECTED:
                return "已连接";
            case RECONNECTING:
                return "重连中";
            case DISCONNECTED:
            default:
                return "未连接";
        }
    }

    private String labelForWifiState(@Nullable DeviceWifiState state) {
        if (state == null) {
            return "未启用";
        }
        switch (state) {
            case CONNECTING:
                return "正在连接";
            case CONNECTED:
                return "已连接";
            case DISCONNECTED:
                return "已断开";
            case PERMISSION_REQUIRED:
                return "缺少权限";
            case UNAVAILABLE:
                return "未找到热点";
            case LOST:
                return "热点已断开";
            case ERROR:
                return "连接失败";
            case UNSUPPORTED:
                return "系统不支持";
            case IDLE:
            default:
                return "未启用";
        }
    }

    private String safeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
