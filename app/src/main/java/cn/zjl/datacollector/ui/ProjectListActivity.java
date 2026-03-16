package cn.zjl.datacollector.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import cn.zjl.datacollector.ui.collection.CollectionActivity;

/**
 * 工程管理界面
 */
public class ProjectListActivity extends AppCompatActivity {
    
    private static final String TAG = "ProjectListActivity";
    
    private ActivityProjectListBinding binding;
    private ProjectAdapter adapter;
    private List<ProjectEntity> projectList;
    private ProjectRepository projectRepository;
    private boolean isLoading = false;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProjectListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        initViews();
        initRepository();
        loadProjects();
    }
    
    private void initViews() {
        projectList = new ArrayList<>();
        
        // 配置 RecyclerView
        binding.recyclerProjects.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectAdapter();
        binding.recyclerProjects.setAdapter(adapter);
        
        // 配置 SwipeRefreshLayout
        binding.swipeRefresh.setOnRefreshListener(this::loadProjects);
        binding.swipeRefresh.setColorSchemeResources(
            R.color.purple_500,
            R.color.purple_700,
            R.color.teal_200
        );
        
        // 添加工程按钮
        binding.fabAddProject.setOnClickListener(v -> showCreateProjectDialog());

    }
    
    private void initRepository() {
        projectRepository = new ProjectRepository(this);
    }
    
    private void loadProjects() {
        if (isLoading) {
            Log.w(TAG, "正在加载数据，忽略重复请求");
            stopRefreshing();
            return;
        }
        
        isLoading = true;
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "=== 开始加载数据 ===");
        
        projectRepository.getAllProjects(projects -> {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            Log.d(TAG, "=== 数据加载完成，耗时：" + duration + "ms ===");
            
            isLoading = false;
            stopRefreshing();
            
            // 统一在 updateProjectList 中处理空数据
            updateProjectList(projects);
            Log.d(TAG, "加载了 " + (projects != null ? projects.size() : 0) + " 个工程");
        });
    }
    
    /**
     * 停止刷新动画
     */
    private void stopRefreshing() {
        if (binding.swipeRefresh.isRefreshing()) {
            binding.swipeRefresh.setRefreshing(false);
        }
    }
    
    /**
     * 更新工程列表数据（用于下拉刷新等全量加载场景）
     */
    private void updateProjectList(List<ProjectEntity> projects) {
        int oldSize = projectList.size();
        projectList.clear();
        
        // 处理空数据或 null 情况
        if (projects == null || projects.isEmpty()) {
            Log.i(TAG, "没有找到工程数据");
            // 如果原来有数据，通知删除
            if (oldSize > 0) {
                adapter.notifyItemRangeRemoved(0, oldSize);
            }
            updateEmptyView();
            return;
        }
        
        // 添加新数据
        projectList.addAll(projects);
        int newSize = projectList.size();
        
        // 根据数据变化选择最优通知方式
        if (oldSize == 0) {
            // 从空到非空，全部插入
            adapter.notifyItemRangeInserted(0, newSize);
        } else if (oldSize == newSize) {
            // 数量不变，全部更新
            adapter.notifyItemRangeChanged(0, newSize);
        } else if (newSize > oldSize) {
            // 数据增加，插入新增的部分
            adapter.notifyItemRangeInserted(oldSize, newSize - oldSize);
        } else {
            // 数据减少，删除多余的部分
            adapter.notifyItemRangeRemoved(newSize, oldSize - newSize);
        }
        
        updateEmptyView();
    }
    
    private void updateEmptyView() {
        runOnUiThread(() -> {
            if (projectList.isEmpty()) {
                binding.emptyView.setVisibility(View.VISIBLE);
                binding.recyclerProjects.setVisibility(View.GONE);
            } else {
                binding.emptyView.setVisibility(View.GONE);
                binding.recyclerProjects.setVisibility(View.VISIBLE);
            }
        });
    }
    
    /**
     * 显示创建工程对话框
     */
    private void showCreateProjectDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_project, null);
        EditText editName = dialogView.findViewById(R.id.edit_project_name);
        EditText editDescription = dialogView.findViewById(R.id.edit_project_description);
        
        new AlertDialog.Builder(this)
            .setTitle("新建工程")
            .setView(dialogView)
            .setPositiveButton("创建", (dialog, which) -> {
                String name = editName.getText().toString().trim();
                String description = editDescription.getText().toString().trim();
                
                if (name.isEmpty()) {
                    Toast.makeText(this, "请输入工程名称", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                createProject(name, description);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 创建工程
     */
    private void createProject(String name, String description) {
        projectRepository.createProject(name, description, new ProjectRepository.CreateProjectCallback() {
            @Override
            public void onSuccess(ProjectEntity project) {
                Log.d(TAG, "工程创建成功：" + project.name);
                
                // 在主线程更新 UI（RecyclerView 操作必须在主线程）
                runOnUiThread(() -> {
                    // 插入到列表顶部（最新工程在最前面）
                    projectList.add(0, project);
                    adapter.notifyItemInserted(0);
                    
                    // 如果是第一个项目，更新空视图
                    if (projectList.size() == 1) {
                        updateEmptyView();
                    }
                    
                    Toast.makeText(ProjectListActivity.this, "工程创建成功", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "工程创建失败：" + error);
                runOnUiThread(() ->
                        Toast.makeText(ProjectListActivity.this, error, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
    
    /**
     * 打开工程
     */
    private void openProject(ProjectEntity project) {
        Log.d(TAG, "打开工程：" + project.name);
        Intent intent = new Intent(this, CollectionActivity.class);
        intent.putExtra("project_id", project.id);
        intent.putExtra("project_name", project.name);
        startActivity(intent);
    }
    
    /**
     * 显示删除确认对话框
     */
    private void showDeleteDialog(ProjectEntity project) {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除工程 \"" + project.name + "\" 吗？此操作不可恢复！")
            .setPositiveButton("删除", (dialog, which) -> {
                // 获取项目位置并立即从列表移除（UI 快速响应）
                int position = projectList.indexOf(project);
                if (position != -1) {
                    projectList.remove(position);
                    adapter.notifyItemRemoved(position);
                    
                    // 如果删除后为空，更新空视图
                    if (projectList.isEmpty()) {
                        updateEmptyView();
                    }
                }
                
                // 后台真正删除数据
                projectRepository.deleteProject(project, new ProjectRepository.DeleteProjectCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "工程删除成功：" + project.name);
                        runOnUiThread(() ->
                                Toast.makeText(ProjectListActivity.this, "工程已删除", Toast.LENGTH_SHORT).show()
                        );
                        // 注意：不需要 loadProjects()，因为已经局部刷新了
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "工程删除失败：" + error);
                        runOnUiThread(() -> {
                            Toast.makeText(ProjectListActivity.this, error, Toast.LENGTH_SHORT).show();
                            
                            // 失败时恢复列表项
                            if (position != -1) {
                                projectList.add(position, project);
                                adapter.notifyItemInserted(position);
                            }
                        });
                    }
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 工程列表适配器 - 使用 RecyclerView.ViewHolder 模式
     */
    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {
        
        class ProjectViewHolder extends RecyclerView.ViewHolder {
            TextView textName;
            TextView textInfo;
            
            public ProjectViewHolder(@NonNull View itemView) {
                super(itemView);
                textName = itemView.findViewById(R.id.text_project_name);
                textInfo = itemView.findViewById(R.id.text_project_info);
                
                // 设置点击监听
                itemView.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && position < projectList.size()) {
                        ProjectEntity project = projectList.get(position);
                        openProject(project);
                    }
                });
                
                // 设置长按监听
                itemView.setOnLongClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && position < projectList.size()) {
                        ProjectEntity project = projectList.get(position);
                        showDeleteDialog(project);
                        return true;
                    }
                    return false;
                });
            }
        }
        
        @NonNull
        @Override
        public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(ProjectListActivity.this)
                .inflate(R.layout.item_project, parent, false);
            return new ProjectViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
            ProjectEntity project = projectList.get(position);
            holder.textName.setText(project.name);
            
            // 格式化日期
            String dateStr = sdf.format(new Date(project.updatedAt));
            
            String info = (project.description != null ? project.description : "") + "\n更新时间：" + dateStr;
            holder.textInfo.setText(info);
        }
        
        @Override
        public int getItemCount() {
            return projectList.size();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadProjects();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
