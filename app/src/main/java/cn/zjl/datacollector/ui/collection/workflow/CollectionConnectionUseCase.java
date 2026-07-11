package cn.zjl.datacollector.ui.collection.workflow;

/**
 * 阅读提示：采集业务流程模块代码：负责连接、质检、保存和参数沿用等采集前后流程。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.net.Network;

import androidx.annotation.Nullable;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.collection.core.CollectionManager;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.net.tcp.TcpClientManager;
import cn.zjl.datacollector.net.tcp.TcpDiagnosticsStore;
import cn.zjl.datacollector.net.wifi.DeviceWifiConfig;
import cn.zjl.datacollector.net.wifi.DeviceWifiConnector;
import cn.zjl.datacollector.net.wifi.DeviceWifiState;
import cn.zjl.datacollector.ui.collection.chart.CollectionChartStateFactory;
import cn.zjl.datacollector.ui.collection.screen.CollectionViewModel;
import cn.zjl.datacollector.ui.log.OperationLogStore;
import cn.zjl.datacollector.util.AppSettings;

/**
 * 负责连接管理、采集流程启动/停止，以及 ActionState 的统一计算。
 */
public class CollectionConnectionUseCase {

    private final Context context;
    private final CollectionChartStateFactory chartStateFactory;
    private final OperationLogStore operationLogStore;

    private TcpClientManager tcpClient;
    private DeviceWifiConnector wifiConnector;
    private CollectionManager collectionManager;
    private Callback callback;
    private boolean readOnlyProject;
    private boolean isCollecting;
    private boolean hasNewData;
    private int currentSampleFrequency = 300;
    private float currentSendFrequency = 25f;
    private float currentAuxiliarySampleFrequency;
    private DeviceMonitorEntity currentMonitor;
    private String databaseName = "";
    private String lastCollectionLineLabel = "";
    private boolean connectionStateInitialized;
    private boolean simulationEnabled;
    private DeviceWifiState wifiState = DeviceWifiState.IDLE;
    private TcpClientManager.ConnectionState lastLoggedConnectionState =
            TcpClientManager.ConnectionState.DISCONNECTED;

    private final CollectionManager.DataCallback collectionCallback = new CollectionManager.DataCallback() {
        @Override
        public void onWaveformData(float[] timePoints, float[] recvValues, float[] sendValues, float[] offValues) {
            CollectionChartStateFactory.LiveWaveformResult result = chartStateFactory.processIncomingWaveform(
                    timePoints,
                    recvValues,
                    sendValues,
                    offValues,
                    currentSampleFrequency,
                    currentSendFrequency,
                    currentAuxiliarySampleFrequency);
            hasNewData = result.hasNewData;
            if (callback != null) {
                callback.onWaveformResult(result);
            }
            dispatchActionState();
        }

        @Override
        public void onMonitorInfo(DeviceMonitorEntity monitor) {
            currentMonitor = monitor;
            if (callback != null) {
                callback.onMonitorChanged(monitor);
            }
        }

        @Override
        public void onCollectionComplete() {
            isCollecting = false;
            operationLogStore.record(
                    OperationLogStore.CATEGORY_COLLECTION,
                    context.getString(R.string.operation_log_title_collection_completed_pending_save),
                    lastCollectionLineLabel.isEmpty()
                            ? ""
                            : context.getString(R.string.operation_log_detail_line_only, lastCollectionLineLabel),
                    databaseName);
            dispatchActionState();
        }

        @Override
        public void onError(String error) {
            isCollecting = false;
            operationLogStore.record(
                    OperationLogStore.CATEGORY_COLLECTION,
                    context.getString(R.string.operation_log_title_collection_failed),
                    safeText(error),
                    databaseName);
            dispatchActionState();
            if (callback != null) {
                callback.onMessage(error);
            }
        }
    };

    private final TcpClientManager.ConnectionListener connectionListener = new TcpClientManager.ConnectionListener() {
        @Override
        public void onConnectionStateChanged(TcpClientManager.ConnectionState state) {
            logConnectionState(state);
            dispatchActionState(state);
            if (callback != null) {
                callback.onConnectionStateChanged(state);
            }
        }

        @Override
        public void onDataReceived(byte[] data) {
            if (collectionManager != null) {
                collectionManager.processReceivedData(data);
            }
        }

        @Override
        public void onError(String error) {
            isCollecting = false;
            operationLogStore.record(
                    OperationLogStore.CATEGORY_DEVICE,
                    context.getString(R.string.operation_log_title_device_connection_io_error),
                    safeText(error),
                    databaseName);
            dispatchActionState();
            if (callback != null) {
                callback.onMessage(error);
            }
        }
    };

    public CollectionConnectionUseCase(Context context, CollectionChartStateFactory chartStateFactory) {
        this.context = context.getApplicationContext();
        this.chartStateFactory = chartStateFactory;
        this.operationLogStore = new OperationLogStore(this.context);
    }

    public void initialize(Callback callback, boolean readOnlyProject, @Nullable String databaseName) {
        release();
        this.callback = callback;
        this.readOnlyProject = readOnlyProject;
        this.databaseName = safeText(databaseName);
        this.isCollecting = false;
        this.hasNewData = false;
        this.currentSampleFrequency = 300;
        this.currentAuxiliarySampleFrequency = 0f;
        this.currentMonitor = null;
        this.lastCollectionLineLabel = "";
        this.connectionStateInitialized = false;
        this.simulationEnabled = AppSettings.isTcpSimulationEnabled(context);
        this.wifiState = DeviceWifiState.IDLE;
        this.lastLoggedConnectionState = TcpClientManager.ConnectionState.DISCONNECTED;
        dispatchActionState(TcpClientManager.ConnectionState.DISCONNECTED);
        if (callback != null) {
            callback.onConnectionStateChanged(TcpClientManager.ConnectionState.DISCONNECTED);
            callback.onWifiStateChanged(wifiState);
        }
    }

    public void requestConnect(String ip, int port, boolean simulationEnabled) {
        if (readOnlyProject) {
            if (callback != null) {
                callback.onMessage(context.getString(R.string.toast_project_read_only_blocked));
            }
            return;
        }
        ensureManagersReady();
        this.simulationEnabled = simulationEnabled;
        AppSettings.saveTcp(context, ip, port);
        AppSettings.setTcpSimulationEnabled(context, simulationEnabled);
        DeviceWifiConfig wifiConfig = AppSettings.getDeviceWifiConfig(context);
        tcpClient.setSimulatedDeviceEnabled(simulationEnabled);
        tcpClient.setConnectionParams(ip, port);
        tcpClient.setSocketFactory(null);
        if (wifiConnector != null) {
            wifiConnector.disconnect();
        }

        if (simulationEnabled || !wifiConfig.canUseAutoConnect()) {
            updateWifiState(DeviceWifiState.IDLE, context.getString(R.string.wifi_status_idle));
            connectTcp(ip, port, null);
            return;
        }

        operationLogStore.record(
                OperationLogStore.CATEGORY_DEVICE,
                context.getString(R.string.operation_log_title_connect_wifi_hotspot),
                buildWifiTargetDetail(wifiConfig, null),
                databaseName);
        wifiConnector.connect(new DeviceWifiConfig(
                        wifiConfig.autoConnectEnabled,
                        wifiConfig.ssid,
                        wifiConfig.password,
                        wifiConfig.bssid,
                        wifiConfig.hiddenSsid,
                        ip,
                        port),
                new DeviceWifiConnector.Callback() {
                    @Override
                    public void onStateChanged(DeviceWifiState state, @Nullable String detail) {
                        updateWifiState(state, detail);
                    }

                    @Override
                    public void onAvailable(Network network, DeviceWifiConfig config) {
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_DEVICE,
                                context.getString(R.string.operation_log_title_wifi_hotspot_connected),
                                buildWifiTargetDetail(config, null),
                                databaseName);
                        connectTcp(config.ip, config.port, network);
                    }

                    @Override
                    public void onUnavailable(String message, DeviceWifiConfig config) {
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_DEVICE,
                                context.getString(R.string.operation_log_title_wifi_hotspot_failed),
                                buildWifiTargetDetail(config, message),
                                databaseName);
                        emitMessage(message);
                    }

                    @Override
                    public void onLost(String message, DeviceWifiConfig config) {
                        isCollecting = false;
                        tcpClient.setSocketFactory(null);
                        tcpClient.disconnect();
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_DEVICE,
                                context.getString(R.string.operation_log_title_wifi_hotspot_lost),
                                buildWifiTargetDetail(config, message),
                                databaseName);
                        emitMessage(message);
                    }

                    @Override
                    public void onError(String message, @Nullable DeviceWifiConfig config) {
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_DEVICE,
                                context.getString(R.string.operation_log_title_wifi_hotspot_failed),
                                buildWifiTargetDetail(config, message),
                                databaseName);
                        emitMessage(message);
                    }

                    @Override
                    public void onPermissionRequired(String message, DeviceWifiConfig config) {
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_DEVICE,
                                context.getString(R.string.operation_log_title_wifi_hotspot_failed),
                                buildWifiTargetDetail(config, message),
                                databaseName);
                        emitMessage(message);
                    }

                    @Override
                    public void onUnsupported(String message, DeviceWifiConfig config) {
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_DEVICE,
                                context.getString(R.string.operation_log_title_wifi_hotspot_failed),
                                buildWifiTargetDetail(config, message),
                                databaseName);
                        emitMessage(message);
                        connectTcp(config.ip, config.port, null);
                    }
                });
    }

    public void requestDisconnect() {
        if (wifiConnector != null) {
            wifiConnector.disconnect();
            updateWifiState(
                    AppSettings.isDeviceWifiAutoConnectEnabled(context)
                            ? DeviceWifiState.DISCONNECTED
                            : DeviceWifiState.IDLE,
                    AppSettings.isDeviceWifiAutoConnectEnabled(context)
                            ? context.getString(R.string.wifi_status_disconnected)
                            : context.getString(R.string.wifi_status_idle));
        }
        if (tcpClient != null) {
            if (tcpClient.getConnectionState() != TcpClientManager.ConnectionState.DISCONNECTED) {
                operationLogStore.record(
                        OperationLogStore.CATEGORY_DEVICE,
                        context.getString(R.string.operation_log_title_disconnect_device),
                        buildConfiguredTargetDetail(),
                        databaseName);
            }
            tcpClient.setSocketFactory(null);
            tcpClient.disconnect();
        }
    }

    public void startCollection(@Nullable SurveyLineEntity currentLine,
                                @Nullable CollectionParameterEntity parameters) {
        if (readOnlyProject) {
            if (callback != null) {
                callback.onMessage(context.getString(R.string.toast_project_read_only_blocked));
            }
            return;
        }
        ensureManagersReady();
        if (tcpClient == null || !tcpClient.isConnected()) {
            if (callback != null) {
                callback.onMessage(context.getString(R.string.toast_connect_antenna_first));
            }
            return;
        }
        if (currentLine == null) {
            if (callback != null) {
                callback.onMessage(context.getString(R.string.toast_line_not_ready));
            }
            return;
        }

        currentSampleFrequency = parameters != null && parameters.getSampleFrequency() > 0
                ? parameters.getSampleFrequency()
                : 300;
        currentSendFrequency = parameters != null && parameters.getTransmitCurrent() > 0f
                ? parameters.getTransmitCurrent()
                : 25f;
        currentAuxiliarySampleFrequency = chartStateFactory.resolveAuxiliarySampleFrequency(parameters);
        collectionManager.setParameters(parameters);
        isCollecting = true;
        hasNewData = false;
        currentMonitor = null;
        lastCollectionLineLabel = formatNumber(currentLine.getName());
        if (callback != null) {
            callback.onMonitorChanged(null);
        }
        operationLogStore.record(
                OperationLogStore.CATEGORY_COLLECTION,
                context.getString(R.string.operation_log_title_start_collection),
                buildCollectionStartDetail(currentLine, parameters),
                databaseName);
        collectionManager.startCollection();
        dispatchActionState();
    }

    public void requestStopCollection() {
        if (collectionManager != null) {
            if (isCollecting) {
                operationLogStore.record(
                        OperationLogStore.CATEGORY_COLLECTION,
                        context.getString(R.string.operation_log_title_stop_collection),
                        lastCollectionLineLabel.isEmpty()
                                ? ""
                                : context.getString(R.string.operation_log_detail_line_only, lastCollectionLineLabel),
                        databaseName);
            }
            collectionManager.stopCollection();
        }
    }

    public boolean hasPendingResult() {
        return hasNewData;
    }

    public void clearPendingResult() {
        hasNewData = false;
        dispatchActionState();
    }

    @Nullable
    public DeviceMonitorEntity getCurrentMonitor() {
        return currentMonitor;
    }

    public void syncActionState() {
        dispatchActionState();
    }

    public void setReadOnlyProject(boolean readOnlyProject) {
        this.readOnlyProject = readOnlyProject;
        if (readOnlyProject) {
            hasNewData = false;
            isCollecting = false;
            currentMonitor = null;
            if (callback != null) {
                callback.onMonitorChanged(null);
            }
            if (collectionManager != null) {
                collectionManager.stopCollection();
            }
            if (wifiConnector != null) {
                wifiConnector.disconnect();
            }
            if (tcpClient != null) {
                tcpClient.setSocketFactory(null);
                tcpClient.disconnect();
            }
            updateWifiState(
                    AppSettings.isDeviceWifiAutoConnectEnabled(context)
                            ? DeviceWifiState.DISCONNECTED
                            : DeviceWifiState.IDLE,
                    AppSettings.isDeviceWifiAutoConnectEnabled(context)
                            ? context.getString(R.string.wifi_status_disconnected)
                            : context.getString(R.string.wifi_status_idle));
        }
        dispatchActionState();
    }

    public void release() {
        if (collectionManager != null) {
            collectionManager.removeDataCallback(collectionCallback);
            collectionManager = null;
        }
        if (tcpClient != null) {
            tcpClient.setConnectionListener(null);
            tcpClient.setSocketFactory(null);
            tcpClient.disconnect();
            tcpClient = null;
        }
        if (wifiConnector != null) {
            wifiConnector.disconnect();
            wifiConnector = null;
        }
        wifiState = DeviceWifiState.IDLE;
    }

    private void ensureManagersReady() {
        if (tcpClient == null) {
            tcpClient = new TcpClientManager();
            tcpClient.setSimulatedDeviceEnabled(AppSettings.isTcpSimulationEnabled(context));
            tcpClient.setConnectionParams(
                    AppSettings.getTcpIp(context),
                    AppSettings.getTcpPort(context));
            tcpClient.setConnectionListener(connectionListener);
        }
        if (wifiConnector == null) {
            wifiConnector = new DeviceWifiConnector(context);
        }
        if (collectionManager == null) {
            collectionManager = new CollectionManager(tcpClient);
            collectionManager.addDataCallback(collectionCallback);
        }
        dispatchActionState(tcpClient.getConnectionState());
    }

    private void dispatchActionState() {
        dispatchActionState(tcpClient != null
                ? tcpClient.getConnectionState()
                : TcpClientManager.ConnectionState.DISCONNECTED);
    }

    private void dispatchActionState(@Nullable TcpClientManager.ConnectionState connectionState) {
        if (callback != null) {
            callback.onActionState(buildActionState(connectionState));
        }
    }

    private void logConnectionState(@Nullable TcpClientManager.ConnectionState state) {
        TcpClientManager.ConnectionState safeState = state == null
                ? TcpClientManager.ConnectionState.DISCONNECTED
                : state;
        if (!connectionStateInitialized) {
            connectionStateInitialized = true;
            lastLoggedConnectionState = safeState;
            return;
        }
        if (safeState == lastLoggedConnectionState) {
            return;
        }
        lastLoggedConnectionState = safeState;

        String title;
        switch (safeState) {
            case CONNECTED:
                title = context.getString(R.string.operation_log_title_device_connected);
                break;
            case RECONNECTING:
                title = context.getString(R.string.operation_log_title_device_reconnecting);
                break;
            case DISCONNECTED:
                title = context.getString(R.string.operation_log_title_device_disconnected);
                break;
            case CONNECTING:
            default:
                return;
        }
        operationLogStore.record(
                OperationLogStore.CATEGORY_DEVICE,
                title,
                buildConfiguredTargetDetail(),
                databaseName);
    }

    private String buildConfiguredTargetDetail() {
        return buildTargetDetail(AppSettings.getTcpIp(context), AppSettings.getTcpPort(context));
    }

    private void connectTcp(@Nullable String ip, int port, @Nullable Network network) {
        if (tcpClient == null) {
            return;
        }
        tcpClient.setSocketFactory(network == null ? null : network.getSocketFactory());
        operationLogStore.record(
                OperationLogStore.CATEGORY_DEVICE,
                context.getString(R.string.operation_log_title_connect_device),
                buildTargetDetail(ip, port),
                databaseName);
        tcpClient.connect();
    }

    private String buildTargetDetail(@Nullable String ip, int port) {
        if (simulationEnabled) {
            return context.getString(R.string.operation_log_detail_target_simulated);
        }
        return context.getString(
                R.string.operation_log_detail_target,
                safeText(ip),
                port);
    }

    private String buildWifiTargetDetail(@Nullable DeviceWifiConfig config, @Nullable String reason) {
        String hotspot = config == null ? "" : safeText(config.ssid);
        String detail = safeText(reason);
        if (detail.isEmpty()) {
            return context.getString(R.string.operation_log_detail_wifi_hotspot, hotspot);
        }
        return context.getString(
                R.string.operation_log_detail_wifi_hotspot_with_reason,
                hotspot,
                detail);
    }

    private String buildCollectionStartDetail(@Nullable SurveyLineEntity currentLine,
                                              @Nullable CollectionParameterEntity parameters) {
        String lineLabel = currentLine == null ? "--" : formatNumber(currentLine.getName());
        if (parameters == null) {
            return context.getString(R.string.operation_log_detail_line_only, lineLabel);
        }
        return context.getString(
                R.string.operation_log_detail_collection_start,
                lineLabel,
                formatNumber(parameters.getTransmitCurrent()),
                parameters.getSampleFrequency(),
                parameters.getCollectionCount(),
                formatNumber(parameters.getSampleTime()));
    }

    private String formatNumber(float value) {
        return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
    }

    private String safeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private void updateWifiState(@Nullable DeviceWifiState state, @Nullable String detail) {
        DeviceWifiState safeState = state == null ? DeviceWifiState.IDLE : state;
        String wifiSsid = AppSettings.isDeviceWifiAutoConnectEnabled(context)
                ? AppSettings.getDeviceWifiSsid(context)
                : "";
        TcpDiagnosticsStore.getInstance().recordWifiState(safeState, wifiSsid, detail);
        if (wifiState == safeState) {
            if (callback != null) {
                callback.onWifiStateChanged(wifiState);
            }
            dispatchActionState();
            return;
        }
        wifiState = safeState;
        if (callback != null) {
            callback.onWifiStateChanged(wifiState);
        }
        dispatchActionState();
    }

    private void emitMessage(@Nullable String message) {
        String safeMessage = safeText(message);
        if (!safeMessage.isEmpty() && callback != null) {
            callback.onMessage(safeMessage);
        }
    }

    private CollectionViewModel.ActionState buildActionState(
            @Nullable TcpClientManager.ConnectionState connectionState) {
        if (connectionState == null) {
            connectionState = TcpClientManager.ConnectionState.DISCONNECTED;
        }

        CollectionViewModel.ActionState state = new CollectionViewModel.ActionState();
        boolean connected = connectionState == TcpClientManager.ConnectionState.CONNECTED;
        boolean connecting = wifiState == DeviceWifiState.CONNECTING
                || connectionState == TcpClientManager.ConnectionState.CONNECTING
                || connectionState == TcpClientManager.ConnectionState.RECONNECTING;

        if (connected) {
            state.connectionStatusRes = R.string.connection_status_connected;
            state.connectButtonRes = R.string.action_disconnect;
        } else if (connecting) {
            state.connectionStatusRes = R.string.connection_status_connecting;
            state.connectButtonRes = R.string.action_connecting;
        } else {
            state.connectionStatusRes = R.string.connection_status_disconnected;
            state.connectButtonRes = R.string.action_connect;
        }

        if (isCollecting) {
            state.progressStatusRes = R.string.progress_collecting;
        } else if (hasNewData) {
            state.progressStatusRes = R.string.progress_completed;
        } else {
            state.progressStatusRes = R.string.progress_idle;
        }

        if (readOnlyProject) {
            state.progressStatusRes = R.string.progress_playback;
            state.connectEnabled = false;
            state.startEnabled = false;
            state.stopEnabled = false;
            state.saveEnabled = false;
            state.nextEnabled = false;
            return state;
        }

        state.connectEnabled = !isCollecting && !connecting;
        state.startEnabled = connected && !isCollecting;
        state.stopEnabled = isCollecting;
        state.saveEnabled = !isCollecting && hasNewData;
        state.nextEnabled = !isCollecting;
        return state;
    }

    public interface Callback {
        void onWaveformResult(CollectionChartStateFactory.LiveWaveformResult result);

        void onMonitorChanged(@Nullable DeviceMonitorEntity monitor);

        void onConnectionStateChanged(TcpClientManager.ConnectionState state);

        void onWifiStateChanged(DeviceWifiState state);

        void onActionState(CollectionViewModel.ActionState state);

        void onMessage(String message);
    }
}
