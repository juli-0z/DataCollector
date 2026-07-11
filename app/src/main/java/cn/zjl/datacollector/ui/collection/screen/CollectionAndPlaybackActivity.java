package cn.zjl.datacollector.ui.collection.screen;

/**
 * 阅读提示：采集与回放主界面：组织工程层级浏览、采集控制、波形显示、保存、回放和诊断入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.List;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.net.wifi.DeviceWifiPermissionHelper;
import cn.zjl.datacollector.ui.common.ProjectExportCoordinator;
import cn.zjl.datacollector.ui.diagnostic.DeviceDiagnosticActivity;
import cn.zjl.datacollector.ui.collection.chart.CollectionChartController;
import cn.zjl.datacollector.ui.collection.chart.WaveformChartRenderer;
import cn.zjl.datacollector.ui.collection.panel.CollectionDialogController;
import cn.zjl.datacollector.ui.collection.panel.CollectionFormHelper;
import cn.zjl.datacollector.ui.collection.panel.CollectionUiRenderer;
import cn.zjl.datacollector.ui.collection.selection.CollectionSelectionCoordinator;
import cn.zjl.datacollector.ui.collection.selection.CollectionTreeHelper;
import cn.zjl.datacollector.ui.collection.workflow.CollectionQualityCheckResult;
import cn.zjl.datacollector.ui.log.OperationLogCenterActivity;
import cn.zjl.datacollector.ui.log.OperationLogStore;
import cn.zjl.datacollector.ui.playback.PointListAdapter;
import cn.zjl.datacollector.ui.playback.PointListItem;
import cn.zjl.datacollector.util.AppSettings;

/**
 * 数据采集与回放主界面。
 * 负责界面初始化、用户交互和 ViewModel 状态绑定。
 */
public class CollectionAndPlaybackActivity extends AppCompatActivity {
    public static final String EXTRA_READ_ONLY_HINT = "read_only_hint";

    private static final String STATE_SECONDARY_CHARTS_EXPANDED = "state_secondary_charts_expanded";
    private static final String STATE_WORKSPACE_PANEL = "state_workspace_panel";
    private static final int WORKSPACE_PANEL_BROWSER = 0;
    private static final int WORKSPACE_PANEL_CONTROL = 1;
    private static final float[] TRANSMIT_CURRENT_PRESETS = {20f, 25f, 30f};
    private static final int[] COLLECTION_COUNT_PRESETS = {1, 2, 4};
    private static final int[] SAMPLE_FREQUENCY_PRESETS = {100, 300, 500};

    private Toolbar toolbar;
    private TextView textConnectionStatus;
    private TextView textWifiStatus;
    private TextView textCollectionProgress;
    private TextView textTemperature;
    private TextView textSummary;
    private TextView textBreadcrumb;
    private TextView textMainChartTitle;
    private TextView textToggleSecondaryCharts;
    private TextView textStatusFrequency;
    private TextView textStatusPeriod;
    private TextView textStatusRepeat;
    private TextView textStatusCurrent;
    private TextView textStatusOff;
    private TextView textMonitorProtocol;
    private TextView textMonitorBattery;
    private TextView textMonitorSignal;
    private TextView textMonitorGps;
    private TextView textMonitorDataRate;
    private TextView textMonitorPacketLoss;
    private TextView textMonitorSystemStatus;
    private TextView textMonitorLastUpdate;
    private TextView textCurrentLineValue;
    private TextView textTransmitCurrentValue;
    private TextView textCollectionCountValue;
    private TextView textSampleFrequencyValue;
    private TextView textWorkspaceModeHint;
    private EditText editLineNumber;
    private EditText editPointNumber;
    private EditText editTransmitCurrent;
    private EditText editSampleFrequency;
    private EditText editCollectionCount;
    private EditText editSampleTime;
    private EditText editElectrodeDistance;

    private CombinedChart chartMain;
    private LineChart chartSend;
    private LineChart chartOff;

    private Button btnConnect;
    private Button btnStartCollection;
    private Button btnStopCollection;
    private Button btnSave;
    private Button btnNextPoint;
    private Button btnApplyPreviousParams;
    private Button btnParameterTemplates;

    private View cardConnection;
    private View cardMonitor;
    private View cardParameters;
    private View cardActions;
    private View cardPoints;
    private View cardCurrentLine;
    private MaterialCardView cardCurrentPoint;
    private View cardParamTransmitCurrent;
    private View cardParamCollectionCount;
    private View cardParamSampleFrequency;
    private View layoutSecondaryCharts;
    private MaterialButtonToggleGroup toggleWorkspaceMode;
    private RecyclerView recyclerPoints;
    private PointListAdapter pointAdapter;

    private FusedLocationProviderClient fusedLocationClient;
    private CollectionTreeHelper treeHelper;
    private WaveformChartRenderer waveformChartRenderer;
    private CollectionChartController chartController;
    private CollectionFormHelper formHelper;
    private CollectionDialogController dialogController;
    private CollectionUiRenderer uiRenderer;
    private CollectionSelectionCoordinator selectionCoordinator;
    private ProjectExportCoordinator exportCoordinator;
    private CollectionViewModel viewModel;
    private OperationLogStore operationLogStore;

    private String databaseName;
    private ProjectEntity currentProject;
    private DataRepository.ProjectTreeSummary currentTree;
    private CollectionViewModel.ActionState lastActionState = new CollectionViewModel.ActionState();
    private boolean secondaryChartsExpanded;
    private boolean readOnlyProjectHint;
    private int workspacePanelMode;
    private String pendingConnectIp;
    private int pendingConnectPort;
    private boolean pendingConnectSimulationEnabled;
    private boolean initialTreeRendered;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            });
    private final ActivityResultLauncher<String[]> deviceWifiPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (DeviceWifiPermissionHelper.hasConnectPermissions(this)) {
                    retryPendingDeviceConnect();
                    return;
                }
                clearPendingDeviceConnect();
                Toast.makeText(
                        this,
                        getString(R.string.toast_device_wifi_permissions_denied),
                        Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);

        databaseName = getIntent().getStringExtra("database_name");
        readOnlyProjectHint = getIntent().getBooleanExtra(EXTRA_READ_ONLY_HINT, false);
        secondaryChartsExpanded = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_SECONDARY_CHARTS_EXPANDED, false);
        workspacePanelMode = savedInstanceState != null
                ? savedInstanceState.getInt(
                STATE_WORKSPACE_PANEL,
                readOnlyProjectHint ? WORKSPACE_PANEL_BROWSER : WORKSPACE_PANEL_CONTROL)
                : (readOnlyProjectHint ? WORKSPACE_PANEL_BROWSER : WORKSPACE_PANEL_CONTROL);

        // 缺少工程数据库时直接退出，避免界面进入异常状态。
        if (databaseName == null || databaseName.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_missing_database), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        initHelpers();
        initCharts();
        initLocation();
        initViewModel();
        bindButtons();
        operationLogStore = new OperationLogStore(this);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        textConnectionStatus = findViewById(R.id.text_connection_status);
        textWifiStatus = findViewById(R.id.text_wifi_status);
        textCollectionProgress = findViewById(R.id.text_collection_progress);
        textTemperature = findViewById(R.id.text_temperature);
        textSummary = findViewById(R.id.text_summary);
        textBreadcrumb = findViewById(R.id.text_breadcrumb);
        textMainChartTitle = findViewById(R.id.text_main_chart_title);
        textToggleSecondaryCharts = findViewById(R.id.text_toggle_secondary_charts);
        textStatusFrequency = findViewById(R.id.text_status_frequency);
        textStatusPeriod = findViewById(R.id.text_status_period);
        textStatusRepeat = findViewById(R.id.text_status_repeat);
        textStatusCurrent = findViewById(R.id.text_status_current);
        textStatusOff = findViewById(R.id.text_status_off);
        textMonitorProtocol = findViewById(R.id.text_monitor_protocol);
        textMonitorBattery = findViewById(R.id.text_monitor_battery);
        textMonitorSignal = findViewById(R.id.text_monitor_signal);
        textMonitorGps = findViewById(R.id.text_monitor_gps);
        textMonitorDataRate = findViewById(R.id.text_monitor_data_rate);
        textMonitorPacketLoss = findViewById(R.id.text_monitor_packet_loss);
        textMonitorSystemStatus = findViewById(R.id.text_monitor_system_status);
        textMonitorLastUpdate = findViewById(R.id.text_monitor_last_update);
        textCurrentLineValue = findViewById(R.id.text_current_line_value);
        textTransmitCurrentValue = findViewById(R.id.text_transmit_current_value);
        textCollectionCountValue = findViewById(R.id.text_collection_count_value);
        textSampleFrequencyValue = findViewById(R.id.text_sample_frequency_value);
        textWorkspaceModeHint = findViewById(R.id.text_workspace_mode_hint);
        editLineNumber = findViewById(R.id.edit_line_number);
        editPointNumber = findViewById(R.id.edit_point_number);
        editTransmitCurrent = findViewById(R.id.edit_transmit_current);
        editSampleFrequency = findViewById(R.id.edit_sample_frequency);
        editCollectionCount = findViewById(R.id.edit_collection_count);
        editSampleTime = findViewById(R.id.edit_sample_time);
        editElectrodeDistance = findViewById(R.id.edit_electrode_distance);

        chartMain = findViewById(R.id.chart_main);
        chartSend = findViewById(R.id.chart_send);
        chartOff = findViewById(R.id.chart_off);

        btnConnect = findViewById(R.id.btn_connect);
        btnStartCollection = findViewById(R.id.btn_start_collection);
        btnStopCollection = findViewById(R.id.btn_stop_collection);
        btnSave = findViewById(R.id.btn_save);
        btnNextPoint = findViewById(R.id.btn_next_point);
        btnApplyPreviousParams = findViewById(R.id.btn_apply_previous_params);
        btnParameterTemplates = findViewById(R.id.btn_parameter_templates);

        cardConnection = findViewById(R.id.card_connection);
        cardMonitor = findViewById(R.id.card_monitor);
        cardParameters = findViewById(R.id.card_parameters);
        cardActions = findViewById(R.id.card_actions);
        cardPoints = findViewById(R.id.card_points);
        cardCurrentLine = findViewById(R.id.card_current_line);
        cardCurrentPoint = findViewById(R.id.card_current_point);
        cardParamTransmitCurrent = findViewById(R.id.card_param_transmit_current);
        cardParamCollectionCount = findViewById(R.id.card_param_collection_count);
        cardParamSampleFrequency = findViewById(R.id.card_param_sample_frequency);
        layoutSecondaryCharts = findViewById(R.id.layout_secondary_charts);
        toggleWorkspaceMode = findViewById(R.id.toggle_workspace_mode);

        recyclerPoints = findViewById(R.id.recycler_points);
        recyclerPoints.setLayoutManager(new LinearLayoutManager(this));
        pointAdapter = new PointListAdapter();
        pointAdapter.setOnItemClickListener(this::handleTreeItemClick);
        recyclerPoints.setAdapter(pointAdapter);

        uiRenderer = new CollectionUiRenderer(
                this,
                textConnectionStatus,
                textWifiStatus,
                textCollectionProgress,
                textTemperature,
                textBreadcrumb,
                textMainChartTitle,
                textToggleSecondaryCharts,
                textStatusFrequency,
                textStatusPeriod,
                textStatusRepeat,
                textStatusCurrent,
                textStatusOff,
                textMonitorProtocol,
                textMonitorBattery,
                textMonitorSignal,
                textMonitorGps,
                textMonitorDataRate,
                textMonitorPacketLoss,
                textMonitorSystemStatus,
                textMonitorLastUpdate,
                editPointNumber,
                btnConnect,
                btnStartCollection,
                btnStopCollection,
                btnSave,
                btnNextPoint,
                btnApplyPreviousParams,
                btnParameterTemplates,
                cardConnection,
                cardMonitor,
                cardParameters,
                cardActions,
                layoutSecondaryCharts,
                cardCurrentPoint);

        uiRenderer.renderCollectionControlsVisible(true);
        toolbar.setTitle(R.string.title_collection_and_display);
        uiRenderer.renderSecondaryCharts(secondaryChartsExpanded);
        refreshCollectionParameterCards();
        uiRenderer.renderCurrentPointHighlight(false);
        uiRenderer.renderWaveformStatus(null);
        applyWorkspacePanelMode();
    }

    /**
     * 初始化界面辅助模块，保持 Activity 只负责调度。
     */
    private void initHelpers() {
        treeHelper = new CollectionTreeHelper(this);
        waveformChartRenderer = new WaveformChartRenderer(this, chartMain, chartSend, chartOff);
        formHelper = new CollectionFormHelper(
                this,
                editLineNumber,
                editPointNumber,
                editTransmitCurrent,
                editSampleFrequency,
                editCollectionCount,
                editSampleTime,
                editElectrodeDistance,
                textCurrentLineValue,
                textTransmitCurrentValue,
                textCollectionCountValue,
                textSampleFrequencyValue);
        selectionCoordinator = new CollectionSelectionCoordinator(
                this,
                treeHelper,
                pointAdapter,
                formHelper,
                uiRenderer);
        chartController = new CollectionChartController(
                waveformChartRenderer,
                uiRenderer,
                formHelper);
        dialogController = new CollectionDialogController(
                this,
                treeHelper,
                formHelper,
                new CollectionDialogController.ActionHandler() {
                    @Override
                    public boolean isReadOnlyProject() {
                        return viewModel != null && viewModel.isReadOnlyProject();
                    }

                    @Override
                    public boolean canSavePendingResult() {
                        return viewModel.canSavePendingResult();
                    }

                    @Override
                    public CollectionQualityCheckResult evaluatePendingResult(@Nullable CollectionParameterEntity parameters) {
                        return viewModel.evaluatePendingResult(parameters);
                    }

                    @Override
                    public void saveCurrentPoint(@Nullable CollectionQualityCheckResult qualityCheckResult) {
                        CollectionAndPlaybackActivity.this.saveCurrentPoint(qualityCheckResult);
                    }

                    @Override
                    public void discardPendingResult() {
                        viewModel.discardPendingResult();
                    }

                    @Override
                    public void requestConnect(String ip, int port, boolean simulationEnabled) {
                        CollectionAndPlaybackActivity.this.requestDeviceConnect(ip, port, simulationEnabled);
                    }

                    @Override
                    public void createLine(float lineNumber, String note) {
                        viewModel.createLine(lineNumber, note);
                    }

                    @Override
                    public void selectLine(PointListItem item) {
                        viewModel.selectLine(item);
                    }

                    @Override
                    public String buildLineBreadcrumb(String lineTitle) {
                        return selectionCoordinator.buildLineBreadcrumb(lineTitle);
                    }

                    @Override
                    public EditText getTransmitCurrentField() {
                        return editTransmitCurrent;
                    }

                    @Override
                    public EditText getCollectionCountField() {
                        return editCollectionCount;
                    }

                    @Override
                    public EditText getSampleFrequencyField() {
                        return editSampleFrequency;
                    }
                    @Override
                    public void refreshCollectionParameterCards() {
                        CollectionAndPlaybackActivity.this.refreshCollectionParameterCards();
                    }
                });
        exportCoordinator = new ProjectExportCoordinator(this);
        chartController.refreshParameterCards();
    }

    private void initCharts() {
        waveformChartRenderer.initCharts();
    }

    private void initLocation() {
        // ✅ 获取 Google Play Services 提供的融合定位客户端
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // ✅ 使用 Activity Result API 异步请求权限
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
                .get(CollectionViewModel.class);

        observeViewModel();
        viewModel.initialize(databaseName, readOnlyProjectHint);
    }

    private void observeViewModel() {
        viewModel.getProjectLiveData().observe(this, project -> {
            currentProject = project;
            if (selectionCoordinator != null) {
                selectionCoordinator.setCurrentProject(project);
            }
            updateHierarchySummary();
        });

        viewModel.getTreeLiveData().observe(this, tree -> {
            currentTree = tree;
            selectionCoordinator.setCurrentTree(tree);
            pointAdapter.setData(treeHelper.buildTreeItems(tree));
            updateHierarchySummary();

            if (tree == null || tree.lines == null || tree.lines.isEmpty()) {
                uiRenderer.renderBreadcrumb(null);
                chartController.clearChartsOnly();
                initialTreeRendered = true;
                return;
            }

            if (initialTreeRendered) {
                return;
            }
            initialTreeRendered = true;
            uiRenderer.renderBreadcrumb(null);
            chartController.clearChartsOnly();
        });

        viewModel.getSelectionLiveData().observe(this, state -> {
            applySelectionState(state);
            updateHierarchySummary();
        });

        viewModel.getChartDisplayLiveData().observe(this, chartController::onChartStateChanged);

        viewModel.getMonitorLiveData().observe(this, uiRenderer::renderMonitor);
        viewModel.getWifiStateLiveData().observe(this, uiRenderer::renderWifiState);

        viewModel.getFormStateLiveData().observe(this, this::applyFormState);

        viewModel.getActionStateLiveData().observe(this, state -> {
            if (state == null) {
                return;
            }
            lastActionState = state;
            uiRenderer.renderActionState(state);
        });

        viewModel.getMessageEvent().observe(this, event -> {
            if (event == null) {
                return;
            }
            String message = event.getContentIfNotHandled();
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getSurveyLinesEvent().observe(this, event -> {
            if (event == null) {
                return;
            }
            List<SurveyLineEntity> lines = event.getContentIfNotHandled();
            if (lines != null) {
                dialogController.showLineSelectionDialog(lines);
            }
        });

    }

    private void bindButtons() {
        if (toggleWorkspaceMode != null) {
            toggleWorkspaceMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                workspacePanelMode = checkedId == R.id.button_mode_browser
                        ? WORKSPACE_PANEL_BROWSER
                        : WORKSPACE_PANEL_CONTROL;
                applyWorkspacePanelMode();
            });
        }
        btnConnect.setOnClickListener(v -> {
            if (lastActionState.connectButtonRes == R.string.action_disconnect) {
                viewModel.requestDisconnect();
            } else {
                dialogController.showTcpDialog();
            }
        });
        btnConnect.setOnLongClickListener(v -> {
            viewModel.requestSurveyLines();
            return true;
        });
        cardCurrentLine.setOnClickListener(v -> viewModel.requestSurveyLines());
        if (cardCurrentPoint != null) {
            cardCurrentPoint.setOnClickListener(v -> {
                editPointNumber.requestFocus();
                uiRenderer.renderCurrentPointCursorVisible(true);
                editPointNumber.setSelection(editPointNumber.getText() == null ? 0 : editPointNumber.getText().length());
            });
        }
        editPointNumber.setOnFocusChangeListener((v, hasFocus) -> uiRenderer.renderCurrentPointHighlight(hasFocus));
        editPointNumber.setOnEditorActionListener((v, actionId, event) -> {
            // 点号输入完成后立即失焦，避免部分机型保留竖向提示光标。
            v.clearFocus();
            uiRenderer.renderCurrentPointCursorVisible(false);
            return false;
        });
        cardParamTransmitCurrent.setOnClickListener(v -> dialogController.showTransmitCurrentDialog(TRANSMIT_CURRENT_PRESETS));
        cardParamCollectionCount.setOnClickListener(v -> dialogController.showCollectionCountDialog(COLLECTION_COUNT_PRESETS));
        cardParamSampleFrequency.setOnClickListener(v -> dialogController.showSampleFrequencyDialog(SAMPLE_FREQUENCY_PRESETS));
        btnStartCollection.setOnClickListener(v -> viewModel.startCollection(formHelper.collectParameters()));
        btnStopCollection.setOnClickListener(v -> viewModel.requestStopCollection());
        btnSave.setOnClickListener(v -> dialogController.showJudgeDialog());
        btnApplyPreviousParams.setOnClickListener(v ->
                viewModel.requestCopyPreviousParameters(formHelper.readPointNumber(Float.NaN)));
        btnParameterTemplates.setOnClickListener(v ->
                dialogController.showParameterTemplateDialog(databaseName));
        btnNextPoint.setOnClickListener(v -> selectionCoordinator.advanceToNextPoint());
        btnNextPoint.setOnLongClickListener(v -> {
            viewModel.requestCopyPreviousParameters(formHelper.readPointNumber(Float.NaN));
            return true;
        });
        textToggleSecondaryCharts.setOnClickListener(v -> toggleSecondaryCharts());
    }

    private void requestDeviceConnect(String ip, int port, boolean simulationEnabled) {
        if (simulationEnabled
                || !AppSettings.isDeviceWifiAutoConnectEnabled(this)
                || !DeviceWifiPermissionHelper.isAutoConnectSupported()) {
            clearPendingDeviceConnect();
            viewModel.requestConnect(ip, port, simulationEnabled);
            return;
        }

        String[] missingPermissions = DeviceWifiPermissionHelper.getMissingConnectPermissions(this);
        if (missingPermissions.length == 0) {
            clearPendingDeviceConnect();
            viewModel.requestConnect(ip, port, false);
            return;
        }

        pendingConnectIp = ip;
        pendingConnectPort = port;
        pendingConnectSimulationEnabled = false;
        deviceWifiPermissionLauncher.launch(missingPermissions);
    }

    private void retryPendingDeviceConnect() {
        String ip = pendingConnectIp;
        int port = pendingConnectPort;
        boolean simulationEnabled = pendingConnectSimulationEnabled;
        clearPendingDeviceConnect();
        if (ip == null || ip.trim().isEmpty()) {
            return;
        }
        viewModel.requestConnect(ip, port, simulationEnabled);
    }

    private void clearPendingDeviceConnect() {
        pendingConnectIp = null;
        pendingConnectPort = 0;
        pendingConnectSimulationEnabled = false;
    }

    private void applyWorkspacePanelMode() {
        boolean browseMode = workspacePanelMode == WORKSPACE_PANEL_BROWSER;
        if (toggleWorkspaceMode != null) {
            int checkedId = browseMode ? R.id.button_mode_browser : R.id.button_mode_collection;
            if (toggleWorkspaceMode.getCheckedButtonId() != checkedId) {
                toggleWorkspaceMode.check(checkedId);
            }
        }
        if (textWorkspaceModeHint != null) {
            textWorkspaceModeHint.setText(browseMode
                    ? R.string.collection_workspace_hint_browser
                    : R.string.collection_workspace_hint_control);
        }
        if (cardPoints != null) {
            cardPoints.setVisibility(browseMode ? View.VISIBLE : View.GONE);
        }
        if (cardConnection != null) {
            cardConnection.setVisibility(browseMode ? View.GONE : View.VISIBLE);
        }
        if (cardParameters != null) {
            cardParameters.setVisibility(browseMode ? View.GONE : View.VISIBLE);
        }
        if (cardMonitor != null) {
            cardMonitor.setVisibility(browseMode ? View.GONE : View.VISIBLE);
        }
    }

    private void updateHierarchySummary() {
        if (textSummary == null) {
            return;
        }
        if (currentTree == null) {
            textSummary.setText(R.string.hierarchy_summary_default);
            return;
        }
        int lineCount = currentTree.lines == null ? 0 : currentTree.lines.size();
        int pointCount = currentTree.pointCount;
        int completedCount = countCompletedPoints(currentTree);
        textSummary.setText(getString(
                R.string.hierarchy_summary_format,
                lineCount,
                pointCount,
                completedCount));
    }

    private int countCompletedPoints(@Nullable DataRepository.ProjectTreeSummary tree) {
        if (tree == null || tree.lines == null) {
            return 0;
        }
        int completedCount = 0;
        for (DataRepository.LineTreeSummary lineTree : tree.lines) {
            if (lineTree == null || lineTree.points == null) {
                continue;
            }
            for (DataRepository.PointTreeSummary pointTree : lineTree.points) {
                if (pointTree != null
                        && pointTree.point != null
                        && pointTree.point.getStatus() >= DataRepository.STATUS_SAVED) {
                    completedCount++;
                }
            }
        }
        return completedCount;
    }

    private void toggleSecondaryCharts() {
        secondaryChartsExpanded = !secondaryChartsExpanded;
        uiRenderer.renderSecondaryCharts(secondaryChartsExpanded);
    }

    /**
     * 处理树形列表点击，同时分发到对应的选择逻辑。
     */
    private void handleTreeItemClick(PointListItem item) {
        selectionCoordinator.handleTreeItemClick(item, new CollectionSelectionCoordinator.ActionHandler() {
            @Override
            public void selectProject(PointListItem item) {
                viewModel.selectProject(item);
            }

            @Override
            public void selectLine(PointListItem item) {
                viewModel.selectLine(item);
            }

            @Override
            public void selectPoint(PointListItem item) {
                viewModel.selectPoint(item, false);
            }

            @Override
            public void selectSession(PointListItem item) {
                viewModel.selectSession(item);
            }
        });
    }

    private void applySelectionState(@Nullable CollectionViewModel.SelectionState state) {
        selectionCoordinator.applySelectionState(state);
        chartController.onSelectionStateChanged(state);
    }

    private void applyFormState(@Nullable CollectionViewModel.FormState state) {
        formHelper.applyFormState(state);
        chartController.onFormStateApplied();
    }

    /**
     * 保存当前测点，位置由 Activity 提供，保存逻辑交给 ViewModel。
     */
    private void saveCurrentPoint(@Nullable CollectionQualityCheckResult qualityCheckResult) {
        float pointNumber = formHelper.readPointNumber(Float.NaN);
        CollectionParameterEntity parameters = formHelper.collectParameters();
        saveCurrentPointWithLocation(pointNumber, parameters, qualityCheckResult);
    }

    @SuppressLint("MissingPermission")
    private void saveCurrentPointWithLocation(float pointNumber,
                                              CollectionParameterEntity parameters,
                                              @Nullable CollectionQualityCheckResult qualityCheckResult) {
        String judgeNote = qualityCheckResult != null
                ? qualityCheckResult.buildJudgeNote(this, true)
                : "";
        if (!hasLocationPermission()) {
            viewModel.saveCurrentPoint(pointNumber, parameters, null, true, judgeNote);
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> viewModel.saveCurrentPoint(pointNumber, parameters, location, true, judgeNote))
                    .addOnFailureListener(error -> viewModel.saveCurrentPoint(pointNumber, parameters, null, true, judgeNote));
        } catch (SecurityException exception) {
            viewModel.saveCurrentPoint(pointNumber, parameters, null, true, judgeNote);
        }
    }

    private void refreshCollectionParameterCards() {
        if (chartController == null) {
            return;
        }
        chartController.refreshParameterCards();
    }

    /**
     * 统一控制测点输入框的光标显示，确保仅在编辑态出现。
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SECONDARY_CHARTS_EXPANDED, secondaryChartsExpanded);
        outState.putInt(STATE_WORKSPACE_PANEL, workspacePanelMode);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_collection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_export_database) {
            exportCurrentProject();
            return true;
        }
        if (item.getItemId() == R.id.action_quality_check_settings) {
            dialogController.showQualityCheckSettingsDialog(databaseName);
            return true;
        }
        if (item.getItemId() == R.id.action_device_diagnostic) {
            startActivity(new Intent(this, DeviceDiagnosticActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_operation_log_center) {
            startActivity(new Intent(this, OperationLogCenterActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportCurrentProject() {
        if (operationLogStore != null) {
            operationLogStore.record(
                    OperationLogStore.CATEGORY_PROJECT,
                    getString(R.string.operation_log_title_export_project_database),
                    currentProject == null
                            ? databaseName
                            : getString(
                                    R.string.operation_log_detail_project_name_database,
                                    currentProject.getName(),
                                    databaseName),
                    databaseName);
        }
        exportCoordinator.exportDatabase(exportCoordinator.resolveDatabasePath(currentProject, databaseName));
    }
}
