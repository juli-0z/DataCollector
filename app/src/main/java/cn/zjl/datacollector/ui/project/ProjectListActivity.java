package cn.zjl.datacollector.ui.project;

/**
 * 阅读提示：工程列表入口界面：负责新建、导入、打开工程，并进入采集、上传、日志等功能页面。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.repository.ProjectRepository;
import cn.zjl.datacollector.databinding.ActivityProjectListBinding;
import cn.zjl.datacollector.sync.worker.DataSyncWorker;
import cn.zjl.datacollector.sync.auth.SyncAuthManager;
import cn.zjl.datacollector.ui.common.ProjectExportCoordinator;
import cn.zjl.datacollector.ui.collection.screen.CollectionAndPlaybackActivity;
import cn.zjl.datacollector.ui.log.OperationLogCenterActivity;
import cn.zjl.datacollector.ui.log.OperationLogStore;
import cn.zjl.datacollector.ui.upload.DataUploadActivity;
import cn.zjl.datacollector.util.AppSettings;

public class ProjectListActivity extends AppCompatActivity {

    private static final int PROJECT_ACTION_OPEN = 0;
    private static final int PROJECT_ACTION_SYNC_BINDING = 1;
    private static final int PROJECT_ACTION_EXPORT = 2;
    private static final int PROJECT_ACTION_DELETE = 3;

    private ActivityProjectListBinding binding;
    private final List<ProjectEntity> projectList = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private ProjectRepository projectRepository;
    private ProjectExportCoordinator exportCoordinator;
    private ProjectAdapter adapter;
    private OperationLogStore operationLogStore;

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleImportResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProjectListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        applyToolbarInsets();
        projectRepository = new ProjectRepository(this);
        exportCoordinator = new ProjectExportCoordinator(this);
        adapter = new ProjectAdapter();
        operationLogStore = new OperationLogStore(this);

        binding.recyclerProjects.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerProjects.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::loadProjects);
        binding.fabAddProject.setOnClickListener(v -> showCreateOrImportDialog());

        DataSyncWorker.cancelAllSync(this);
        initializeProjects();
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

    private void initializeProjects() {
        binding.swipeRefresh.setRefreshing(true);
        projectRepository.ensureBundledProjectsInstalled(new ProjectRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                loadProjects();
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    binding.swipeRefresh.setRefreshing(false);
                    Toast.makeText(ProjectListActivity.this, error, Toast.LENGTH_LONG).show();
                    loadProjects();
                });
            }
        });
    }

    private void showCreateOrImportDialog() {
        String[] items = new String[]{
                getString(R.string.action_new_project),
                getString(R.string.action_import_database)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_project_operation)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showCreateProjectDialog();
                    } else {
                        importLauncher.launch(new String[]{
                                "application/octet-stream",
                                "application/x-sqlite3",
                                "*/*"
                        });
                    }
                })
                .show();
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tcp_config, null);
        EditText editIp = dialogView.findViewById(R.id.edit_tcp_ip);
        EditText editPort = dialogView.findViewById(R.id.edit_tcp_port);
        EditText editSyncBaseUrl = dialogView.findViewById(R.id.edit_sync_base_url);
        EditText editSyncUsername = dialogView.findViewById(R.id.edit_sync_username);
        EditText editSyncPassword = dialogView.findViewById(R.id.edit_sync_password);
        EditText editSyncToken = dialogView.findViewById(R.id.edit_sync_token);
        EditText editSyncDeviceId = dialogView.findViewById(R.id.edit_sync_device_id);
        editIp.setText(AppSettings.getTcpIp(this));
        editPort.setText(String.valueOf(AppSettings.getTcpPort(this)));
        editSyncBaseUrl.setText(AppSettings.getSyncBaseUrl(this));
        editSyncUsername.setText(AppSettings.getSyncUsername(this));
        editSyncPassword.setText(AppSettings.getSyncPassword(this));
        editSyncToken.setText(AppSettings.getSyncToken(this));
        editSyncDeviceId.setText(AppSettings.getSyncDeviceId(this));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_sync_settings)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, null)
                .setNeutralButton(R.string.action_sync_login, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                persistSyncSettings(
                        editIp,
                        editPort,
                        editSyncBaseUrl,
                        editSyncUsername,
                        editSyncPassword,
                        editSyncToken,
                        editSyncDeviceId);
                operationLogStore.record(
                        OperationLogStore.CATEGORY_SETTINGS,
                        getString(R.string.operation_log_title_save_sync_settings),
                        getString(
                                R.string.operation_log_detail_sync_settings_saved,
                                AppSettings.getSyncBaseUrl(this),
                                AppSettings.getSyncDeviceId(this)),
                        "");
                Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                persistSyncSettings(
                        editIp,
                        editPort,
                        editSyncBaseUrl,
                        editSyncUsername,
                        editSyncPassword,
                        editSyncToken,
                        editSyncDeviceId);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                SyncAuthManager authManager = new SyncAuthManager(this);
                authManager.loginAsync(result -> runOnUiThread(() -> {
                    if (dialog.isShowing() && dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                    }
                    operationLogStore.record(
                            OperationLogStore.CATEGORY_SETTINGS,
                            getString(result.success
                                    ? R.string.operation_log_title_sync_login_succeeded
                                    : R.string.operation_log_title_sync_login_failed),
                            result.message,
                            "");
                    if (result.success) {
                        if (dialog.isShowing()) {
                            editSyncToken.setText(result.token);
                        }
                        Toast.makeText(this, R.string.toast_sync_login_success, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                    }
                }));
            });
        });
        dialog.show();
    }

    private void persistSyncSettings(EditText editIp,
                                     EditText editPort,
                                     EditText editSyncBaseUrl,
                                     EditText editSyncUsername,
                                     EditText editSyncPassword,
                                     EditText editSyncToken,
                                     EditText editSyncDeviceId) {
        int port;
        try {
            port = Integer.parseInt(editPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = 8080;
        }
        AppSettings.saveTcp(this, editIp.getText().toString().trim(), port);
        AppSettings.setSyncBaseUrl(this, editSyncBaseUrl.getText().toString().trim());
        AppSettings.setSyncUsername(this, editSyncUsername.getText().toString().trim());
        AppSettings.setSyncPassword(this, editSyncPassword.getText().toString().trim());
        AppSettings.setSyncToken(this, editSyncToken.getText().toString().trim());
        AppSettings.setSyncDeviceId(this, editSyncDeviceId.getText().toString().trim());
    }

    private void showCreateProjectDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_project, null);
        EditText editName = dialogView.findViewById(R.id.edit_project_name);
        EditText editDescription = dialogView.findViewById(R.id.edit_project_description);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_create_project)
                .setView(dialogView)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.toast_input_project_name, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    projectRepository.createProject(name, editDescription.getText().toString().trim(), new ProjectRepository.CreateProjectCallback() {
                        @Override
                        public void onSuccess(ProjectEntity project) {
                            runOnUiThread(() -> {
                                operationLogStore.record(
                                        OperationLogStore.CATEGORY_PROJECT,
                                        getString(R.string.operation_log_title_project_created),
                                        getString(
                                                R.string.operation_log_detail_project_name_database,
                                                project.getName(),
                                                project.getDatabaseName()),
                                        project.getDatabaseName());
                                Toast.makeText(ProjectListActivity.this, R.string.toast_project_created, Toast.LENGTH_SHORT).show();
                                loadProjects();
                                openProject(project);
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(ProjectListActivity.this, error, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void handleImportResult(Uri uri) {
        if (uri == null) {
            return;
        }
        projectRepository.importProject(uri, null, new ProjectRepository.CreateProjectCallback() {
            @Override
            public void onSuccess(ProjectEntity project) {
                runOnUiThread(() -> {
                    operationLogStore.record(
                            OperationLogStore.CATEGORY_PROJECT,
                            getString(R.string.operation_log_title_project_imported),
                            getString(
                                    R.string.operation_log_detail_project_name_database,
                                    project.getName(),
                                    project.getDatabaseName()),
                            project.getDatabaseName());
                    Toast.makeText(ProjectListActivity.this, R.string.toast_database_imported, Toast.LENGTH_SHORT).show();
                    loadProjects();
                    openProject(project);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ProjectListActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void loadProjects() {
        projectRepository.getAllProjects(projects -> runOnUiThread(() -> {
            binding.swipeRefresh.setRefreshing(false);
            projectList.clear();
            if (projects != null) {
                projectList.addAll(projects);
            }
            adapter.notifyDataSetChanged();
        }));
    }

    private void openProject(ProjectEntity project) {
        Intent intent = new Intent(this, CollectionAndPlaybackActivity.class);
        intent.putExtra("database_name", project.getDatabaseName());
        intent.putExtra(CollectionAndPlaybackActivity.EXTRA_READ_ONLY_HINT, project.getImported());
        startActivity(intent);
    }

    private void showProjectMenu(ProjectEntity project) {
        List<String> labels = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        labels.add(getString(project.getImported()
                ? R.string.menu_playback
                : R.string.menu_enter_collection));
        actions.add(PROJECT_ACTION_OPEN);
        if (!project.getImported()) {
            labels.add(getString(R.string.menu_sync_binding));
            actions.add(PROJECT_ACTION_SYNC_BINDING);
        }
        labels.add(getString(R.string.menu_export_database));
        actions.add(PROJECT_ACTION_EXPORT);
        labels.add(getString(R.string.menu_delete_project));
        actions.add(PROJECT_ACTION_DELETE);

        new AlertDialog.Builder(this)
                .setTitle(project.getName())
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    switch (actions.get(which)) {
                        case PROJECT_ACTION_OPEN:
                            openProject(project);
                            break;
                        case PROJECT_ACTION_SYNC_BINDING:
                            showProjectSyncBindingDialog(project);
                            break;
                        case PROJECT_ACTION_EXPORT:
                            exportProject(project);
                            break;
                        case PROJECT_ACTION_DELETE:
                            confirmDelete(project);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void showProjectSyncBindingDialog(ProjectEntity project) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_project_sync_binding, null);
        EditText editRemoteProjectId = dialogView.findViewById(R.id.edit_remote_project_id);
        EditText editEngineeringCode = dialogView.findViewById(R.id.edit_sync_engineering_code);
        Long remoteProjectId = AppSettings.getProjectSyncRemoteProjectId(this, project.getDatabaseName());
        if (remoteProjectId != null) {
            editRemoteProjectId.setText(String.valueOf(remoteProjectId));
        }
        editEngineeringCode.setText(AppSettings.getProjectSyncEngineeringCode(this, project.getDatabaseName()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_project_sync_binding)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Long parsedRemoteProjectId = parseRemoteProjectId(editRemoteProjectId.getText() == null
                    ? ""
                    : editRemoteProjectId.getText().toString());
            if (parsedRemoteProjectId == null) {
                editRemoteProjectId.setError(getString(R.string.error_sync_missing_remote_project_id));
                return;
            }
            AppSettings.setProjectSyncRemoteProjectId(this, project.getDatabaseName(), parsedRemoteProjectId);
            AppSettings.setProjectSyncEngineeringCode(
                    this,
                    project.getDatabaseName(),
                    editEngineeringCode.getText().toString().trim());
            operationLogStore.record(
                    OperationLogStore.CATEGORY_PROJECT,
                    getString(R.string.operation_log_title_save_project_sync_binding),
                    getString(
                            R.string.operation_log_detail_project_sync_binding,
                            project.getName(),
                            parsedRemoteProjectId,
                            AppSettings.getProjectSyncEngineeringCode(this, project.getDatabaseName())),
                    project.getDatabaseName());
            Toast.makeText(this, R.string.toast_project_sync_binding_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private Long parseRemoteProjectId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(trimmed);
            return parsed > 0L ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void exportProject(ProjectEntity project) {
        operationLogStore.record(
                OperationLogStore.CATEGORY_PROJECT,
                getString(R.string.operation_log_title_export_project_database),
                getString(
                        R.string.operation_log_detail_project_name_database,
                        project.getName(),
                        project.getDatabaseName()),
                project.getDatabaseName());
        exportCoordinator.exportDatabase(exportCoordinator.resolveDatabasePath(project, project.getDatabaseName()));
    }

    private void confirmDelete(ProjectEntity project) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_project)
                .setMessage(getString(R.string.dialog_delete_project_message, project.getName()))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> projectRepository.deleteProject(project, new ProjectRepository.DeleteProjectCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            operationLogStore.record(
                                    OperationLogStore.CATEGORY_PROJECT,
                                    getString(R.string.operation_log_title_project_deleted),
                                    getString(
                                            R.string.operation_log_detail_project_name_database,
                                            project.getName(),
                                            project.getDatabaseName()),
                                    project.getDatabaseName());
                            Toast.makeText(ProjectListActivity.this, R.string.toast_project_deleted, Toast.LENGTH_SHORT).show();
                            loadProjects();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(ProjectListActivity.this, error, Toast.LENGTH_SHORT).show());
                    }
                }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProjects();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_project_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_data_upload) {
            startActivity(new Intent(this, DataUploadActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_sync_settings) {
            showSettingsDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_operation_log_center) {
            startActivity(new Intent(this, OperationLogCenterActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

        @NonNull
        @Override
        public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project, parent, false);
            return new ProjectViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
            ProjectEntity project = projectList.get(position);
            holder.name.setText(project.getName());
            holder.info.setText(project.getNote() == null || project.getNote().isEmpty()
                    ? getString(R.string.item_project_default_info)
                    : project.getNote());
            holder.date.setText(getString(R.string.item_project_updated_at, sdf.format(new Date(project.getUpdatedAt()))));
            holder.itemView.setOnClickListener(v -> openProject(project));
            holder.itemView.setOnLongClickListener(v -> {
                showProjectMenu(project);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return projectList.size();
        }

        class ProjectViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            TextView info;
            TextView date;

            ProjectViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.text_project_name);
                info = itemView.findViewById(R.id.text_project_info);
                date = itemView.findViewById(R.id.text_project_date);
            }
        }
    }
}
