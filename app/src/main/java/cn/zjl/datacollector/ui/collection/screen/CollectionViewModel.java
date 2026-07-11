package cn.zjl.datacollector.ui.collection.screen;

/**
 * 阅读提示：采集界面状态模型：保存当前工程、测线、测点、参数、波形缓存和界面临时状态。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

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
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.net.tcp.TcpClientManager;
import cn.zjl.datacollector.net.wifi.DeviceWifiState;
import cn.zjl.datacollector.sync.reporter.DeviceStatusReporter;
import cn.zjl.datacollector.ui.collection.chart.CollectionChartStateFactory;
import cn.zjl.datacollector.ui.collection.chart.WaveformChartData;
import cn.zjl.datacollector.ui.collection.chart.WaveformSessionResolver;
import cn.zjl.datacollector.ui.collection.selection.CollectionSelectionUseCase;
import cn.zjl.datacollector.ui.playback.PointListItem;
import cn.zjl.datacollector.ui.collection.workflow.CollectionConnectionUseCase;
import cn.zjl.datacollector.ui.collection.workflow.CollectionQualityCheckResult;
import cn.zjl.datacollector.ui.collection.workflow.CollectionQualityChecker;
import cn.zjl.datacollector.ui.collection.workflow.CollectionSaveCoordinator;
import cn.zjl.datacollector.ui.log.OperationLogStore;
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
        public float recvFs;
        public float sendFs;
        public float sampleSendFs;
        public float sampleOffFs;
        public int period;
        public int repeatCount;
        public WaveformChartData waveformData = new WaveformChartData();
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
    private final MutableLiveData<DeviceWifiState> wifiStateLiveData = new MutableLiveData<>(DeviceWifiState.IDLE);
    private final MutableLiveData<FormState> formStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<ActionState> actionStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> messageEvent = new MutableLiveData<>();
    private final MutableLiveData<Event<List<SurveyLineEntity>>> surveyLinesEvent = new MutableLiveData<>();

    private final WaveformSessionResolver waveformSessionResolver = new WaveformSessionResolver();
    private final CollectionChartStateFactory chartStateFactory =
            new CollectionChartStateFactory(waveformSessionResolver);
    private final CollectionSaveCoordinator saveCoordinator = new CollectionSaveCoordinator();
    private final CollectionSelectionUseCase selectionUseCase =
            new CollectionSelectionUseCase(getApplication(), chartStateFactory);
    private final CollectionConnectionUseCase connectionUseCase =
            new CollectionConnectionUseCase(getApplication(), chartStateFactory);
    private final CollectionQualityChecker qualityChecker;
    private final OperationLogStore operationLogStore;
    private final DeviceStatusReporter deviceStatusReporter =
            new DeviceStatusReporter(getApplication());

    private DataRepository dataRepository;

    private String databaseName;
    private ProjectEntity project;
    private SurveyLineEntity currentLine;
    private boolean deviceConnected;
    private boolean readOnlyProject;
    private boolean readOnlyProjectNoticeShown;
    private boolean initialSelectionPending;
    private boolean initialSelectionCompleted;

    private float[] currentRecvTimeAxis = new float[0];
    private float[] currentSendTimeAxis = new float[0];
    private float[] currentOffTimeAxis = new float[0];
    private float[] currentRecv = new float[0];
    private float[] currentSend = new float[0];
    private float[] currentOff = new float[0];

    private final CollectionSelectionUseCase.Callback selectionCallback = new CollectionSelectionUseCase.Callback() {
        @Override
        public void onSelectionState(SelectionState state) {
            selectionLiveData.postValue(state);
        }

        @Override
        public void onCurrentLineChanged(SurveyLineEntity line) {
            currentLine = line;
            reportDeviceStatusIfNeeded();
        }

        @Override
        public void onFormState(FormState formState) {
            formStateLiveData.postValue(formState);
        }

        @Override
        public void onMonitorChanged(@Nullable DeviceMonitorEntity monitor) {
            monitorLiveData.postValue(monitor);
        }

        @Override
        public void onChartState(ChartDisplayState chartState) {
            chartDisplayLiveData.postValue(chartState);
        }

        @Override
        public void onSelectionFromSaveConsumed() {
            initialSelectionPending = false;
        }
    };

    private final CollectionConnectionUseCase.Callback connectionCallback =
            new CollectionConnectionUseCase.Callback() {
                @Override
                public void onWaveformResult(CollectionChartStateFactory.LiveWaveformResult result) {
                    currentRecvTimeAxis = result.recvTimeAxis;
                    currentSendTimeAxis = result.sendTimeAxis;
                    currentOffTimeAxis = result.offTimeAxis;
                    currentRecv = result.recvValues;
                    currentSend = result.sendValues;
                    currentOff = result.offValues;
                    chartDisplayLiveData.postValue(result.chartState);
                }

                @Override
                public void onMonitorChanged(@Nullable DeviceMonitorEntity monitor) {
                    monitorLiveData.postValue(monitor);
                    reportDeviceStatusIfNeeded();
                }

                @Override
                public void onConnectionStateChanged(TcpClientManager.ConnectionState state) {
                    deviceConnected = state == TcpClientManager.ConnectionState.CONNECTED;
                    if (deviceConnected) {
                        reportDeviceStatusIfNeeded();
                    }
                }

                @Override
                public void onWifiStateChanged(DeviceWifiState state) {
                    wifiStateLiveData.postValue(state);
                }

                @Override
                public void onActionState(ActionState state) {
                    actionStateLiveData.postValue(state);
                }

                @Override
                public void onMessage(String message) {
                    emitMessage(message);
                }
            };

    public CollectionViewModel(@NonNull Application application) {
        super(application);
        qualityChecker = new CollectionQualityChecker(application.getApplicationContext());
        operationLogStore = new OperationLogStore(application.getApplicationContext());
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

    public LiveData<DeviceWifiState> getWifiStateLiveData() {
        return wifiStateLiveData;
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

    public boolean isReadOnlyProject() {
        return readOnlyProject;
    }

    public void initialize(@NonNull String databaseName, boolean readOnlyHint) {
        if (databaseName.equals(this.databaseName) && dataRepository != null) {
            setReadOnlyProject(readOnlyHint || (project != null && project.getImported()));
            return;
        }

        connectionUseCase.release();

        this.databaseName = databaseName;
        this.project = null;
        this.currentLine = null;
        this.deviceConnected = false;
        this.readOnlyProject = readOnlyHint;
        this.readOnlyProjectNoticeShown = false;
        this.initialSelectionPending = false;
        this.initialSelectionCompleted = false;
        dataRepository = new DataRepository(getApplication(), databaseName);

        connectionUseCase.initialize(connectionCallback, readOnlyProject, databaseName);
        postEmptyChart();
        monitorLiveData.postValue(null);
        wifiStateLiveData.postValue(DeviceWifiState.IDLE);
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

    public void requestConnect(String ip, int port, boolean simulationEnabled) {
        connectionUseCase.requestConnect(ip, port, simulationEnabled);
    }

    public void requestDisconnect() {
        connectionUseCase.requestDisconnect();
    }

    public void startCollection(CollectionParameterEntity parameters) {
        if (readOnlyProject) {
            emitReadOnlyBlocked();
            return;
        }
        currentRecv = new float[0];
        currentSend = new float[0];
        currentOff = new float[0];
        currentRecvTimeAxis = new float[0];
        currentSendTimeAxis = new float[0];
        currentOffTimeAxis = new float[0];
        monitorLiveData.postValue(null);
        postEmptyChart();
        connectionUseCase.startCollection(currentLine, parameters);
    }

    public void requestStopCollection() {
        connectionUseCase.requestStopCollection();
    }

    public boolean canSavePendingResult() {
        return connectionUseCase.hasPendingResult();
    }

    public void discardPendingResult() {
        connectionUseCase.clearPendingResult();
        operationLogStore.record(
                OperationLogStore.CATEGORY_COLLECTION,
                getApplication().getString(R.string.operation_log_title_discard_pending_collection),
                currentLine == null
                        ? ""
                        : getApplication().getString(
                                R.string.operation_log_detail_line_only,
                                formatNumber(currentLine.getName())),
                databaseName);
        emitMessage(R.string.toast_abandon_result);
    }

    @NonNull
    public CollectionQualityCheckResult evaluatePendingResult(@Nullable CollectionParameterEntity parameters) {
        return qualityChecker.evaluate(
                currentRecv,
                currentSend,
                currentOff,
                connectionUseCase.getCurrentMonitor(),
                parameters);
    }

    public void saveCurrentPoint(float pointNumber,
                                 CollectionParameterEntity parameters,
                                 @Nullable Location location,
                                 boolean qualified,
                                 @Nullable String judgeNote) {
        if (readOnlyProject) {
            emitReadOnlyBlocked();
            return;
        }
        if (currentLine == null) {
            emitMessage(R.string.toast_line_not_ready);
            return;
        }
        if (!Float.isFinite(pointNumber)) {
            emitMessage(R.string.toast_invalid_point_number);
            return;
        }

        saveCoordinator.saveCurrentPoint(
                dataRepository,
                currentLine.getId(),
                pointNumber,
                parameters,
                currentRecvTimeAxis,
                currentRecv,
                currentSend,
                currentOff,
                connectionUseCase.getCurrentMonitor(),
                location,
                qualified,
                judgeNote,
                new DataRepository.SaveCallback<MeasurementPointEntity>() {
                    @Override
                    public void onSuccess(MeasurementPointEntity result) {
                        connectionUseCase.clearPendingResult();
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_COLLECTION,
                                getApplication().getString(R.string.operation_log_title_point_saved),
                                buildPointDetail(pointNumber, judgeNote),
                                databaseName);
                        emitMessage(R.string.toast_point_saved_continue_collection);
                        loadPointTree();
                        formStateLiveData.postValue(buildContinueCollectionFormState(pointNumber, parameters));
                    }

                    @Override
                    public void onError(String error) {
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_COLLECTION,
                                getApplication().getString(R.string.operation_log_title_point_save_failed),
                                buildPointDetail(pointNumber, error),
                                databaseName);
                        emitMessage(getApplication().getString(R.string.toast_save_failed, error));
                    }
                });
    }

    public void requestSurveyLines() {
        if (project == null || dataRepository == null) {
            return;
        }
        dataRepository.getSurveyLinesByProject(project.getId(), lines ->
                surveyLinesEvent.postValue(new Event<>(lines == null ? new ArrayList<>() : lines)));
    }

    public void createLine(float lineNumber, @Nullable String note) {
        if (readOnlyProject) {
            emitReadOnlyBlocked();
            return;
        }
        if (project == null || dataRepository == null) {
            return;
        }
        dataRepository.createSurveyLine(project.getId(), lineNumber, note == null ? "" : note, new DataRepository.SaveCallback<Long>() {
            @Override
            public void onSuccess(Long result) {
                currentLine = new SurveyLineEntity();
                currentLine.setId(result);
                currentLine.setName(lineNumber);
                operationLogStore.record(
                        OperationLogStore.CATEGORY_PROJECT,
                        getApplication().getString(R.string.operation_log_title_create_line),
                        getApplication().getString(
                                R.string.operation_log_detail_line_only,
                                formatNumber(lineNumber)) + buildOptionalSuffix(note),
                        databaseName);
                emitMessage(R.string.toast_line_created);
                loadPointTree();
            }

            @Override
            public void onError(String error) {
                operationLogStore.record(
                        OperationLogStore.CATEGORY_PROJECT,
                        getApplication().getString(R.string.operation_log_title_create_line_failed),
                        getApplication().getString(
                                R.string.operation_log_detail_line_only,
                                formatNumber(lineNumber)) + buildOptionalSuffix(error),
                        databaseName);
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
        dataRepository.getPreviousPointParameters(currentLine.getId(), currentPointNumber, parameters -> {
            if (parameters == null) {
                operationLogStore.record(
                        OperationLogStore.CATEGORY_TEMPLATE,
                        getApplication().getString(R.string.operation_log_title_apply_previous_parameters_failed),
                        getApplication().getString(
                                R.string.operation_log_detail_no_previous_parameters,
                                formatNumber(currentPointNumber)),
                        databaseName);
                emitMessage(R.string.toast_previous_point_no_params);
                return;
            }
            FormState formState = new FormState();
            formState.parameters = parameters;
            formStateLiveData.postValue(formState);
            operationLogStore.record(
                    OperationLogStore.CATEGORY_TEMPLATE,
                    getApplication().getString(R.string.operation_log_title_apply_previous_parameters),
                    getApplication().getString(
                            R.string.operation_log_detail_point_only,
                            formatNumber(currentPointNumber))
                            + " · "
                            + buildParameterSummary(parameters),
                    databaseName);
            emitMessage(R.string.toast_previous_point_params_applied);
        });
    }

    public void selectProject(PointListItem item) {
        initialSelectionCompleted = true;
        if (dataRepository == null) {
            return;
        }
        selectionUseCase.selectProject(dataRepository, item, selectionCallback);
    }

    public void selectLine(PointListItem item) {
        initialSelectionCompleted = true;
        selectionUseCase.selectLine(dataRepository, item, selectionCallback);
    }

    public void selectPoint(PointListItem item, boolean fromSave) {
        initialSelectionCompleted = true;
        selectionUseCase.selectPoint(dataRepository, item, fromSave, selectionCallback);
    }

    public void selectSession(PointListItem item) {
        initialSelectionCompleted = true;
        selectionUseCase.selectSession(dataRepository, item, selectionCallback);
    }

    private void loadProjectContext() {
        dataRepository.getProject(projectResult -> {
            project = projectResult;
            projectLiveData.postValue(projectResult);
            setReadOnlyProject((projectResult != null && projectResult.getImported()) || readOnlyProject);
            reportDeviceStatusIfNeeded();
            if (projectResult == null) {
                loadPointTree();
                return;
            }

            if (readOnlyProject && !readOnlyProjectNoticeShown) {
                readOnlyProjectNoticeShown = true;
                emitMessage(R.string.toast_project_opened_read_only);
            }

            if (readOnlyProject) {
                loadPointTree();
                return;
            }

            dataRepository.ensureDefaultSurveyLine(projectResult.getId(), new DataRepository.SaveCallback<SurveyLineEntity>() {
                @Override
                public void onSuccess(SurveyLineEntity result) {
                    currentLine = result;
                    reportDeviceStatusIfNeeded();
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

    private void postEmptyChart() {
        chartDisplayLiveData.postValue(new ChartDisplayState());
    }

    @NonNull
    private FormState buildContinueCollectionFormState(float savedPointNumber,
                                                       @Nullable CollectionParameterEntity parameters) {
        FormState formState = new FormState();
        MeasurementPointEntity nextPoint = new MeasurementPointEntity();
        nextPoint.setName(savedPointNumber + resolvePointStep());
        formState.point = nextPoint;
        formState.parameters = copyParameters(parameters);
        return formState;
    }

    private float resolvePointStep() {
        return project != null && project.getPointNoStep() > 0f ? project.getPointNoStep() : 1f;
    }

    @Nullable
    private CollectionParameterEntity copyParameters(@Nullable CollectionParameterEntity source) {
        if (source == null) {
            return null;
        }
        CollectionParameterEntity target = new CollectionParameterEntity();
        target.setTransmitCurrent(source.getTransmitCurrent());
        target.setSampleFrequency(source.getSampleFrequency());
        target.setCollectionCount(source.getCollectionCount());
        target.setSampleTime(source.getSampleTime());
        target.setElectrodeDistance(source.getElectrodeDistance());
        target.setTransmitterDirection(source.getTransmitterDirection());
        target.setCustomParameters(source.getCustomParameters());
        return target;
    }

    private void setReadOnlyProject(boolean readOnlyProject) {
        this.readOnlyProject = readOnlyProject;
        connectionUseCase.setReadOnlyProject(readOnlyProject);
    }

    private void emitReadOnlyBlocked() {
        emitMessage(R.string.toast_project_read_only_blocked);
    }

    private void emitMessage(int resId) {
        emitMessage(getApplication().getString(resId));
    }

    private void emitMessage(String message) {
        messageEvent.postValue(new Event<>(message));
    }

    private void reportDeviceStatusIfNeeded() {
        if (!deviceConnected || readOnlyProject || AppSettings.isTcpSimulationEnabled(getApplication())) {
            return;
        }
        deviceStatusReporter.reportOnlineIfPossible(
                project,
                currentLine,
                connectionUseCase.getCurrentMonitor());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        connectionUseCase.release();
        deviceStatusReporter.shutdown();
    }

    private String buildPointDetail(float pointNumber, @Nullable String extra) {
        StringBuilder builder = new StringBuilder();
        if (currentLine != null) {
            builder.append(getApplication().getString(
                    R.string.operation_log_detail_line_only,
                    formatNumber(currentLine.getName()))).append(" · ");
        }
        builder.append(getApplication().getString(
                R.string.operation_log_detail_point_only,
                formatNumber(pointNumber)));
        String extraText = sanitizeDetail(extra);
        if (!extraText.isEmpty()) {
            builder.append(" · ").append(extraText);
        }
        return builder.toString();
    }

    private String buildParameterSummary(@Nullable CollectionParameterEntity parameters) {
        if (parameters == null) {
            return "";
        }
        return getApplication().getString(
                R.string.operation_log_detail_parameter_summary,
                formatNumber(parameters.getTransmitCurrent()),
                parameters.getSampleFrequency(),
                parameters.getCollectionCount(),
                formatNumber(parameters.getSampleTime()));
    }

    private String buildOptionalSuffix(@Nullable String text) {
        String safe = sanitizeDetail(text);
        return safe.isEmpty() ? "" : " · " + safe;
    }

    private String sanitizeDetail(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        while (normalized.contains("  ")) {
            normalized = normalized.replace("  ", " ");
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String formatNumber(float value) {
        return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
    }
}
