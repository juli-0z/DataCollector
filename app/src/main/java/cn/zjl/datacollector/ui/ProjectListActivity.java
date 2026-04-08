package cn.zjl.datacollector.ui;

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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.repository.ProjectRepository;
import cn.zjl.datacollector.databinding.ActivityProjectListBinding;
import cn.zjl.datacollector.sync.DataSyncWorker;
import cn.zjl.datacollector.ui.collection.CollectionAndPlaybackActivity;
import cn.zjl.datacollector.util.AppSettings;
import cn.zjl.datacollector.util.ExportUtils;

public class ProjectListActivity extends AppCompatActivity {

    private ActivityProjectListBinding binding;
    private final List<ProjectEntity> projectList = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private ProjectRepository projectRepository;
    private ProjectAdapter adapter;

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
        adapter = new ProjectAdapter();

        binding.recyclerProjects.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerProjects.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::loadProjects);
        binding.fabAddProject.setOnClickListener(v -> showCreateOrImportDialog());

        DataSyncWorker.schedulePeriodicSync(this);
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
        editIp.setText(AppSettings.getTcpIp(this));
        editPort.setText(String.valueOf(AppSettings.getTcpPort(this)));

        EditText editUrl = new EditText(this);
        editUrl.setHint("https://example.com/");
        editUrl.setText(AppSettings.getSyncBaseUrl(this));
        editUrl.setPadding(48, 24, 48, 0);

        ViewGroup container = (ViewGroup) dialogView;
        container.addView(editUrl);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_sync_settings)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    int port;
                    try {
                        port = Integer.parseInt(editPort.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        port = 8080;
                    }
                    AppSettings.saveTcp(this, editIp.getText().toString().trim(), port);
                    AppSettings.setSyncBaseUrl(this, editUrl.getText().toString().trim());
                    Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
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
                                Toast.makeText(ProjectListActivity.this, R.string.toast_project_created, Toast.LENGTH_SHORT).show();
                                loadProjects();
                                openProject(project, CollectionAndPlaybackActivity.COLLECTION_MODE);
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
                    Toast.makeText(ProjectListActivity.this, R.string.toast_database_imported, Toast.LENGTH_SHORT).show();
                    loadProjects();
                    openProject(project, CollectionAndPlaybackActivity.PLAYBACK_MODE);
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

    private void openProject(ProjectEntity project, int mode) {
        Intent intent = new Intent(this, CollectionAndPlaybackActivity.class);
        intent.putExtra("database_name", project.databaseName);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

    private void showProjectMenu(ProjectEntity project) {
        String[] items = new String[]{
                getString(R.string.menu_enter_collection),
                getString(R.string.menu_playback),
                getString(R.string.menu_sync_now),
                getString(R.string.menu_export_database),
                getString(R.string.menu_delete_project)
        };
        new AlertDialog.Builder(this)
                .setTitle(project.name)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openProject(project, CollectionAndPlaybackActivity.COLLECTION_MODE);
                            break;
                        case 1:
                            openProject(project, CollectionAndPlaybackActivity.PLAYBACK_MODE);
                            break;
                        case 2:
                            DataSyncWorker.scheduleProjectSync(this, project.databaseName);
                            Toast.makeText(this, R.string.toast_sync_submitted, Toast.LENGTH_SHORT).show();
                            break;
                        case 3:
                            exportProject(project);
                            break;
                        case 4:
                            confirmDelete(project);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void exportProject(ProjectEntity project) {
        if (project.databasePath == null) {
            Toast.makeText(this, R.string.toast_missing_project_database_path, Toast.LENGTH_SHORT).show();
            return;
        }
        ExportUtils.exportDatabase(this, project.databasePath, new ExportUtils.ExportCallback() {
            @Override
            public void onSuccess(File exportedFile) {
                runOnUiThread(() -> new AlertDialog.Builder(ProjectListActivity.this)
                        .setTitle(R.string.dialog_export_success)
                        .setMessage(exportedFile.getAbsolutePath())
                        .setNegativeButton(R.string.action_open_export_folder, (dialog, which) -> {
                            if (!ExportUtils.openContainingDirectory(ProjectListActivity.this, exportedFile)) {
                                Toast.makeText(
                                        ProjectListActivity.this,
                                        getString(R.string.toast_open_export_folder_failed, exportedFile.getParent()),
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNeutralButton(R.string.action_share_file, (dialog, which) -> {
                            if (!ExportUtils.shareFile(ProjectListActivity.this, exportedFile)) {
                                Toast.makeText(ProjectListActivity.this, R.string.toast_share_file_failed, Toast.LENGTH_LONG).show();
                            }
                        })
                        .setPositiveButton(R.string.action_confirm, null)
                        .show());
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ProjectListActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmDelete(ProjectEntity project) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_project)
                .setMessage(getString(R.string.dialog_delete_project_message, project.name))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> projectRepository.deleteProject(project, new ProjectRepository.DeleteProjectCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
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
        if (item.getItemId() == R.id.action_sync_settings) {
            showSettingsDialog();
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
            holder.name.setText(project.name);
            holder.info.setText(project.note == null || project.note.isEmpty()
                    ? getString(R.string.item_project_default_info)
                    : project.note);
            holder.date.setText(getString(R.string.item_project_updated_at, sdf.format(new Date(project.updatedAt))));
            holder.itemView.setOnClickListener(v -> openProject(project, CollectionAndPlaybackActivity.COLLECTION_MODE));
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
