package cn.zjl.datacollector.ui.upload;

/**
 * 阅读提示：数据上传界面：支持选择工程文件、登录配置、上传未同步点、失败点筛选与重试。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.data.repository.ProjectRepository;
import cn.zjl.datacollector.databinding.ActivityDataUploadBinding;
import cn.zjl.datacollector.sync.executor.ProjectSyncExecutor;
import cn.zjl.datacollector.sync.auth.SyncAuthManager;
import cn.zjl.datacollector.ui.log.OperationLogCenterActivity;
import cn.zjl.datacollector.ui.log.OperationLogStore;
import cn.zjl.datacollector.util.AppSettings;

/**
 * 数据上传任务中心页面。
 *
 * <p>该页面负责展示可上传工程、维护登录配置、触发手动上传、筛选失败测点、
 * 展示测点级上传进度和保存最近上传任务记录。实际上传动作由 ProjectSyncExecutor 执行，
 * Activity 只负责界面状态组织和用户交互。</p>
 */
public class DataUploadActivity extends AppCompatActivity {

    /**
     * 上传页面中的单个工程条目状态。
     *
     * <p>该对象是界面层模型，不直接写回数据库；其中的计数信息来自工程库中的测点同步状态。</p>
     */
    private static final class UploadProjectItem {
        ProjectEntity project;
        int uploadablePointCount;
        int unsyncedCount;
        int failedPointCount;
        boolean selected;
        boolean busy;
        String latestError = "";
        String statusMessage = "";
    }

    /**
     * 当前正在执行的上传任务状态。
     *
     * <p>上传进度按“已处理测点数 / 待处理测点总数”统计，同时记录当前工程、
     * 当前测点、成功数和失败数，用于刷新页面顶部的整体进度卡片。</p>
     */
    private static final class ActiveUploadSession {
        String taskId = "";
        long startedAt;
        int totalProjects;
        int totalPendingPoints;
        int completedProjects;
        int currentProjectIndex;
        int completedProcessedBeforeCurrentProject;
        int completedSyncedBeforeCurrentProject;
        int completedFailedBeforeCurrentProject;
        int processedPoints;
        int syncedPoints;
        int failedPoints;
        int failedProjects;
        String currentProjectName = "";
        String currentPointName = "";
    }

    private final List<UploadProjectItem> uploadItems = new ArrayList<>();
    private final List<UploadProjectItem> visibleItems = new ArrayList<>();
    private final List<UploadTaskCenterStore.TaskRecord> taskHistory = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private ActivityDataUploadBinding binding;
    private ProjectRepository projectRepository;
    private UploadTaskCenterStore taskStore;
    private UploadProjectAdapter projectAdapter;
    private TaskHistoryAdapter historyAdapter;
    private OperationLogStore operationLogStore;

    private boolean loading;
    private boolean uploadInProgress;
    private boolean refreshingToken;
    private boolean failedOnlyFilter;
    private ActiveUploadSession activeUploadSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDataUploadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        applyToolbarInsets();

        projectRepository = new ProjectRepository(this);
        taskStore = new UploadTaskCenterStore(this);
        projectAdapter = new UploadProjectAdapter();
        historyAdapter = new TaskHistoryAdapter();
        operationLogStore = new OperationLogStore(this);

        binding.recyclerProjects.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerProjects.setNestedScrollingEnabled(false);
        binding.recyclerProjects.setAdapter(projectAdapter);

        binding.recyclerTaskHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTaskHistory.setNestedScrollingEnabled(false);
        binding.recyclerTaskHistory.setAdapter(historyAdapter);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.buttonRefresh.setOnClickListener(v -> loadProjectItems());
        binding.buttonSelectRegular.setOnClickListener(v -> selectRegularProjects());
        binding.buttonSelectFailed.setOnClickListener(v -> selectFailedProjects());
        binding.buttonClearSelection.setOnClickListener(v -> clearSelection());
        binding.buttonRefreshToken.setOnClickListener(v -> refreshToken());
        binding.buttonClearTaskHistory.setOnClickListener(v -> confirmAndClearTaskHistory());
        binding.buttonUploadSelected.setOnClickListener(v -> confirmAndUploadSelectedProjects());
        binding.buttonRetryFailedSelected.setOnClickListener(v -> confirmAndRetryFailedPoints());
        binding.chipFilterAll.setOnClickListener(v -> setFailedOnlyFilter(false));
        binding.chipFilterFailedOnly.setOnClickListener(v -> setFailedOnlyFilter(true));
        binding.editSyncUsername.setText(AppSettings.getSyncUsername(this));
        binding.editSyncPassword.setText(AppSettings.getSyncPassword(this));
        updateFilterChips();

        loadTaskHistory();
        refreshSelectionSummary();
        refreshConfigSummary();
        updateActiveTaskCard();
        updateActionState();
        loadProjectItems();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_data_upload, menu);
        return true;
    }

    private void applyToolbarInsets() {
        final int baseToolbarHeight = (int) (60f * getResources().getDisplayMetrics().density);
        final int baseToolbarPaddingTop = binding.toolbar.getPaddingTop();
        final int baseAppBarPaddingTop = binding.appBarLayout.getPaddingTop();

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            int topInset = systemBars.top;

            ViewGroup.LayoutParams layoutParams = binding.toolbar.getLayoutParams();
            layoutParams.height = baseToolbarHeight + topInset;
            binding.toolbar.setLayoutParams(layoutParams);
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    baseToolbarPaddingTop + topInset,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            binding.appBarLayout.setPadding(
                    binding.appBarLayout.getPaddingLeft(),
                    baseAppBarPaddingTop,
                    binding.appBarLayout.getPaddingRight(),
                    binding.appBarLayout.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.appBarLayout);
    }

    private void loadTaskHistory() {
        taskHistory.clear();
        taskHistory.addAll(taskStore.loadHistory());
        historyAdapter.notifyDataSetChanged();
        binding.textHistoryEmptyState.setVisibility(taskHistory.isEmpty() ? View.VISIBLE : View.GONE);
        binding.recyclerTaskHistory.setVisibility(taskHistory.isEmpty() ? View.GONE : View.VISIBLE);
        updateActiveTaskCard();
        updateActionState();
    }

    private void loadProjectItems() {
        loading = true;
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.textEmptyState.setVisibility(View.GONE);
        binding.recyclerProjects.setVisibility(View.GONE);
        updateActionState();

        projectRepository.getAllProjects(projects -> {
            List<UploadProjectItem> items = new ArrayList<>();
            if (projects != null) {
                for (ProjectEntity project : projects) {
                    if (project == null) {
                        continue;
                    }
                    UploadProjectItem item = new UploadProjectItem();
                    item.project = project;
                    populateProjectState(item);
                    items.add(item);
                }
            }
            runOnUiThread(() -> {
                loading = false;
                binding.progressLoading.setVisibility(View.GONE);
                uploadItems.clear();
                uploadItems.addAll(items);
                rebuildVisibleItems();
                refreshProjectListVisibility();
                refreshSelectionSummary();
                refreshConfigSummary();
                updateActionState();
            });
        });
    }

    private void populateProjectState(UploadProjectItem item) {
        if (item.project == null || item.project.getDatabaseName() == null
                || item.project.getDatabaseName().trim().isEmpty()) {
            item.latestError = getString(R.string.data_upload_status_missing_database);
            item.statusMessage = item.latestError;
            item.unsyncedCount = 0;
            return;
        }
        // 工程列表展示的上传状态实时来自对应工程数据库，避免只依赖上一次页面缓存。
        DataRepository repository = new DataRepository(this, item.project.getDatabaseName());
        List<MeasurementPointEntity> uploadablePoints = repository.getUploadablePointsSync();
        List<MeasurementPointEntity> unsyncedPoints = repository.getUnsyncedPointsSync();
        List<MeasurementPointEntity> failedPoints = repository.getFailedUnsyncedPointsSync();
        item.uploadablePointCount = uploadablePoints == null ? 0 : uploadablePoints.size();
        item.unsyncedCount = unsyncedPoints == null ? 0 : unsyncedPoints.size();
        item.failedPointCount = failedPoints == null ? 0 : failedPoints.size();
        item.latestError = findLatestSyncError(item.failedPointCount > 0 ? failedPoints : unsyncedPoints);
        item.statusMessage = buildIdleStatus(item);
    }

    private String findLatestSyncError(List<MeasurementPointEntity> unsyncedPoints) {
        if (unsyncedPoints == null) {
            return "";
        }
        for (MeasurementPointEntity point : unsyncedPoints) {
            if (point != null && !TextUtils.isEmpty(point.getSyncError())) {
                return point.getSyncError().trim();
            }
        }
        return "";
    }

    private void selectRegularProjects() {
        if (loading || uploadInProgress) {
            return;
        }
        for (UploadProjectItem item : uploadItems) {
            item.selected = false;
        }
        for (UploadProjectItem item : visibleItems) {
            item.selected = item.project != null && !item.project.getImported() && item.uploadablePointCount > 0;
        }
        projectAdapter.notifyDataSetChanged();
        refreshSelectionSummary();
        updateActionState();
    }

    private void selectFailedProjects() {
        if (loading || uploadInProgress) {
            return;
        }
        for (UploadProjectItem item : uploadItems) {
            item.selected = false;
        }
        for (UploadProjectItem item : visibleItems) {
            item.selected = item.project != null && !item.project.getImported() && item.failedPointCount > 0;
        }
        projectAdapter.notifyDataSetChanged();
        refreshSelectionSummary();
        updateActionState();
    }

    private void setFailedOnlyFilter(boolean failedOnly) {
        if (failedOnlyFilter == failedOnly) {
            updateFilterChips();
            return;
        }
        failedOnlyFilter = failedOnly;
        for (UploadProjectItem item : uploadItems) {
            item.selected = false;
        }
        rebuildVisibleItems();
        refreshProjectListVisibility();
        refreshSelectionSummary();
        updateActionState();
    }

    private void clearSelection() {
        if (loading || uploadInProgress) {
            return;
        }
        for (UploadProjectItem item : uploadItems) {
            item.selected = false;
        }
        projectAdapter.notifyDataSetChanged();
        refreshSelectionSummary();
        updateActionState();
    }

    private void refreshToken() {
        if (refreshingToken || uploadInProgress) {
            return;
        }
        // 先保存界面输入，再执行登录，保证用户刚修改的账号、密码和地址立即生效。
        saveCredentialInputs();
        refreshingToken = true;
        updateActionState();

        SyncAuthManager authManager = new SyncAuthManager(this);
        authManager.loginAsync(result -> runOnUiThread(() -> {
            refreshingToken = false;
            refreshConfigSummary();
            updateActionState();
            operationLogStore.record(
                    OperationLogStore.CATEGORY_UPLOAD,
                    getString(result.success
                            ? R.string.operation_log_title_refresh_upload_token_succeeded
                            : R.string.operation_log_title_refresh_upload_token_failed),
                    safeTextOrDefault(result.message, ""),
                    "");
            if (result.success) {
                Toast.makeText(this, R.string.toast_sync_login_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void confirmAndClearTaskHistory() {
        if (uploadInProgress || taskHistory.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.data_upload_task_clear_history_confirm_title)
                .setMessage(R.string.data_upload_task_clear_history_confirm_message)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    taskStore.clearHistory();
                    loadTaskHistory();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmAndUploadSelectedProjects() {
        if (loading || uploadInProgress) {
            return;
        }
        saveCredentialInputs();
        List<UploadProjectItem> selectedItems = getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, R.string.toast_upload_select_project, Toast.LENGTH_SHORT).show();
            return;
        }
        if (containsImportedProject(selectedItems)) {
            // 导入工程可能是历史库或只读库，上传前需要二次确认，避免误把历史数据写入后端。
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_data_upload_imported_title)
                    .setMessage(R.string.dialog_data_upload_imported_message)
                    .setPositiveButton(R.string.action_confirm,
                            (dialog, which) -> startUpload(selectedItems, false))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }
        startUpload(selectedItems, false);
    }

    private void confirmAndRetryFailedPoints() {
        if (loading || uploadInProgress) {
            return;
        }
        saveCredentialInputs();
        // 失败点重试只允许选择仍存在失败测点的工程，避免重复处理已经成功的测点。
        List<UploadProjectItem> selectedItems = getSelectedFailedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, R.string.toast_upload_no_failed_points_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_data_upload_retry_failed_title)
                .setMessage(R.string.dialog_data_upload_retry_failed_message)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    if (containsImportedProject(selectedItems)) {
                        confirmImportedAndStartUpload(selectedItems, true);
                    } else {
                        startUpload(selectedItems, true);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private List<UploadProjectItem> getSelectedItems() {
        List<UploadProjectItem> selectedItems = new ArrayList<>();
        for (UploadProjectItem item : visibleItems) {
            if (item.selected) {
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    private List<UploadProjectItem> getSelectedFailedItems() {
        List<UploadProjectItem> selectedItems = new ArrayList<>();
        for (UploadProjectItem item : visibleItems) {
            if (item.selected && item.failedPointCount > 0) {
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    private boolean containsImportedProject(List<UploadProjectItem> selectedItems) {
        for (UploadProjectItem item : selectedItems) {
            if (item.project != null && item.project.getImported()) {
                return true;
            }
        }
        return false;
    }

    private void confirmImportedAndStartUpload(List<UploadProjectItem> selectedItems, boolean failedOnly) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_data_upload_imported_title)
                .setMessage(R.string.dialog_data_upload_imported_message)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> startUpload(selectedItems, failedOnly))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void startUpload(List<UploadProjectItem> selectedItems, boolean failedOnly) {
        List<UploadProjectItem> queuedItems = new ArrayList<>(selectedItems);
        uploadInProgress = true;
        activeUploadSession = createActiveSession(queuedItems, failedOnly);
        operationLogStore.record(
                OperationLogStore.CATEGORY_UPLOAD,
                getString(failedOnly
                        ? R.string.operation_log_title_retry_failed_points_task
                        : R.string.operation_log_title_start_manual_upload_task),
                getString(
                        failedOnly
                                ? R.string.operation_log_detail_retry_failed_points_task
                                : R.string.operation_log_detail_manual_upload_task,
                        queuedItems.size(),
                        activeUploadSession.totalPendingPoints),
                "");
        updateActiveTaskCard();
        updateActionState();

        // 上传过程包含数据库读取、JSON 组装和网络请求，必须放到后台线程，避免阻塞主界面。
        executorService.execute(() -> {
            ProjectSyncExecutor syncExecutor = new ProjectSyncExecutor(this);
            ProjectSyncExecutor.SyncRunResult runResult = new ProjectSyncExecutor.SyncRunResult();
            for (int i = 0; i < queuedItems.size(); i++) {
                UploadProjectItem selectedItem = queuedItems.get(i);
                String databaseName = selectedItem.project.getDatabaseName();
                String projectName = resolveProjectName(selectedItem.project);
                int projectIndex = i + 1;
                runOnUiThread(() -> markItemUploading(databaseName, projectName, projectIndex, queuedItems.size()));

                // 普通上传允许重复上传用于联调；失败重试只处理同步失败且未成功的测点。
                ProjectSyncExecutor.ProjectSyncResult result =
                        syncExecutor.syncProject(
                                selectedItem.project,
                                true,
                                failedOnly,
                                !failedOnly,
                                projectIndex,
                                queuedItems.size(),
                                event -> runOnUiThread(() -> applyPointProgress(event)));
                runResult.projectResults.add(result);
                runResult.totalSyncedCount += result.syncedCount;
                runResult.hasRetryableFailure = runResult.hasRetryableFailure || result.retryableFailure;
                runResult.hasFatalFailure = runResult.hasFatalFailure || result.fatalFailure;

                // 每个工程完成后立即刷新列表，用户可以看到该工程的剩余失败点和最新错误。
                runOnUiThread(() -> applyUploadResult(databaseName, result));
            }
            runOnUiThread(() -> finishUpload(runResult));
        });
    }

    private ActiveUploadSession createActiveSession(List<UploadProjectItem> selectedItems, boolean failedOnly) {
        ActiveUploadSession session = new ActiveUploadSession();
        session.taskId = UUID.randomUUID().toString();
        session.startedAt = System.currentTimeMillis();
        session.totalProjects = selectedItems.size();
        for (UploadProjectItem item : selectedItems) {
            // 普通上传统计所有可上传测点；失败重试只统计失败测点，保证进度条含义一致。
            session.totalPendingPoints += failedOnly ? item.failedPointCount : item.uploadablePointCount;
        }
        return session;
    }

    private void markItemUploading(String databaseName, String projectName, int progress, int total) {
        UploadProjectItem item = findItem(databaseName);
        if (item == null) {
            return;
        }
        item.busy = true;
        item.statusMessage = getString(R.string.data_upload_status_uploading_progress, progress, total);
        if (activeUploadSession != null) {
            activeUploadSession.currentProjectName = projectName;
            activeUploadSession.currentProjectIndex = progress;
            activeUploadSession.currentPointName = "";
        }
        projectAdapter.notifyDataSetChanged();
        refreshSelectionSummary();
        updateActiveTaskCard();
    }

    private void applyPointProgress(ProjectSyncExecutor.ProgressEvent event) {
        if (activeUploadSession == null || event == null) {
            return;
        }
        // ProjectSyncExecutor 回调的是当前工程内的进度，这里叠加之前已完成工程的数量，
        // 转换为整个上传任务的测点级总进度。
        activeUploadSession.currentProjectName = resolveProjectName(event.project);
        activeUploadSession.currentPointName = resolvePointName(event.point);
        activeUploadSession.currentProjectIndex = event.projectIndex;
        activeUploadSession.processedPoints = Math.min(
                activeUploadSession.totalPendingPoints,
                Math.max(0, activeUploadSession.completedProcessedBeforeCurrentProject
                        + event.processedPointCount));
        activeUploadSession.syncedPoints = activeUploadSession.completedSyncedBeforeCurrentProject
                + Math.max(0, event.syncedPointCount);
        activeUploadSession.failedPoints = activeUploadSession.completedFailedBeforeCurrentProject
                + Math.max(0, event.failedPointCount);

        updateActiveTaskCard();
    }

    private void applyUploadResult(String databaseName, ProjectSyncExecutor.ProjectSyncResult result) {
        UploadProjectItem item = findItem(databaseName);
        if (item == null) {
            return;
        }
        item.busy = false;
        item.unsyncedCount = Math.max(0, result.remainingUnsyncedCount);
        item.failedPointCount = Math.max(0, result.remainingFailedPointCount);
        item.latestError = result.success || item.failedPointCount <= 0 ? "" : safeText(result.message);
        item.statusMessage = safeText(result.message);
        // 上传失败且仍有失败点时保留选中状态，方便用户直接点击失败点重试。
        item.selected = !result.success && item.failedPointCount > 0;
        if (result.syncedAt > 0L) {
            item.project.setLastSyncedAt(result.syncedAt);
            item.project.setUpdatedAt(result.syncedAt);
        }

        if (activeUploadSession != null) {
            // 工程完成后，把当前工程的处理数量固化为“之前已完成数量”，供下一个工程进度叠加。
            activeUploadSession.completedProjects =
                    Math.min(activeUploadSession.totalProjects, activeUploadSession.completedProjects + 1);
            int projectProcessedCount = Math.max(0, result.processedPointCount);
            activeUploadSession.processedPoints = Math.min(
                    activeUploadSession.totalPendingPoints,
                    activeUploadSession.completedProcessedBeforeCurrentProject + projectProcessedCount);
            activeUploadSession.completedProcessedBeforeCurrentProject = activeUploadSession.processedPoints;
            activeUploadSession.syncedPoints = activeUploadSession.completedSyncedBeforeCurrentProject
                    + result.syncedCount;
            activeUploadSession.failedPoints = activeUploadSession.completedFailedBeforeCurrentProject
                    + result.failedPointCount;
            activeUploadSession.completedSyncedBeforeCurrentProject = activeUploadSession.syncedPoints;
            activeUploadSession.completedFailedBeforeCurrentProject = activeUploadSession.failedPoints;
            if (!result.success) {
                activeUploadSession.failedProjects++;
            }
        }

        operationLogStore.record(
                OperationLogStore.CATEGORY_UPLOAD,
                getString(result.success
                        ? R.string.operation_log_title_project_upload_completed
                        : R.string.operation_log_title_project_upload_failed),
                getString(
                        R.string.operation_log_detail_project_upload_result,
                        resolveProjectName(item.project),
                        result.syncedCount,
                        result.pendingPointCount,
                        safeTextOrDefault(result.message,
                                getString(R.string.data_upload_status_failed_generic))),
                databaseName);

        rebuildVisibleItems();
        refreshProjectListVisibility();
        refreshSelectionSummary();
        refreshConfigSummary();
        updateActionState();
        updateActiveTaskCard();
    }

    private void finishUpload(ProjectSyncExecutor.SyncRunResult runResult) {
        uploadInProgress = false;

        // 任务历史只保存摘要，便于页面展示；每个测点的真实同步状态由同步执行器写入工程数据库。
        UploadTaskCenterStore.TaskRecord record = buildTaskRecord(activeUploadSession, runResult);
        if (record != null) {
            taskStore.saveRecord(record);
            operationLogStore.record(
                    OperationLogStore.CATEGORY_UPLOAD,
                    getString(R.string.operation_log_title_upload_task_finished),
                    safeTextOrDefault(record.summary, ""),
                    "");
        }
        activeUploadSession = null;

        // 结束时把未被结果覆盖的工程恢复为空闲状态，避免页面残留“上传中”提示。
        for (UploadProjectItem item : uploadItems) {
            if (!item.busy && TextUtils.isEmpty(item.statusMessage)) {
                item.statusMessage = buildIdleStatus(item);
            }
        }

        rebuildVisibleItems();
        refreshProjectListVisibility();
        refreshSelectionSummary();
        refreshConfigSummary();
        loadTaskHistory();
        updateActionState();
        showUploadResultDialog(runResult);
    }

    private UploadTaskCenterStore.TaskRecord buildTaskRecord(ActiveUploadSession session,
                                                             ProjectSyncExecutor.SyncRunResult runResult) {
        if (session == null) {
            return null;
        }
        UploadTaskCenterStore.TaskRecord record = new UploadTaskCenterStore.TaskRecord();
        record.taskId = session.taskId;
        record.startedAt = session.startedAt;
        record.finishedAt = System.currentTimeMillis();
        record.projectCount = session.totalProjects;
        record.totalPendingPoints = session.totalPendingPoints;
        record.syncedPoints = runResult.totalSyncedCount;
        record.status = resolveTaskStatus(runResult);
        record.summary = buildRunSummary(session.totalPendingPoints, runResult);

        // 按工程记录本次任务结果，供用户点击历史记录时查看每个工程的上传摘要。
        for (ProjectSyncExecutor.ProjectSyncResult result : runResult.projectResults) {
            UploadTaskCenterStore.ProjectRecord projectRecord = new UploadTaskCenterStore.ProjectRecord();
            projectRecord.projectName = resolveProjectName(result.project);
            projectRecord.databaseName = result.project == null ? "" : safeText(result.project.getDatabaseName());
            projectRecord.pendingPointCount = result.pendingPointCount;
            projectRecord.syncedCount = result.syncedCount;
            projectRecord.success = result.success;
            projectRecord.message = safeText(result.message);
            record.projectRecords.add(projectRecord);
        }
        return record;
    }

    private String resolveTaskStatus(ProjectSyncExecutor.SyncRunResult runResult) {
        if (!runResult.hasRetryableFailure && !runResult.hasFatalFailure) {
            return UploadTaskCenterStore.STATUS_SUCCESS;
        }
        if (runResult.totalSyncedCount > 0) {
            return UploadTaskCenterStore.STATUS_PARTIAL;
        }
        return UploadTaskCenterStore.STATUS_FAILED;
    }

    private String buildRunSummary(int totalPendingPoints, ProjectSyncExecutor.SyncRunResult runResult) {
        if (!runResult.hasRetryableFailure && !runResult.hasFatalFailure) {
            return getString(R.string.data_upload_status_success, runResult.totalSyncedCount);
        }

        String firstError = findFirstProjectMessage(runResult.projectResults, false);
        if (runResult.totalSyncedCount > 0) {
            return getString(
                    R.string.data_upload_status_partial,
                    runResult.totalSyncedCount,
                    totalPendingPoints,
                    safeTextOrDefault(firstError, getString(R.string.data_upload_status_failed_generic)));
        }
        return safeTextOrDefault(firstError, getString(R.string.data_upload_status_failed_generic));
    }

    private String findFirstProjectMessage(List<ProjectSyncExecutor.ProjectSyncResult> projectResults,
                                           boolean successOnly) {
        for (ProjectSyncExecutor.ProjectSyncResult result : projectResults) {
            if (result == null) {
                continue;
            }
            if (successOnly && !result.success) {
                continue;
            }
            if (!successOnly && result.success) {
                continue;
            }
            if (!TextUtils.isEmpty(result.message)) {
                return result.message.trim();
            }
        }
        return "";
    }

    private void showUploadResultDialog(ProjectSyncExecutor.SyncRunResult runResult) {
        StringBuilder messageBuilder = new StringBuilder();
        for (ProjectSyncExecutor.ProjectSyncResult result : runResult.projectResults) {
            if (messageBuilder.length() > 0) {
                messageBuilder.append("\n\n");
            }
            messageBuilder.append(resolveProjectName(result.project))
                    .append("：")
                    .append(safeTextOrDefault(result.message,
                            getString(R.string.data_upload_status_failed_generic)));
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_data_upload_result_title, runResult.totalSyncedCount))
                .setMessage(messageBuilder.toString())
                .setPositiveButton(R.string.action_confirm, null)
                .show();
    }

    private void showTaskDetailDialog(UploadTaskCenterStore.TaskRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.data_upload_task_detail_status, getTaskStatusLabel(record.status)))
                .append("\n\n")
                .append(getString(
                        R.string.data_upload_task_detail_time,
                        formatTaskTime(record.startedAt),
                        formatTaskTime(record.finishedAt)))
                .append("\n\n")
                .append(getString(
                        R.string.data_upload_task_item_meta,
                        record.projectCount,
                        record.totalPendingPoints,
                        record.syncedPoints))
                .append("\n\n")
                .append(safeTextOrDefault(record.summary,
                        getString(R.string.data_upload_status_failed_generic)));

        if (record.projectRecords != null) {
            for (UploadTaskCenterStore.ProjectRecord projectRecord : record.projectRecords) {
                builder.append("\n\n")
                        .append(getString(
                                R.string.data_upload_task_detail_project,
                                safeTextOrDefault(projectRecord.projectName, projectRecord.databaseName),
                                safeTextOrDefault(projectRecord.message,
                                        getString(R.string.data_upload_status_failed_generic))));
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.data_upload_task_detail_title)
                .setMessage(builder.toString())
                .setPositiveButton(R.string.action_confirm, null)
                .show();
    }

    private UploadProjectItem findItem(String databaseName) {
        for (UploadProjectItem item : uploadItems) {
            if (item.project != null && TextUtils.equals(item.project.getDatabaseName(), databaseName)) {
                return item;
            }
        }
        return null;
    }

    private void rebuildVisibleItems() {
        visibleItems.clear();
        for (UploadProjectItem item : uploadItems) {
            // 失败筛选只影响页面显示范围，不改变工程本身的选中状态和数据库状态。
            if (!failedOnlyFilter || item.failedPointCount > 0) {
                visibleItems.add(item);
            }
        }
        updateFilterChips();
        projectAdapter.notifyDataSetChanged();
    }

    private void refreshProjectListVisibility() {
        boolean empty = !loading && visibleItems.isEmpty();
        binding.textEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerProjects.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateFilterChips() {
        binding.chipFilterAll.setChecked(!failedOnlyFilter);
        binding.chipFilterFailedOnly.setChecked(failedOnlyFilter);
    }

    private int resolveSummaryPointCount(UploadProjectItem item) {
        if (item == null) {
            return 0;
        }
        return failedOnlyFilter ? item.failedPointCount : item.uploadablePointCount;
    }

    private void refreshSelectionSummary() {
        int selectedCount = 0;
        int selectedPendingCount = 0;
        int totalPendingCount = 0;
        for (UploadProjectItem item : visibleItems) {
            // 摘要中的点数随筛选模式变化：全部模式看可上传点，失败模式看失败点。
            totalPendingCount += resolveSummaryPointCount(item);
            if (item.selected) {
                selectedCount++;
                selectedPendingCount += resolveSummaryPointCount(item);
            }
        }
        binding.textSelectionSummary.setText(
                getString(R.string.data_upload_selection_summary, selectedCount, selectedPendingCount));
        binding.textQueueSummary.setText(
                getString(R.string.data_upload_queue_summary, visibleItems.size(), totalPendingCount));
    }

    private void refreshConfigSummary() {
        String baseUrl = AppSettings.getSyncBaseUrl(this);
        binding.textConfigBaseUrl.setText(
                getString(R.string.data_upload_config_base_url, baseUrl));
        binding.textApiUrlPreview.setText(buildApiUrlPreview(baseUrl));

        SyncAuthManager authManager = new SyncAuthManager(this);
        String authState;
        // 配置摘要只显示当前认证准备情况，不在这里触发网络登录。
        if (!AppSettings.getSyncToken(this).isEmpty()) {
            authState = getString(R.string.data_upload_config_auth_token_ready);
        } else if (authManager.canLogin()) {
            authState = getString(R.string.data_upload_config_auth_login_ready);
        } else {
            authState = getString(R.string.data_upload_config_auth_missing);
        }
        binding.textConfigAuth.setText(getString(R.string.data_upload_config_auth, authState));
        binding.textConfigDeviceId.setText(
                getString(R.string.data_upload_config_device_id, AppSettings.getSyncDeviceId(this)));

        int engineeringCodeCount = 0;
        for (UploadProjectItem item : uploadItems) {
            if (item.project != null
                    && !TextUtils.isEmpty(AppSettings.getProjectSyncEngineeringCode(
                    this, item.project.getDatabaseName()))) {
                engineeringCodeCount++;
            }
        }
        // 远程项目 ID 和工程编码是后端归属判断的关键配置，集中显示便于现场联调核对。
        Long fixedProjectId = AppSettings.getProjectSyncRemoteProjectId(this, "");
        binding.textConfigBinding.setText(getString(
                R.string.data_upload_config_project_binding,
                fixedProjectId == null ? 0L : fixedProjectId,
                engineeringCodeCount,
                uploadItems.size()));
    }

    private String buildApiUrlPreview(String baseUrl) {
        String root = ensureTrailingSlash(baseUrl);
        // 集中展示当前 Base URL 下会访问的接口，方便联调时直接核对请求地址。
        return "登录认证：POST " + root + "auth/login"
                + "\n当前用户：GET " + root + "auth/me"
                + "\n项目列表：GET " + root + "project/page?current=1&size=20"
                + "\n项目详情：GET " + root + "project/{projectId}"
                + "\n任务选项：GET " + root + "device/task-options?projectId={projectId}"
                + "\n设备上报：POST " + root + "device/report/android"
                + "\n测量上传：POST " + root + "receive/android"
                + "\n回放详情：GET " + root + "data/playback/sample/full/{sampleId}";
    }

    private String ensureTrailingSlash(String value) {
        String safeValue = safeText(value);
        if (safeValue.isEmpty()) {
            return "";
        }
        return safeValue.endsWith("/") ? safeValue : safeValue + "/";
    }

    private void saveCredentialInputs() {
        // 登录信息来源于当前页面输入框，保存后供 SyncAuthManager 统一读取。
        AppSettings.setSyncUsername(this, safeText(binding.editSyncUsername.getText() == null
                ? null
                : binding.editSyncUsername.getText().toString()));
        AppSettings.setSyncPassword(this, safeText(binding.editSyncPassword.getText() == null
                ? null
                : binding.editSyncPassword.getText().toString()));
    }

    private void updateActiveTaskCard() {
        if (activeUploadSession != null) {
            String currentProject = safeTextOrDefault(
                    activeUploadSession.currentProjectName,
                    getString(R.string.data_upload_current_project_unknown));
            binding.textActiveTaskStatus.setText(R.string.data_upload_active_running);
            binding.textActiveTaskDetail.setText(getString(
                    R.string.data_upload_active_running_detail,
                    activeUploadSession.completedProjects,
                    activeUploadSession.totalProjects,
                    currentProject,
                    activeUploadSession.syncedPoints,
                    activeUploadSession.processedPoints,
                    activeUploadSession.failedProjects));
            int max = Math.max(1, activeUploadSession.totalPendingPoints);
            // 进度条按测点数更新，不按网络字节数更新，更贴合现场关心的采集进度。
            binding.progressUploadTask.setMax(max);
            binding.progressUploadTask.setProgress(Math.min(activeUploadSession.processedPoints, max));
            binding.progressUploadTask.setVisibility(View.VISIBLE);
            binding.textUploadProgress.setVisibility(View.VISIBLE);
            binding.textUploadProgress.setText(getString(
                    R.string.data_upload_point_progress_detail,
                    activeUploadSession.processedPoints,
                    activeUploadSession.totalPendingPoints,
                    activeUploadSession.syncedPoints,
                    activeUploadSession.failedPoints,
                    safeTextOrDefault(activeUploadSession.currentPointName,
                            getString(R.string.data_upload_current_point_unknown))));
            return;
        }

        binding.progressUploadTask.setVisibility(View.GONE);
        binding.textUploadProgress.setVisibility(View.GONE);
        if (!taskHistory.isEmpty()) {
            // 没有运行中任务时，顶部卡片展示最近一次上传结果，减少用户翻历史记录的成本。
            UploadTaskCenterStore.TaskRecord latestTask = taskHistory.get(0);
            binding.textActiveTaskStatus.setText(getTaskStatusLabel(latestTask.status));
            binding.textActiveTaskDetail.setText(
                    getString(R.string.data_upload_active_finished_detail, latestTask.summary));
            return;
        }

        binding.textActiveTaskStatus.setText(R.string.data_upload_active_idle);
        binding.textActiveTaskDetail.setText(R.string.data_upload_active_idle_detail);
    }

    private void updateActionState() {
        boolean hasProjects = !visibleItems.isEmpty();
        boolean hasSelection = false;
        int selectedPendingCount = 0;
        int selectedFailedCount = 0;
        boolean hasVisibleFailedProject = false;
        for (UploadProjectItem item : visibleItems) {
            hasVisibleFailedProject = hasVisibleFailedProject || item.failedPointCount > 0;
            if (item.selected) {
                hasSelection = true;
                selectedPendingCount += item.uploadablePointCount;
                selectedFailedCount += item.failedPointCount;
            }
        }

        // 上传过程中锁定选择和筛选按钮，避免后台任务运行时队列被用户修改。
        boolean canEditSelection = hasProjects && !loading && !uploadInProgress;
        binding.buttonRefresh.setEnabled(!loading && !uploadInProgress);
        binding.buttonSelectRegular.setEnabled(canEditSelection);
        binding.buttonSelectFailed.setEnabled(canEditSelection && hasVisibleFailedProject);
        binding.buttonClearSelection.setEnabled(canEditSelection && hasSelection);
        binding.buttonUploadSelected.setEnabled(canEditSelection && hasSelection && selectedPendingCount > 0);
        binding.buttonRetryFailedSelected.setEnabled(
                canEditSelection && hasSelection && selectedFailedCount > 0);
        binding.buttonRefreshToken.setEnabled(!refreshingToken && !uploadInProgress);
        binding.buttonClearTaskHistory.setEnabled(!taskHistory.isEmpty() && !uploadInProgress);
        binding.chipFilterAll.setEnabled(!loading && !uploadInProgress);
        binding.chipFilterFailedOnly.setEnabled(!loading && !uploadInProgress);
        binding.buttonRefreshToken.setText(
                refreshingToken
                        ? R.string.data_upload_token_refreshing
                        : R.string.data_upload_action_refresh_token);
    }

    private String buildIdleStatus(UploadProjectItem item) {
        // 空闲状态优先展示上一次同步错误，帮助用户决定是否需要失败点重试。
        if (!TextUtils.isEmpty(item.latestError)) {
            return getString(R.string.data_upload_status_previous_error, item.latestError);
        }
        if (item.uploadablePointCount > 0) {
            return getString(R.string.data_upload_status_ready);
        }
        return getString(R.string.data_upload_status_no_pending);
    }

    private String buildProjectPath(ProjectEntity project) {
        if (project == null) {
            return "";
        }
        if (!TextUtils.isEmpty(project.getDatabasePath())) {
            return project.getDatabasePath();
        }
        if (!TextUtils.isEmpty(project.getDatabaseName())) {
            return getDatabasePath(project.getDatabaseName()).getAbsolutePath();
        }
        return getString(R.string.data_upload_status_missing_database);
    }

    private String buildEngineeringCode(ProjectEntity project) {
        String configured = project == null ? "" : AppSettings.getProjectSyncEngineeringCode(this, project.getDatabaseName());
        String resolved = configured;
        if (TextUtils.isEmpty(resolved)) {
            if (project != null && !TextUtils.isEmpty(project.getName())) {
                resolved = project.getName();
            } else if (project != null && !TextUtils.isEmpty(project.getDatabaseName())) {
                resolved = project.getDatabaseName();
            } else {
                resolved = getString(R.string.data_upload_engineering_code_empty);
            }
        }
        return getString(R.string.data_upload_engineering_code, resolved);
    }

    private String buildPendingInfo(UploadProjectItem item) {
        // 工程条目同时展示可上传点和失败点，便于用户区分普通上传和失败重试。
        if (item.failedPointCount > 0) {
            return getString(
                    R.string.data_upload_pending_info_with_failed,
                    item.uploadablePointCount,
                    item.failedPointCount,
                    formatLastSyncedTime(item.project.getLastSyncedAt()));
        }
        return getString(
                R.string.data_upload_pending_info,
                item.uploadablePointCount,
                formatLastSyncedTime(item.project.getLastSyncedAt()));
    }

    private String formatLastSyncedTime(long lastSyncedAt) {
        if (lastSyncedAt <= 0L) {
            return getString(R.string.data_upload_last_synced_never);
        }
        return getString(R.string.data_upload_last_synced_at, timeFormat.format(new Date(lastSyncedAt)));
    }

    private String formatTaskTime(long taskTime) {
        if (taskTime <= 0L) {
            return getString(R.string.device_diagnostic_placeholder);
        }
        return timeFormat.format(new Date(taskTime));
    }

    private String resolveProjectName(ProjectEntity project) {
        if (project == null) {
            return getString(R.string.title_data_upload);
        }
        if (!TextUtils.isEmpty(project.getName())) {
            return project.getName();
        }
        if (!TextUtils.isEmpty(project.getDatabaseName())) {
            return project.getDatabaseName();
        }
        return getString(R.string.title_data_upload);
    }

    private String resolvePointName(MeasurementPointEntity point) {
        if (point == null) {
            return getString(R.string.data_upload_current_point_unknown);
        }
        return formatNumber(point.getName());
    }

    private String formatNumber(float value) {
        if (!Float.isFinite(value)) {
            return getString(R.string.data_upload_current_point_unknown);
        }
        int rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.0001f) {
            return String.valueOf(rounded);
        }
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private String getTaskStatusLabel(String status) {
        if (UploadTaskCenterStore.STATUS_SUCCESS.equals(status)) {
            return getString(R.string.data_upload_task_status_success);
        }
        if (UploadTaskCenterStore.STATUS_PARTIAL.equals(status)) {
            return getString(R.string.data_upload_task_status_partial);
        }
        return getString(R.string.data_upload_task_status_failed);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeTextOrDefault(String value, String fallback) {
        String trimmed = safeText(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_operation_log_center) {
            startActivity(new Intent(this, OperationLogCenterActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 页面关闭时停止接收新的后台任务，避免 Activity 被销毁后继续回调界面。
        executorService.shutdown();
    }

    private class UploadProjectAdapter extends RecyclerView.Adapter<UploadProjectAdapter.UploadProjectViewHolder> {

        @NonNull
        @Override
        public UploadProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_upload_project, parent, false);
            return new UploadProjectViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull UploadProjectViewHolder holder, int position) {
            UploadProjectItem item = visibleItems.get(position);
            holder.projectName.setText(resolveProjectName(item.project));
            holder.projectPath.setText(buildProjectPath(item.project));
            holder.projectType.setText(item.project.getImported()
                    ? R.string.data_upload_project_type_imported
                    : R.string.data_upload_project_type_regular);
            holder.engineeringCode.setText(buildEngineeringCode(item.project));
            holder.pendingInfo.setText(buildPendingInfo(item));
            holder.status.setText(getString(
                    R.string.data_upload_status_label,
                    safeTextOrDefault(item.statusMessage, buildIdleStatus(item))));

            // RecyclerView 复用 ViewHolder 时先清空旧监听，避免 setChecked 触发错误的选择回调。
            holder.selected.setOnCheckedChangeListener(null);
            holder.selected.setChecked(item.selected);
            holder.selected.setEnabled(!loading && !uploadInProgress && !item.busy);
            holder.itemView.setEnabled(!loading && !uploadInProgress && !item.busy);

            holder.selected.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.selected = isChecked;
                refreshSelectionSummary();
                updateActionState();
            });
            holder.itemView.setOnClickListener(v -> {
                if (loading || uploadInProgress || item.busy) {
                    return;
                }
                // 点击整行与点击复选框效果一致，降低现场操作时的点击精度要求。
                item.selected = !item.selected;
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(adapterPosition);
                }
                refreshSelectionSummary();
                updateActionState();
            });
        }

        @Override
        public int getItemCount() {
            return visibleItems.size();
        }

        class UploadProjectViewHolder extends RecyclerView.ViewHolder {
            final MaterialCheckBox selected;
            final TextView projectName;
            final TextView projectPath;
            final TextView projectType;
            final TextView engineeringCode;
            final TextView pendingInfo;
            final TextView status;

            UploadProjectViewHolder(@NonNull View itemView) {
                super(itemView);
                selected = itemView.findViewById(R.id.checkbox_selected);
                projectName = itemView.findViewById(R.id.text_project_name);
                projectPath = itemView.findViewById(R.id.text_project_path);
                projectType = itemView.findViewById(R.id.text_project_type);
                engineeringCode = itemView.findViewById(R.id.text_engineering_code);
                pendingInfo = itemView.findViewById(R.id.text_pending_info);
                status = itemView.findViewById(R.id.text_status);
            }
        }
    }

    private class TaskHistoryAdapter extends RecyclerView.Adapter<TaskHistoryAdapter.TaskHistoryViewHolder> {

        @NonNull
        @Override
        public TaskHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_upload_task_record, parent, false);
            return new TaskHistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TaskHistoryViewHolder holder, int position) {
            UploadTaskCenterStore.TaskRecord record = taskHistory.get(position);
            // 历史记录只显示任务摘要；点击条目后再展开每个工程的详细结果。
            holder.title.setText(getString(
                    R.string.data_upload_task_item_title,
                    formatTaskTime(record.startedAt)));
            holder.meta.setText(getString(
                    R.string.data_upload_task_item_meta,
                    record.projectCount,
                    record.totalPendingPoints,
                    record.syncedPoints));
            holder.summary.setText(safeTextOrDefault(
                    record.summary,
                    getString(R.string.data_upload_status_failed_generic)));
            holder.statusChip.setText(getTaskStatusLabel(record.status));
            holder.itemView.setOnClickListener(v -> showTaskDetailDialog(record));
        }

        @Override
        public int getItemCount() {
            return taskHistory.size();
        }

        class TaskHistoryViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView meta;
            final TextView summary;
            final TextView statusChip;

            TaskHistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.text_task_title);
                meta = itemView.findViewById(R.id.text_task_meta);
                summary = itemView.findViewById(R.id.text_task_summary);
                statusChip = itemView.findViewById(R.id.text_task_status_chip);
            }
        }
    }
}
