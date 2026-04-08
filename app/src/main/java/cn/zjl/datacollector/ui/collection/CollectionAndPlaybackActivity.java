package cn.zjl.datacollector.ui.collection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.ui.playback.PointListAdapter;
import cn.zjl.datacollector.ui.playback.PointListItem;
import cn.zjl.datacollector.util.AppSettings;
import cn.zjl.datacollector.util.ExportUtils;

/**
 * 数据采集与回放主界面。
 * 负责界面初始化、用户交互和 ViewModel 状态绑定。
 */
public class CollectionAndPlaybackActivity extends AppCompatActivity {

    public static final int COLLECTION_MODE = 0;
    public static final int PLAYBACK_MODE = 1;
    private static final String STATE_SECONDARY_CHARTS_EXPANDED = "state_secondary_charts_expanded";
    private static final float[] TRANSMIT_CURRENT_PRESETS = {20f, 25f, 30f};
    private static final int[] COLLECTION_COUNT_PRESETS = {1, 2, 4};
    private static final int[] SAMPLE_FREQUENCY_PRESETS = {100, 300, 500};

    private Toolbar toolbar;
    private TextView textConnectionStatus;
    private TextView textCollectionProgress;
    private TextView textTemperature;
    private TextView textSummary;
    private TextView textBreadcrumb;
    private TextView textToggleSecondaryCharts;
    private TextView textStatusFrequency;
    private TextView textStatusPeriod;
    private TextView textStatusRepeat;
    private TextView textStatusCurrent;
    private TextView textStatusOff;
    private TextView textCurrentLineValue;
    private TextView textTransmitCurrentValue;
    private TextView textCollectionCountValue;
    private TextView textSampleFrequencyValue;
    private EditText editLineNumber;
    private EditText editPointNumber;
    private EditText editTransmitCurrent;
    private EditText editSampleFrequency;
    private EditText editCollectionCount;
    private EditText editSampleTime;
    private EditText editElectrodeDistance;

    private LineChart chartMain;
    private LineChart chartSend;
    private LineChart chartOff;

    private Button btnConnect;
    private Button btnStartCollection;
    private Button btnStopCollection;
    private Button btnSave;
    private Button btnNextPoint;

    private View cardConnection;
    private View cardMonitor;
    private View cardParameters;
    private View cardActions;
    private View cardCurrentLine;
    private MaterialCardView cardCurrentPoint;
    private View cardParamTransmitCurrent;
    private View cardParamCollectionCount;
    private View cardParamSampleFrequency;
    private View layoutSecondaryCharts;
    private RecyclerView recyclerPoints;
    private PointListAdapter pointAdapter;

    private FusedLocationProviderClient fusedLocationClient;
    private CollectionTreeHelper treeHelper;
    private WaveformChartRenderer waveformChartRenderer;
    private CollectionViewModel viewModel;

    private String databaseName;
    private int currentMode;
    private ProjectEntity currentProject;
    private DataRepository.ProjectTreeSummary currentTree;
    private CollectionViewModel.SelectionState currentSelectionState;
    private CollectionViewModel.ChartDisplayState lastChartDisplayState = new CollectionViewModel.ChartDisplayState();
    private CollectionViewModel.ActionState lastActionState = new CollectionViewModel.ActionState();
    private boolean secondaryChartsExpanded;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);

        databaseName = getIntent().getStringExtra("database_name");
        currentMode = getIntent().getIntExtra("mode", COLLECTION_MODE);
        secondaryChartsExpanded = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_SECONDARY_CHARTS_EXPANDED, false);

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
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        textConnectionStatus = findViewById(R.id.text_connection_status);
        textCollectionProgress = findViewById(R.id.text_collection_progress);
        textTemperature = findViewById(R.id.text_temperature);
        textSummary = findViewById(R.id.text_summary);
        textBreadcrumb = findViewById(R.id.text_breadcrumb);
        textToggleSecondaryCharts = findViewById(R.id.text_toggle_secondary_charts);
        textStatusFrequency = findViewById(R.id.text_status_frequency);
        textStatusPeriod = findViewById(R.id.text_status_period);
        textStatusRepeat = findViewById(R.id.text_status_repeat);
        textStatusCurrent = findViewById(R.id.text_status_current);
        textStatusOff = findViewById(R.id.text_status_off);
        textCurrentLineValue = findViewById(R.id.text_current_line_value);
        textTransmitCurrentValue = findViewById(R.id.text_transmit_current_value);
        textCollectionCountValue = findViewById(R.id.text_collection_count_value);
        textSampleFrequencyValue = findViewById(R.id.text_sample_frequency_value);
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

        cardConnection = findViewById(R.id.card_connection);
        cardMonitor = findViewById(R.id.card_monitor);
        cardParameters = findViewById(R.id.card_parameters);
        cardActions = findViewById(R.id.card_actions);
        cardCurrentLine = findViewById(R.id.card_current_line);
        cardCurrentPoint = findViewById(R.id.card_current_point);
        cardParamTransmitCurrent = findViewById(R.id.card_param_transmit_current);
        cardParamCollectionCount = findViewById(R.id.card_param_collection_count);
        cardParamSampleFrequency = findViewById(R.id.card_param_sample_frequency);
        layoutSecondaryCharts = findViewById(R.id.layout_secondary_charts);

        recyclerPoints = findViewById(R.id.recycler_points);
        recyclerPoints.setLayoutManager(new LinearLayoutManager(this));
        pointAdapter = new PointListAdapter();
        pointAdapter.setOnItemClickListener(this::handleTreeItemClick);
        recyclerPoints.setAdapter(pointAdapter);

        boolean playback = currentMode == PLAYBACK_MODE;
        setCollectionControlsVisible(!playback);
        toolbar.setTitle(playback ? R.string.title_playback : R.string.title_collection_and_display);
        updateSecondaryChartsVisibility();
        refreshCollectionParameterCards();
        updateCurrentPointHighlight(false);
        renderWaveformStatus(null);
    }

    /**
     * 初始化界面辅助模块，保持 Activity 只负责调度。
     */
    private void initHelpers() {
        treeHelper = new CollectionTreeHelper(this);
        waveformChartRenderer = new WaveformChartRenderer(this, chartMain, chartSend, chartOff);
    }

    private void initCharts() {
        waveformChartRenderer.initCharts();
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        permissionLauncher.launch(new String[]{
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
        viewModel.initialize(databaseName, currentMode);
    }

    private void observeViewModel() {
        viewModel.getProjectLiveData().observe(this, project -> currentProject = project);

        viewModel.getTreeLiveData().observe(this, tree -> {
            currentTree = tree;
            pointAdapter.setData(treeHelper.buildTreeItems(tree));
            int pointCount = tree != null ? tree.pointCount : 0;
            textSummary.setText(getString(R.string.point_summary_format, pointCount));

            if (tree == null || tree.lines == null || tree.lines.isEmpty()) {
                updateBreadcrumb(null);
                waveformChartRenderer.renderWaveformPanels(new float[0], new float[0], new float[0], new float[0], new float[0], new float[0]);
                return;
            }

            if (viewModel.consumeInitialSelectionPending()) {
                PointListItem firstLineItem = treeHelper.findFirstLineItem(tree);
                if (firstLineItem != null) {
                    viewModel.selectLine(firstLineItem);
                }
            }
        });

        viewModel.getSelectionLiveData().observe(this, this::applySelectionState);

        viewModel.getChartDisplayLiveData().observe(this, chartState -> {
            if (chartState == null) {
                lastChartDisplayState = new CollectionViewModel.ChartDisplayState();
                waveformChartRenderer.renderWaveformPanels(new float[0], new float[0], new float[0], new float[0], new float[0], new float[0]);
                renderWaveformStatus(null);
                refreshCollectionParameterCards();
                return;
            }
            lastChartDisplayState = chartState;
            syncWaveformMetaToFields(chartState);
            if (chartState.aggregateMode) {
                waveformChartRenderer.renderAggregateWaveforms(chartState.aggregateWaveforms);
                renderWaveformStatus(chartState);
                refreshCollectionParameterCards();
                return;
            }
            WaveformSessionResolver.WaveformRenderState state = chartState.waveformState;
            waveformChartRenderer.renderWaveformPanels(
                    state.recvTimes,
                    state.recvValues,
                    state.sendTimes,
                    state.sendValues,
                    state.offTimes,
                    state.offValues);
            renderWaveformStatus(chartState);
            refreshCollectionParameterCards();
        });

        viewModel.getMonitorLiveData().observe(this, this::renderMonitor);

        viewModel.getFormStateLiveData().observe(this, this::applyFormState);

        viewModel.getActionStateLiveData().observe(this, state -> {
            if (state == null) {
                return;
            }
            lastActionState = state;
            textConnectionStatus.setText(state.connectionStatusRes);
            textCollectionProgress.setText(state.progressStatusRes);
            updateConnectionStatusAppearance(state.connectionStatusRes);
            btnConnect.setText(state.connectButtonRes);
            btnConnect.setEnabled(state.connectEnabled);
            btnStartCollection.setEnabled(state.startEnabled);
            btnStopCollection.setEnabled(state.stopEnabled);
            btnSave.setEnabled(state.saveEnabled);
            btnNextPoint.setEnabled(state.nextEnabled);
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
                showLineSelectionDialog(lines);
            }
        });

        viewModel.getPointSavedEvent().observe(this, event -> {
            if (event == null) {
                return;
            }
            Float savedPoint = event.getContentIfNotHandled();
            if (savedPoint != null) {
                advanceToNextPoint();
            }
        });
    }

    private void bindButtons() {
        btnConnect.setOnClickListener(v -> {
            if (lastActionState.connectButtonRes == R.string.action_disconnect) {
                viewModel.requestDisconnect();
            } else {
                showTcpDialog();
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
                syncCurrentPointCursorVisibility(true);
                editPointNumber.setSelection(editPointNumber.getText() == null ? 0 : editPointNumber.getText().length());
            });
        }
        editPointNumber.setOnFocusChangeListener((v, hasFocus) -> updateCurrentPointHighlight(hasFocus));
        editPointNumber.setOnEditorActionListener((v, actionId, event) -> {
            // 点号输入完成后立即失焦，避免部分机型保留竖向提示光标。
            v.clearFocus();
            syncCurrentPointCursorVisibility(false);
            return false;
        });
        cardParamTransmitCurrent.setOnClickListener(v -> showTransmitCurrentDialog());
        cardParamCollectionCount.setOnClickListener(v -> showCollectionCountDialog());
        btnStartCollection.setOnClickListener(v -> viewModel.startCollection(collectParameters()));
        btnStopCollection.setOnClickListener(v -> viewModel.requestStopCollection());
        btnSave.setOnClickListener(v -> showJudgeDialog());
        btnNextPoint.setOnClickListener(v -> advanceToNextPoint());
        btnNextPoint.setOnLongClickListener(v -> {
            viewModel.requestCopyPreviousParameters(parseFloat(editPointNumber, Float.NaN));
            return true;
        });
        textToggleSecondaryCharts.setOnClickListener(v -> toggleSecondaryCharts());
    }

    private void setCollectionControlsVisible(boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        cardConnection.setVisibility(state);
        cardMonitor.setVisibility(View.GONE);
        cardParameters.setVisibility(state);
        cardActions.setVisibility(state);
    }

    private void toggleSecondaryCharts() {
        secondaryChartsExpanded = !secondaryChartsExpanded;
        updateSecondaryChartsVisibility();
    }

    private void updateSecondaryChartsVisibility() {
        if (layoutSecondaryCharts == null || textToggleSecondaryCharts == null) {
            return;
        }
        layoutSecondaryCharts.setVisibility(secondaryChartsExpanded ? View.VISIBLE : View.GONE);
        textToggleSecondaryCharts.setText(
                secondaryChartsExpanded
                        ? R.string.action_collapse_secondary_charts
                        : R.string.action_expand_secondary_charts);
        textToggleSecondaryCharts.setSelected(secondaryChartsExpanded);
    }

    /**
     * 处理树形列表点击，同时分发到对应的选择逻辑。
     */
    private void handleTreeItemClick(PointListItem item) {
        if (item == null) {
            return;
        }

        if (item.hasChildren) {
            item.expanded = !item.expanded;
            pointAdapter.refresh();
        }

        switch (item.type) {
            case PointListItem.TYPE_PROJECT:
                viewModel.selectProject(item);
                break;
            case PointListItem.TYPE_LINE:
                viewModel.selectLine(item);
                break;
            case PointListItem.TYPE_POINT:
                viewModel.selectPoint(item, false);
                break;
            case PointListItem.TYPE_SESSION:
                viewModel.selectSession(item);
                break;
            default:
                break;
        }
    }

    private void applySelectionState(@Nullable CollectionViewModel.SelectionState state) {
        currentSelectionState = state;
        if (state == null) {
            updateBreadcrumb(null);
            refreshCollectionParameterCards();
            return;
        }

        switch (state.type) {
            case PointListItem.TYPE_PROJECT:
                pointAdapter.setSelectedProject(state.nodeId);
                break;
            case PointListItem.TYPE_LINE:
                pointAdapter.setSelectedLine(state.nodeId);
                break;
            case PointListItem.TYPE_POINT:
                pointAdapter.setSelectedPoint(state.pointId);
                break;
            case PointListItem.TYPE_SESSION:
                pointAdapter.setSelectedSession(state.pointId, state.sessionIndex);
                break;
            default:
                break;
        }
        updateLineField(state);
        updateBreadcrumb(state.breadcrumb);
        refreshCollectionParameterCards();
    }

    private void updateBreadcrumb(@Nullable String breadcrumb) {
        if (breadcrumb == null || breadcrumb.trim().isEmpty()) {
            textBreadcrumb.setText(R.string.breadcrumb_empty);
            return;
        }
        textBreadcrumb.setText(getString(R.string.breadcrumb_format, breadcrumb));
    }

    private void applyFormState(@Nullable CollectionViewModel.FormState state) {
        if (state == null) {
            return;
        }
        if (state.point != null) {
            setFieldValue(editPointNumber, formatNumber(state.point.name));
        }
        if (state.parameters == null) {
            refreshCollectionParameterCards();
            return;
        }
        setFieldValue(editTransmitCurrent, formatNumber(state.parameters.transmitCurrent));
        setFieldValue(editSampleFrequency, String.valueOf(state.parameters.sampleFrequency));
        setFieldValue(editCollectionCount, String.valueOf(state.parameters.collectionCount));
        setFieldValue(editSampleTime, formatNumber(state.parameters.sampleTime));
        setFieldValue(editElectrodeDistance, formatNumber(state.parameters.electrodeDistance));
        refreshCollectionParameterCards();
    }

    private void updateLineField(@Nullable CollectionViewModel.SelectionState state) {
        String lineTitle = resolveLineTitle(state);
        setFieldValue(editLineNumber, extractLineValue(lineTitle));
        refreshCollectionParameterCards();
    }

    @Nullable
    private String resolveLineTitle(@Nullable CollectionViewModel.SelectionState state) {
        if (state == null || currentTree == null || currentTree.lines == null) {
            return null;
        }

        if (state.type == PointListItem.TYPE_LINE) {
            for (DataRepository.LineTreeSummary lineTree : currentTree.lines) {
                if (lineTree.line.id == state.nodeId) {
                    return treeHelper.buildLineTitle(lineTree.line.name);
                }
            }
            return null;
        }

        if (state.type == PointListItem.TYPE_POINT || state.type == PointListItem.TYPE_SESSION) {
            for (DataRepository.LineTreeSummary lineTree : currentTree.lines) {
                for (DataRepository.PointTreeSummary pointTree : lineTree.points) {
                    if (pointTree.point.id == state.pointId) {
                        return treeHelper.buildLineTitle(lineTree.line.name);
                    }
                }
            }
        }

        return null;
    }

    private void renderMonitor(@Nullable DeviceMonitorEntity monitor) {
        if (monitor == null) {
            textTemperature.setText(R.string.default_degree_celsius);
            return;
        }
        textTemperature.setText(getString(R.string.monitor_temperature_value, monitor.temperature));
    }

    private void renderWaveformStatus(@Nullable CollectionViewModel.ChartDisplayState state) {
        if (state == null) {
            textStatusFrequency.setText(R.string.default_status_value);
            textStatusPeriod.setText(R.string.default_status_value);
        } else {
            textStatusFrequency.setText(formatFrequency(state.recvFs));
            textStatusPeriod.setText(formatPeriod(state.period));
        }
        textStatusRepeat.setText(R.string.default_status_value);
        textStatusCurrent.setText(R.string.default_status_value);
        textStatusOff.setText(R.string.default_status_value);
    }

    private void updateConnectionStatusAppearance(int statusRes) {
        int backgroundRes;
        int textColorRes;
        if (statusRes == R.string.connection_status_connected) {
            backgroundRes = R.drawable.bg_status_active;
            textColorRes = R.color.status_active_fg;
        } else if (statusRes == R.string.connection_status_connecting) {
            backgroundRes = R.drawable.bg_status_pending;
            textColorRes = R.color.status_pending_fg;
        } else {
            backgroundRes = R.drawable.bg_status_idle;
            textColorRes = R.color.status_idle_fg;
        }
        textConnectionStatus.setBackgroundResource(backgroundRes);
        textConnectionStatus.setTextColor(ContextCompat.getColor(this, textColorRes));
    }

    private String formatFrequency(float recvFs) {
        if (!Float.isFinite(recvFs) || recvFs <= 0f) {
            return getString(R.string.default_status_value);
        }
        return getString(R.string.summary_unit_hz, formatNumber(recvFs));
    }

    private String formatPeriod(int period) {
        return period > 0 ? String.valueOf(period) : getString(R.string.default_status_value);
    }

    private String extractLineValue(@Nullable String lineTitle) {
        if (lineTitle == null) {
            return "";
        }
        String trimmed = lineTitle.trim();
        int index = trimmed.lastIndexOf(' ');
        return index >= 0 && index < trimmed.length() - 1
                ? trimmed.substring(index + 1)
                : trimmed;
    }

    private void showJudgeDialog() {
        if (!viewModel.canSavePendingResult()) {
            Toast.makeText(this, getString(R.string.toast_no_pending_result), Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_data_judge)
                .setMessage(R.string.judge_manual_message)
                .setPositiveButton(R.string.action_save, (dialog, which) -> saveCurrentPoint())
                .setNegativeButton(R.string.action_recollect, (dialog, which) -> viewModel.discardPendingResult())
                .setNeutralButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * 保存当前测点，位置由 Activity 提供，保存逻辑交给 ViewModel。
     */
    private void saveCurrentPoint() {
        float pointNumber = parseFloat(editPointNumber, Float.NaN);
        CollectionParameterEntity parameters = collectParameters();

        if (hasLocationPermission()) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> viewModel.saveCurrentPoint(pointNumber, parameters, location, true, ""))
                    .addOnFailureListener(error -> viewModel.saveCurrentPoint(pointNumber, parameters, null, true, ""));
        } else {
            viewModel.saveCurrentPoint(pointNumber, parameters, null, true, "");
        }
    }

    private CollectionParameterEntity collectParameters() {
        CollectionParameterEntity parameters = new CollectionParameterEntity();
        parameters.transmitCurrent = parseFloat(editTransmitCurrent, 25f);
        parameters.sampleFrequency = parseInt(editSampleFrequency, 300);
        parameters.collectionCount = parseInt(editCollectionCount, 2);
        parameters.sampleTime = parseFloat(editSampleTime, 10f);
        parameters.electrodeDistance = parseFloat(editElectrodeDistance, 0f);
        parameters.transmitterDirection = "";
        parameters.customParameters = "";
        return parameters;
    }

    private void showTcpDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tcp_config, null);
        EditText editIp = dialogView.findViewById(R.id.edit_tcp_ip);
        EditText editPort = dialogView.findViewById(R.id.edit_tcp_port);
        editIp.setText(AppSettings.getTcpIp(this));
        editPort.setText(String.valueOf(AppSettings.getTcpPort(this)));

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_tcp_settings)
                .setView(dialogView)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    String ip = editIp.getText() == null ? "" : editIp.getText().toString().trim();
                    int port = parseInt(editPort, 8080);
                    viewModel.requestConnect(ip, port);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showLineSelectionDialog(List<SurveyLineEntity> lines) {
        List<SurveyLineEntity> safeLines = lines == null ? new ArrayList<>() : lines;
        String[] items = new String[safeLines.size() + 1];
        for (int i = 0; i < safeLines.size(); i++) {
            items[i] = treeHelper.buildLineTitle(safeLines.get(i).name);
        }
        items[items.length - 1] = getString(R.string.dialog_new_line_option);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_line_manage)
                .setItems(items, (dialog, which) -> {
                    if (which == safeLines.size()) {
                        showCreateLineDialog();
                        return;
                    }
                    SurveyLineEntity line = safeLines.get(which);
                    PointListItem item = PointListItem.createLine(line.id, line.name, 0);
                    item.title = treeHelper.buildLineTitle(line.name);
                    item.breadcrumb = buildLineBreadcrumb(item.title);
                    viewModel.selectLine(item);
                })
                .show();
    }

    private void showCreateLineDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_project, null);
        EditText editName = dialogView.findViewById(R.id.edit_project_name);
        EditText editDescription = dialogView.findViewById(R.id.edit_project_description);
        editName.setHint(R.string.hint_line_number);
        editDescription.setHint(R.string.hint_note);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_create_line)
                .setView(dialogView)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    float lineNumber = parseFloat(editName, Float.NaN);
                    if (!Float.isFinite(lineNumber)) {
                        Toast.makeText(this, getString(R.string.toast_invalid_line_number), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String note = editDescription.getText() == null
                            ? ""
                            : editDescription.getText().toString().trim();
                    viewModel.createLine(lineNumber, note);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private String buildLineBreadcrumb(String lineTitle) {
        String projectName = currentProject != null ? currentProject.name : "";
        return getString(
                R.string.tree_breadcrumb_format,
                projectName,
                lineTitle,
                getString(R.string.tree_all_points),
                getString(R.string.tree_all_collections));
    }

    private void advanceToNextPoint() {
        float currentPoint = parseFloat(editPointNumber, 0f);
        float step = currentProject != null && currentProject.pointNoStep > 0f ? currentProject.pointNoStep : 1f;
        setFieldValue(editPointNumber, formatNumber(currentPoint + step));
    }

    private void refreshCollectionParameterCards() {
        if (textCurrentLineValue != null) {
            String lineValue = getTextValue(editLineNumber);
            textCurrentLineValue.setText(lineValue.isEmpty()
                    ? getString(R.string.collection_unset_value)
                    : lineValue);
        }
        if (textTransmitCurrentValue != null) {
            textTransmitCurrentValue.setText(resolveSendTileValue());
        }
        if (textCollectionCountValue != null) {
            textCollectionCountValue.setText(resolvePeriodTileValue());
        }
        if (textSampleFrequencyValue != null) {
            textSampleFrequencyValue.setText(resolveRepeatTileValue());
        }
    }

    private String resolveSendTileValue() {
        if (shouldUseWaveformMeta() && lastChartDisplayState.sendFs > 0f) {
            return getString(R.string.collection_unit_hz_value, formatNumber(lastChartDisplayState.sendFs));
        }
        return buildDisplayValue(editTransmitCurrent, R.string.collection_unit_hz_value, 25f);
    }

    private String resolvePeriodTileValue() {
        if (shouldUseWaveformMeta() && lastChartDisplayState.period > 0) {
            return getString(R.string.collection_unit_period_value, String.valueOf(lastChartDisplayState.period));
        }
        return buildDisplayValue(editCollectionCount, R.string.collection_unit_period_value, 2);
    }

    private String resolveRepeatTileValue() {
        if (currentSelectionState == null) {
            return getString(R.string.collection_unset_value);
        }
        if (currentSelectionState.type != PointListItem.TYPE_POINT
                && currentSelectionState.type != PointListItem.TYPE_SESSION) {
            return getString(R.string.collection_unset_value);
        }
        if (lastChartDisplayState.repeatCount <= 0) {
            return getString(R.string.collection_unset_value);
        }
        return getString(R.string.collection_unit_repeat_value, String.valueOf(lastChartDisplayState.repeatCount));
    }

    private boolean shouldUseWaveformMeta() {
        return currentSelectionState != null
                && (currentSelectionState.type == PointListItem.TYPE_POINT
                || currentSelectionState.type == PointListItem.TYPE_SESSION);
    }

    private String buildDisplayValue(EditText field, int formatRes, float defaultValue) {
        float value = parseFloat(field, defaultValue);
        return getString(formatRes, formatNumber(value));
    }

    private String buildDisplayValue(EditText field, int formatRes, int defaultValue) {
        int value = parseInt(field, defaultValue);
        return getString(formatRes, String.valueOf(value));
    }

    private void showTransmitCurrentDialog() {
        String[] items = new String[TRANSMIT_CURRENT_PRESETS.length + 1];
        for (int i = 0; i < TRANSMIT_CURRENT_PRESETS.length; i++) {
            items[i] = getString(R.string.collection_unit_hz_value, formatNumber(TRANSMIT_CURRENT_PRESETS[i]));
        }
        items[items.length - 1] = getString(R.string.collection_option_custom);

        new AlertDialog.Builder(this)
                .setTitle(R.string.collection_set_send_frequency)
                .setItems(items, (dialog, which) -> {
                    if (which == TRANSMIT_CURRENT_PRESETS.length) {
                        showCustomValueDialog(
                                R.string.collection_set_send_frequency,
                                R.string.collection_custom_send_frequency_hint,
                                getTextValue(editTransmitCurrent),
                                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                                value -> {
                                    setFieldValue(editTransmitCurrent, value);
                                    refreshCollectionParameterCards();
                                });
                        return;
                    }
                    setFieldValue(editTransmitCurrent, formatNumber(TRANSMIT_CURRENT_PRESETS[which]));
                    refreshCollectionParameterCards();
                })
                .show();
    }

    private void showCollectionCountDialog() {
        String[] items = new String[COLLECTION_COUNT_PRESETS.length + 1];
        for (int i = 0; i < COLLECTION_COUNT_PRESETS.length; i++) {
            items[i] = getString(R.string.collection_unit_period_value, String.valueOf(COLLECTION_COUNT_PRESETS[i]));
        }
        items[items.length - 1] = getString(R.string.collection_option_custom);

        new AlertDialog.Builder(this)
                .setTitle(R.string.collection_set_period)
                .setItems(items, (dialog, which) -> {
                    if (which == COLLECTION_COUNT_PRESETS.length) {
                        showCustomValueDialog(
                                R.string.collection_set_period,
                                R.string.collection_custom_period_hint,
                                getTextValue(editCollectionCount),
                                InputType.TYPE_CLASS_NUMBER,
                                value -> {
                                    setFieldValue(editCollectionCount, value);
                                    refreshCollectionParameterCards();
                                });
                        return;
                    }
                    setFieldValue(editCollectionCount, String.valueOf(COLLECTION_COUNT_PRESETS[which]));
                    refreshCollectionParameterCards();
                })
                .show();
    }

    private void syncWaveformMetaToFields(@Nullable CollectionViewModel.ChartDisplayState chartState) {
        if (chartState == null || !shouldUseWaveformMeta()) {
            return;
        }
        if (chartState.recvFs > 0f) {
            setFieldValue(editSampleFrequency, String.valueOf(Math.round(chartState.recvFs)));
        }
        if (chartState.sendFs > 0f) {
            setFieldValue(editTransmitCurrent, formatNumber(chartState.sendFs));
        }
        if (chartState.period > 0) {
            setFieldValue(editCollectionCount, String.valueOf(chartState.period));
        }
        float sampleAxisFrequency = chartState.sampleSendFs > 0f
                ? chartState.sampleSendFs
                : chartState.sampleOffFs;
        if (sampleAxisFrequency > 0f) {
            setFieldValue(editSampleTime, formatNumber(1_000_000f / sampleAxisFrequency));
        }
    }

    private void showSampleFrequencyDialog() {
        String[] items = new String[SAMPLE_FREQUENCY_PRESETS.length + 1];
        for (int i = 0; i < SAMPLE_FREQUENCY_PRESETS.length; i++) {
            items[i] = getString(R.string.collection_unit_hz_value, String.valueOf(SAMPLE_FREQUENCY_PRESETS[i]));
        }
        items[items.length - 1] = getString(R.string.collection_option_custom);

        new AlertDialog.Builder(this)
                .setTitle(R.string.collection_set_frequency)
                .setItems(items, (dialog, which) -> {
                    if (which == SAMPLE_FREQUENCY_PRESETS.length) {
                        showCustomValueDialog(
                                R.string.collection_set_frequency,
                                R.string.collection_custom_frequency_hint,
                                getTextValue(editSampleFrequency),
                                InputType.TYPE_CLASS_NUMBER,
                                value -> {
                                    setFieldValue(editSampleFrequency, value);
                                    refreshCollectionParameterCards();
                                });
                        return;
                    }
                    setFieldValue(editSampleFrequency, String.valueOf(SAMPLE_FREQUENCY_PRESETS[which]));
                    refreshCollectionParameterCards();
                })
                .show();
    }

    private void showCustomValueDialog(
            int titleRes,
            int hintRes,
            String currentValue,
            int inputType,
            ValueCommitter committer) {
        EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setText(currentValue);
        input.setHint(hintRes);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);

        FrameLayout container = new FrameLayout(this);
        int horizontalPadding = dpToPx(20);
        int verticalPadding = dpToPx(8);
        container.setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(container)
                .setPositiveButton(R.string.collection_dialog_confirm, (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        committer.commit(value);
                    }
                })
                .setNegativeButton(R.string.collection_dialog_cancel, null)
                .show();
    }

    private void setFieldValue(@Nullable EditText editText, @Nullable String value) {
        if (editText == null) {
            return;
        }
        String safeValue = value == null ? "" : value;
        if (safeValue.equals(getTextValue(editText))) {
            return;
        }
        editText.setText(safeValue);
    }

    private String getTextValue(@Nullable EditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateCurrentPointHighlight(boolean hasFocus) {
        if (cardCurrentPoint == null) {
            return;
        }
        syncCurrentPointCursorVisibility(hasFocus);
        cardCurrentPoint.setStrokeColor(ContextCompat.getColor(
                this,
                hasFocus ? R.color.blue_primary : R.color.divider));
        cardCurrentPoint.setStrokeWidth(dpToPx(hasFocus ? 2 : 1));
    }

    /**
     * 统一控制测点输入框的光标显示，确保仅在编辑态出现。
     */
    private void syncCurrentPointCursorVisibility(boolean visible) {
        if (editPointNumber != null) {
            editPointNumber.setCursorVisible(visible);
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private int parseInt(EditText editText, int defaultValue) {
        if (editText == null || editText.getText() == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private float parseFloat(EditText editText, float defaultValue) {
        if (editText == null || editText.getText() == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(editText.getText().toString().trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private String formatNumber(float value) {
        return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SECONDARY_CHARTS_EXPANDED, secondaryChartsExpanded);
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
        return super.onOptionsItemSelected(item);
    }

    private void exportCurrentProject() {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            Toast.makeText(this, R.string.toast_missing_project_database_path, Toast.LENGTH_SHORT).show();
            return;
        }

        String databasePath = currentProject != null && currentProject.databasePath != null && !currentProject.databasePath.trim().isEmpty()
                ? currentProject.databasePath
                : getDatabasePath(databaseName).getAbsolutePath();
        File databaseFile = new File(databasePath);
        if (!databaseFile.exists()) {
            Toast.makeText(this, R.string.toast_missing_project_database_path, Toast.LENGTH_SHORT).show();
            return;
        }

        ExportUtils.exportDatabase(this, databasePath, new ExportUtils.ExportCallback() {
            @Override
            public void onSuccess(File exportedFile) {
                runOnUiThread(() -> new AlertDialog.Builder(CollectionAndPlaybackActivity.this)
                        .setTitle(R.string.dialog_export_success)
                        .setMessage(exportedFile.getAbsolutePath())
                        .setNegativeButton(R.string.action_open_export_folder, (dialog, which) -> {
                            if (!ExportUtils.openContainingDirectory(CollectionAndPlaybackActivity.this, exportedFile)) {
                                Toast.makeText(
                                        CollectionAndPlaybackActivity.this,
                                        getString(R.string.toast_open_export_folder_failed, exportedFile.getParent()),
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNeutralButton(R.string.action_share_file, (dialog, which) -> {
                            if (!ExportUtils.shareFile(CollectionAndPlaybackActivity.this, exportedFile)) {
                                Toast.makeText(CollectionAndPlaybackActivity.this, R.string.toast_share_file_failed, Toast.LENGTH_LONG).show();
                            }
                        })
                        .setPositiveButton(R.string.action_confirm, null)
                        .show());
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CollectionAndPlaybackActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private interface ValueCommitter {
        void commit(String value);
    }
}
