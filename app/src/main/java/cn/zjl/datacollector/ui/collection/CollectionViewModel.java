package cn.zjl.datacollector.ui.collection;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.collection.CollectionManager;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.net.tcp.TcpClientManager;
import cn.zjl.datacollector.sync.DataSyncWorker;
import cn.zjl.datacollector.ui.playback.PointListItem;
import cn.zjl.datacollector.util.AppSettings;

/**
 * 负责数据采集与显示页面的状态管理。
 * 这里集中处理仓库访问、树节点选择、连接状态、采集状态和保存流程，
 * Activity 只负责界面渲染、弹框与表单采集。
 */
public class CollectionViewModel extends AndroidViewModel {

    /**
     * 一次性事件包装，避免旋转后重复消费 Toast 或导航事件。
     */
    public static class Event<T> {
        private final T content;
        private boolean handled;

        public Event(T content) {
            this.content = content;
        }

        @Nullable
        public T getContentIfNotHandled() {
            if (handled) {
                return null;
            }
            handled = true;
            return content;
        }
    }

    /**
     * 当前界面高亮与面包屑状态。
     */
    public static class SelectionState {
        public int type = -1;
        public long nodeId = -1L;
        public long pointId = -1L;
        public int sessionIndex = -1;
        public String breadcrumb;
    }

    /**
     * 按钮可用性和连接文案统一从这里下发，避免 Activity 自己拼状态。
     */
    public static class ActionState {
        public int connectionStatusRes = R.string.connection_status_disconnected;
        public int progressStatusRes = R.string.progress_idle;
        public int connectButtonRes = R.string.action_connect;
        public boolean connectEnabled;
        public boolean startEnabled;
        public boolean stopEnabled;
        public boolean saveEnabled;
        public boolean nextEnabled;
    }

    /**
     * 图表区域统一使用一种状态对象，便于 Activity 判断渲染聚合图还是单次图。
     */
    public static class ChartDisplayState {
        public boolean aggregateMode;
        public List<WaveformDataEntity> aggregateWaveforms = new ArrayList<>();
        public float recvFs;
        public float sendFs;
        public float sampleSendFs;
        public float sampleOffFs;
        public int period;
        public int repeatCount;
        public WaveformSessionResolver.WaveformRenderState waveformState =
                new WaveformSessionResolver.WaveformRenderState();
    }

    /**
     * 表单区域只关心点号和参数回填。
     */
    public static class FormState {
        public MeasurementPointEntity point;
        public CollectionParameterEntity parameters;
    }

    private final MutableLiveData<ProjectEntity> projectLiveData = new MutableLiveData<>();
    private final MutableLiveData<DataRepository.ProjectTreeSummary> treeLiveData = new MutableLiveData<>();
    private final MutableLiveData<SelectionState> selectionLiveData = new MutableLiveData<>();
    private final MutableLiveData<ChartDisplayState> chartDisplayLiveData = new MutableLiveData<>();
    private final MutableLiveData<DeviceMonitorEntity> monitorLiveData = new MutableLiveData<>();
    private final MutableLiveData<FormState> formStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<ActionState> actionStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messageEvent = new MutableLiveData<>();
    private final MutableLiveData<Event<List<SurveyLineEntity>>> surveyLinesEvent = new MutableLiveData<>();
    private final MutableLiveData<Event<Float>> pointSavedEvent = new MutableLiveData<>();

    private final WaveformSessionResolver waveformSessionResolver = new WaveformSessionResolver();

    private DataRepository dataRepository;
    private TcpClientManager tcpClient;
    private CollectionManager collectionManager;

    private String databaseName;
    private int currentMode = CollectionAndPlaybackActivity.COLLECTION_MODE;
    private ProjectEntity project;
    private SurveyLineEntity currentLine;
    private boolean initialSelectionPending;
    private boolean initialSelectionCompleted;
    private boolean isCollecting;
    private boolean hasNewData;
    private int currentSampleFrequency = 300;
    private float currentAuxiliarySampleFrequency = 0f;

    private float[] currentTimeAxis = new float[0];
    private float[] currentRecvTimeAxis = new float[0];
    private float[] currentSendTimeAxis = new float[0];
    private float[] currentOffTimeAxis = new float[0];
    private float[] currentRecv = new float[0];
    private float[] currentSend = new float[0];
    private float[] currentOff = new float[0];
    private DeviceMonitorEntity currentMonitor;

    private final CollectionManager.DataCallback collectionCallback = new CollectionManager.DataCallback() {
        @Override
        public void onWaveformData(float[] timePoints, float[] recvValues, float[] sendValues, float[] offValues) {
            currentTimeAxis = trimAxis(timePoints, recvValues, sendValues, offValues);
            currentRecv = trimValues(recvValues, currentTimeAxis.length);
            currentSend = trimValues(sendValues, effectiveLength(sendValues));
            currentOff = trimValues(offValues, effectiveLength(offValues));
            currentRecvTimeAxis = currentTimeAxis;
            currentSendTimeAxis = buildUniformTimeAxis(currentSend.length, currentAuxiliarySampleFrequency);
            currentOffTimeAxis = buildUniformTimeAxis(currentOff.length, currentAuxiliarySampleFrequency);
            hasNewData = currentRecv.length > 0 || currentSend.length > 0 || currentOff.length > 0;

            postSingleWaveformState();
            updateActionState();
        }

        @Override
        public void onMonitorInfo(DeviceMonitorEntity monitor) {
            currentMonitor = monitor;
            monitorLiveData.postValue(monitor);
        }

        @Override
        public void onCollectionComplete() {
            isCollecting = false;
            updateActionState();
        }

        @Override
        public void onError(String error) {
            isCollecting = false;
            updateActionState();
            emitMessage(error);
        }
    };

    private final TcpClientManager.ConnectionListener connectionListener = new TcpClientManager.ConnectionListener() {
        @Override
        public void onConnectionStateChanged(TcpClientManager.ConnectionState state) {
            updateActionState(state);
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
            updateActionState();
            emitMessage(error);
        }
    };

    public CollectionViewModel(@NonNull Application application) {
        super(application);
        chartDisplayLiveData.setValue(new ChartDisplayState());
        actionStateLiveData.setValue(new ActionState());
    }

    public LiveData<ProjectEntity> getProjectLiveData() {
        return projectLiveData;
    }

    public LiveData<DataRepository.ProjectTreeSummary> getTreeLiveData() {
        return treeLiveData;
    }

    public LiveData<SelectionState> getSelectionLiveData() {
        return selectionLiveData;
    }

    public LiveData<ChartDisplayState> getChartDisplayLiveData() {
        return chartDisplayLiveData;
    }

    public LiveData<DeviceMonitorEntity> getMonitorLiveData() {
        return monitorLiveData;
    }

    public LiveData<FormState> getFormStateLiveData() {
        return formStateLiveData;
    }

    public LiveData<ActionState> getActionStateLiveData() {
        return actionStateLiveData;
    }

    public LiveData<Event<String>> getMessageEvent() {
        return messageEvent;
    }

    public LiveData<Event<List<SurveyLineEntity>>> getSurveyLinesEvent() {
        return surveyLinesEvent;
    }

    public LiveData<Event<Float>> getPointSavedEvent() {
        return pointSavedEvent;
    }

    public void initialize(@NonNull String databaseName, int mode) {
        if (databaseName.equals(this.databaseName) && dataRepository != null && currentMode == mode) {
            return;
        }

        releaseManagers();

        this.databaseName = databaseName;
        this.currentMode = mode;
        this.project = null;
        this.currentLine = null;
        this.initialSelectionPending = false;
        this.initialSelectionCompleted = false;
        this.isCollecting = false;
        this.hasNewData = false;
        this.currentAuxiliarySampleFrequency = 0f;
        this.currentMonitor = null;
        dataRepository = new DataRepository(getApplication(), databaseName);

        postEmptyChart();
        monitorLiveData.postValue(null);
        updateActionState(TcpClientManager.ConnectionState.DISCONNECTED);
        loadProjectContext();
    }

    /**
     * 初次打开页面时，默认请求界面选中第一条测线。
     */
    public boolean consumeInitialSelectionPending() {
        boolean pending = initialSelectionPending;
        initialSelectionPending = false;
        if (pending) {
            initialSelectionCompleted = true;
        }
        return pending;
    }

    public void requestConnect(String ip, int port) {
        if (currentMode == CollectionAndPlaybackActivity.PLAYBACK_MODE) {
            return;
        }
        ensureManagersReady();
        AppSettings.saveTcp(getApplication(), ip, port);
        tcpClient.setConnectionParams(ip, port);
        tcpClient.connect();
    }

    public void requestDisconnect() {
        if (tcpClient != null) {
            tcpClient.disconnect();
        }
    }

    public void startCollection(CollectionParameterEntity parameters) {
        if (currentMode == CollectionAndPlaybackActivity.PLAYBACK_MODE) {
            return;
        }
        if (tcpClient == null || !tcpClient.isConnected()) {
            emitMessage(R.string.toast_connect_antenna_first);
            return;
        }
        if (currentLine == null) {
            emitMessage(R.string.toast_line_not_ready);
            return;
        }

        ensureManagersReady();
        currentSampleFrequency = parameters != null && parameters.sampleFrequency > 0
                ? parameters.sampleFrequency
                : 300;
        currentAuxiliarySampleFrequency = resolveAuxiliarySampleFrequency(parameters);
        collectionManager.setParameters(parameters);
        isCollecting = true;
        hasNewData = false;
        currentRecv = new float[0];
        currentSend = new float[0];
        currentOff = new float[0];
        currentRecvTimeAxis = new float[0];
        currentSendTimeAxis = new float[0];
        currentOffTimeAxis = new float[0];
        currentMonitor = null;
        monitorLiveData.postValue(null);
        postEmptyChart();
        collectionManager.startCollection();
        updateActionState();
    }

    public void requestStopCollection() {
        if (collectionManager != null) {
            collectionManager.stopCollection();
        }
    }

    public boolean canSavePendingResult() {
        return hasNewData;
    }

    public void discardPendingResult() {
        hasNewData = false;
        updateActionState();
        emitMessage(R.string.toast_abandon_result);
    }

    public void saveCurrentPoint(float pointNumber,
                                 CollectionParameterEntity parameters,
                                 @Nullable Location location,
                                 boolean qualified,
                                 @Nullable String judgeNote) {
        if (currentLine == null) {
            emitMessage(R.string.toast_line_not_ready);
            return;
        }
        if (!Float.isFinite(pointNumber)) {
            emitMessage(R.string.toast_invalid_point_number);
            return;
        }

        double latitude = location != null ? location.getLatitude() : 0d;
        double longitude = location != null ? location.getLongitude() : 0d;
        double altitude = location != null ? location.getAltitude() : 0d;

        dataRepository.saveCollectionSession(
                currentLine.id,
                pointNumber,
                0,
                "",
                parameters,
                currentRecvTimeAxis,
                currentRecv,
                currentSend,
                currentOff,
                currentMonitor,
                latitude,
                longitude,
                altitude,
                qualified,
                judgeNote == null ? "" : judgeNote,
                new DataRepository.SaveCallback<MeasurementPointEntity>() {
                    @Override
                    public void onSuccess(MeasurementPointEntity result) {
                        hasNewData = false;
                        emitMessage(R.string.toast_point_saved);
                        pointSavedEvent.postValue(new Event<>(pointNumber));
                        updateActionState();
                        loadPointTree();
                        selectPointById(result.id, buildPointBreadcrumb(result.name), true);
                        DataSyncWorker.scheduleProjectSync(getApplication(), databaseName);
                    }

                    @Override
                    public void onError(String error) {
                        emitMessage(getApplication().getString(R.string.toast_save_failed, error));
                    }
                });
    }

    public void requestSurveyLines() {
        if (project == null || dataRepository == null) {
            return;
        }
        dataRepository.getSurveyLinesByProject(project.id, lines ->
                surveyLinesEvent.postValue(new Event<>(lines == null ? new ArrayList<>() : lines)));
    }

    public void createLine(float lineNumber, @Nullable String note) {
        if (project == null || dataRepository == null) {
            return;
        }
        dataRepository.createSurveyLine(project.id, lineNumber, note == null ? "" : note, new DataRepository.SaveCallback<Long>() {
            @Override
            public void onSuccess(Long result) {
                currentLine = new SurveyLineEntity();
                currentLine.id = result;
                currentLine.name = lineNumber;
                emitMessage("测线已创建");
                loadPointTree();
            }

            @Override
            public void onError(String error) {
                emitMessage(error);
            }
        });
    }

    public void requestCopyPreviousParameters(float currentPointNumber) {
        if (currentLine == null) {
            emitMessage(R.string.toast_line_not_ready);
            return;
        }
        if (!Float.isFinite(currentPointNumber)) {
            emitMessage(R.string.toast_invalid_point_number);
            return;
        }
        dataRepository.getPreviousPointParameters(currentLine.id, currentPointNumber, parameters -> {
            if (parameters == null) {
                emitMessage(R.string.toast_previous_point_no_params);
                return;
            }
            FormState formState = new FormState();
            formState.parameters = parameters;
            formStateLiveData.postValue(formState);
        });
    }

    public void selectProject(PointListItem item) {
        initialSelectionCompleted = true;
        postSelection(item.type, item.nodeId, -1L, -1, item.breadcrumb);
    }

    public void selectLine(PointListItem item) {
        initialSelectionCompleted = true;
        currentLine = new SurveyLineEntity();
        currentLine.id = item.nodeId;
        currentLine.name = parseLineName(item.title);
        postSelection(item.type, item.nodeId, -1L, -1, item.breadcrumb);
        monitorLiveData.postValue(null);

        dataRepository.getAllWaveformsByLine(item.nodeId, waveforms -> {
            ChartDisplayState chartState = new ChartDisplayState();
            chartState.aggregateMode = true;
            chartState.aggregateWaveforms = waveforms == null ? new ArrayList<>() : waveforms;
            applyWaveformMeta(chartState, chartState.aggregateWaveforms);
            chartDisplayLiveData.postValue(chartState);
        });
    }

    public void selectPoint(PointListItem item, boolean fromSave) {
        initialSelectionCompleted = true;
        selectPointById(item.pointId, item.breadcrumb, fromSave);
    }

    public void selectSession(PointListItem item) {
        initialSelectionCompleted = true;
        postSelection(item.type, item.nodeId, item.pointId, item.sessionIndex, item.breadcrumb);
        dataRepository.getAllDataByPoint(item.pointId, pointData -> {
            if (pointData == null || pointData.point == null) {
                return;
            }
            currentLine = ensureCurrentLine(pointData.point.dataLineId);
            postFormState(pointData.point, pointData.parameters);
            monitorLiveData.postValue(findLatestMonitor(pointData.monitors));

            WaveformSessionResolver.WaveformSession session =
                    waveformSessionResolver.selectWaveformSession(pointData.waveforms, item.sessionIndex);
            if (session == null) {
                postEmptyChart();
                return;
            }

            ChartDisplayState chartState = new ChartDisplayState();
            chartState.waveformState = waveformSessionResolver.prepareWaveformState(session.waveforms);
            applyWaveformMeta(chartState, session.waveforms);
            chartDisplayLiveData.postValue(chartState);
        });
    }

    private void loadProjectContext() {
        dataRepository.getProject(projectResult -> {
            project = projectResult;
            projectLiveData.postValue(projectResult);
            if (projectResult == null) {
                ensureManagersReady();
                loadPointTree();
                return;
            }

            dataRepository.ensureDefaultSurveyLine(projectResult.id, new DataRepository.SaveCallback<SurveyLineEntity>() {
                @Override
                public void onSuccess(SurveyLineEntity result) {
                    currentLine = result;
                    ensureManagersReady();
                    loadPointTree();
                }

                @Override
                public void onError(String error) {
                    emitMessage(error);
                    loadPointTree();
                }
            });
        });
    }

    private void loadPointTree() {
        dataRepository.getProjectTree(tree -> {
            treeLiveData.postValue(tree);
            initialSelectionPending = !initialSelectionCompleted
                    && tree != null
                    && tree.lines != null
                    && !tree.lines.isEmpty();
        });
    }

    private void selectPointById(long pointId, @Nullable String breadcrumb, boolean fromSave) {
        dataRepository.getAllDataByPoint(pointId, pointData -> {
            if (pointData == null || pointData.point == null) {
                return;
            }

            currentLine = ensureCurrentLine(pointData.point.dataLineId);
            postSelection(
                    PointListItem.TYPE_POINT,
                    pointData.point.id,
                    pointData.point.id,
                    -1,
                    breadcrumb != null ? breadcrumb : buildPointBreadcrumb(pointData.point.name));
            postFormState(pointData.point, pointData.parameters);
            monitorLiveData.postValue(findLatestMonitor(pointData.monitors));

            ChartDisplayState chartState = new ChartDisplayState();
            chartState.aggregateMode = true;
            chartState.aggregateWaveforms = pointData.waveforms == null ? new ArrayList<>() : pointData.waveforms;
            applyWaveformMeta(chartState, chartState.aggregateWaveforms);
            chartDisplayLiveData.postValue(chartState);

            if (fromSave) {
                initialSelectionPending = false;
            }
        });
    }

    private void ensureManagersReady() {
        if (currentMode == CollectionAndPlaybackActivity.PLAYBACK_MODE) {
            updateActionState(TcpClientManager.ConnectionState.DISCONNECTED);
            return;
        }
        if (tcpClient == null) {
            tcpClient = new TcpClientManager();
            tcpClient.setConnectionParams(AppSettings.getTcpIp(getApplication()), AppSettings.getTcpPort(getApplication()));
            tcpClient.setConnectionListener(connectionListener);
        }
        if (collectionManager == null) {
            collectionManager = new CollectionManager(tcpClient);
            collectionManager.addDataCallback(collectionCallback);
        }
        updateActionState(tcpClient.getConnectionState());
    }

    private void releaseManagers() {
        if (collectionManager != null) {
            collectionManager.removeDataCallback(collectionCallback);
            collectionManager = null;
        }
        if (tcpClient != null) {
            tcpClient.setConnectionListener(null);
            tcpClient.disconnect();
            tcpClient = null;
        }
    }

    private void postSelection(int type, long nodeId, long pointId, int sessionIndex, @Nullable String breadcrumb) {
        SelectionState state = new SelectionState();
        state.type = type;
        state.nodeId = nodeId;
        state.pointId = pointId;
        state.sessionIndex = sessionIndex;
        state.breadcrumb = breadcrumb;
        selectionLiveData.postValue(state);
    }

    private void postFormState(@Nullable MeasurementPointEntity point, @Nullable CollectionParameterEntity parameters) {
        FormState formState = new FormState();
        formState.point = point;
        formState.parameters = parameters;
        formStateLiveData.postValue(formState);
    }

    private void postSingleWaveformState() {
        ChartDisplayState chartState = new ChartDisplayState();
        chartState.recvFs = currentSampleFrequency;
        chartState.sampleSendFs = currentAuxiliarySampleFrequency;
        chartState.sampleOffFs = currentAuxiliarySampleFrequency;
        chartState.waveformState.recvTimes = currentRecvTimeAxis;
        chartState.waveformState.recvValues = currentRecv;
        chartState.waveformState.sendTimes = currentSendTimeAxis;
        chartState.waveformState.sendValues = currentSend;
        chartState.waveformState.offTimes = currentOffTimeAxis;
        chartState.waveformState.offValues = currentOff;
        chartDisplayLiveData.postValue(chartState);
    }

    private void postEmptyChart() {
        chartDisplayLiveData.postValue(new ChartDisplayState());
    }

    private void updateActionState() {
        updateActionState(tcpClient != null ? tcpClient.getConnectionState() : TcpClientManager.ConnectionState.DISCONNECTED);
    }

    private void updateActionState(@Nullable TcpClientManager.ConnectionState connectionState) {
        if (connectionState == null) {
            connectionState = TcpClientManager.ConnectionState.DISCONNECTED;
        }

        ActionState state = new ActionState();
        boolean inCollectionMode = currentMode == CollectionAndPlaybackActivity.COLLECTION_MODE;
        boolean connected = connectionState == TcpClientManager.ConnectionState.CONNECTED;

        switch (connectionState) {
            case CONNECTING:
            case RECONNECTING:
                state.connectionStatusRes = R.string.connection_status_connecting;
                state.connectButtonRes = R.string.action_connecting;
                break;
            case CONNECTED:
                state.connectionStatusRes = R.string.connection_status_connected;
                state.connectButtonRes = R.string.action_disconnect;
                break;
            case DISCONNECTED:
            default:
                state.connectionStatusRes = R.string.connection_status_disconnected;
                state.connectButtonRes = R.string.action_connect;
                break;
        }

        if (currentMode == CollectionAndPlaybackActivity.PLAYBACK_MODE) {
            state.progressStatusRes = R.string.progress_playback;
        } else if (isCollecting) {
            state.progressStatusRes = R.string.progress_collecting;
        } else if (hasNewData) {
            state.progressStatusRes = R.string.progress_completed;
        } else {
            state.progressStatusRes = R.string.progress_idle;
        }

        state.connectEnabled = inCollectionMode && !isCollecting;
        state.startEnabled = inCollectionMode && connected && !isCollecting;
        state.stopEnabled = inCollectionMode && isCollecting;
        state.saveEnabled = inCollectionMode && !isCollecting && hasNewData;
        state.nextEnabled = inCollectionMode && !isCollecting;
        actionStateLiveData.postValue(state);
    }

    private void applyWaveformMeta(@NonNull ChartDisplayState chartState,
                                   @Nullable List<WaveformDataEntity> waveforms) {
        if (waveforms == null || waveforms.isEmpty()) {
            return;
        }
        for (int i = waveforms.size() - 1; i >= 0; i--) {
            WaveformDataEntity waveform = waveforms.get(i);
            if (waveform == null) {
                continue;
            }
            if (chartState.recvFs <= 0f && waveform.recvFs > 0f) {
                chartState.recvFs = waveform.recvFs;
            }
            if (chartState.sendFs <= 0f && waveform.sendFs > 0f) {
                chartState.sendFs = waveform.sendFs;
            }
            if (chartState.sampleSendFs <= 0f && waveform.simpleSendFs > 0f) {
                chartState.sampleSendFs = waveform.simpleSendFs;
            }
            if (chartState.sampleOffFs <= 0f && waveform.simpleOffFs > 0f) {
                chartState.sampleOffFs = waveform.simpleOffFs;
            }
            if (chartState.period <= 0 && waveform.period > 0) {
                chartState.period = waveform.period;
            }
        }
        chartState.repeatCount = waveformSessionResolver.buildWaveformSessions(waveforms).size();
        if (chartState.repeatCount < 0) {
            chartState.repeatCount = 0;
        }
    }

    private DeviceMonitorEntity findLatestMonitor(@Nullable List<DeviceMonitorEntity> monitors) {
        if (monitors == null || monitors.isEmpty()) {
            return null;
        }
        DeviceMonitorEntity latest = monitors.get(0);
        for (int i = 1; i < monitors.size(); i++) {
            if (monitors.get(i).timestamp >= latest.timestamp) {
                latest = monitors.get(i);
            }
        }
        return latest;
    }

    private SurveyLineEntity ensureCurrentLine(long lineId) {
        if (currentLine == null) {
            currentLine = new SurveyLineEntity();
        }
        currentLine.id = lineId;
        return currentLine;
    }

    private String buildPointBreadcrumb(float pointName) {
        return getApplication().getString(R.string.tree_point_title, (int) pointName);
    }

    private void emitMessage(int resId) {
        emitMessage(getApplication().getString(resId));
    }

    private void emitMessage(String message) {
        messageEvent.postValue(new Event<>(message));
    }

    private float[] trimAxis(float[] axis, float[] recvValues, float[] sendValues, float[] offValues) {
        int length = Math.max(
                effectiveLength(axis),
                Math.max(effectiveLength(recvValues), Math.max(effectiveLength(sendValues), effectiveLength(offValues))));
        if (length <= 0 || axis == null || axis.length == 0) {
            return new float[0];
        }
        float[] trimmed = new float[Math.min(length, axis.length)];
        System.arraycopy(axis, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private float[] trimValues(float[] values, int length) {
        if (values == null || values.length == 0 || length <= 0) {
            return new float[0];
        }
        int safeLength = Math.min(length, values.length);
        float[] trimmed = new float[safeLength];
        System.arraycopy(values, 0, trimmed, 0, safeLength);
        return trimmed;
    }

    private int effectiveLength(float[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        for (int i = values.length - 1; i >= 0; i--) {
            float value = values[i];
            if (Float.isFinite(value) && Math.abs(value) > 1.0e-9f) {
                return i + 1;
            }
        }
        return 0;
    }

    private float resolveAuxiliarySampleFrequency(@Nullable CollectionParameterEntity parameters) {
        if (parameters == null) {
            return 0f;
        }
        if (parameters.sampleTime > 0f) {
            return 1_000_000f / parameters.sampleTime;
        }
        if (parameters.sampleFrequency > 0) {
            return parameters.sampleFrequency;
        }
        return 0f;
    }

    private float[] buildUniformTimeAxis(int count, float sampleRateHz) {
        if (count <= 0) {
            return new float[0];
        }
        float sampleFrequency = sampleRateHz > 0f
                ? sampleRateHz
                : (currentSampleFrequency > 0 ? currentSampleFrequency : 300f);
        float stepUs = sampleFrequency > 0f ? 1_000_000f / sampleFrequency : 1f;
        float[] axis = new float[count];
        for (int i = 0; i < count; i++) {
            axis[i] = i * stepUs;
        }
        return axis;
    }

    private float parseLineName(@Nullable String title) {
        if (title == null) {
            return 0f;
        }
        String digits = title.replaceAll("[^0-9.\\-]", "");
        if (digits.isEmpty()) {
            return 0f;
        }
        try {
            return Float.parseFloat(digits);
        } catch (NumberFormatException ignore) {
            return 0f;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        releaseManagers();
    }
}
