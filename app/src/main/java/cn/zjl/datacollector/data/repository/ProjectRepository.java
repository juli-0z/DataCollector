package cn.zjl.datacollector.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.zjl.datacollector.data.AppDatabase;
import cn.zjl.datacollector.data.dao.ProjectDao;
import cn.zjl.datacollector.data.entity.ProjectEntity;

/**
 * 工程数据仓库
 */
public class ProjectRepository {
    private final ProjectDao projectDao;
    private final ExecutorService executorService;
    private final Context context;
    
    public ProjectRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(context);
        projectDao = database.projectDao();
        executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 创建新工程
     */
    public interface CreateProjectCallback {
        void onSuccess(ProjectEntity project);
        void onError(String error);
    }
    
    public void createProject(String name, String description, CreateProjectCallback callback) {
        executorService.execute(() -> {
            try {
                // 检查是否已存在同名工程
                ProjectEntity existingProject = projectDao.getProjectByName(name);
                if (existingProject != null) {
                    if (callback != null) {
                        callback.onError("工程名称已存在");
                    }
                    return;
                }
                
                // 创建数据库文件
                File dbFile = new File(context.getFilesDir(), name + ".db");
                
                // 创建 SQLite 数据库文件
                SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
                db.close();
                
                // 创建工程记录
                ProjectEntity project = new ProjectEntity();
                project.name = name;
                project.databasePath = dbFile.getAbsolutePath();
                project.createdAt = System.currentTimeMillis();
                project.updatedAt = System.currentTimeMillis();
                project.description = description;
                project.isSynced = false;
                
                long projectId = projectDao.insert(project);
                project.id = projectId;
                
                if (callback != null) {
                    callback.onSuccess(project);
                }
            } catch (SQLiteException e) {
                e.printStackTrace();
                if (callback != null) {
                    callback.onError("创建工程失败：" + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 打开已有工程
     */
    public interface OpenProjectCallback {
        void onSuccess(ProjectEntity project);
        void onError(String error);
    }
    
    public void openProject(long projectId, OpenProjectCallback callback) {
        executorService.execute(() -> {
            try {
                ProjectEntity project = projectDao.getProjectById(projectId);
                if (project == null) {
                    if (callback != null) {
                        callback.onError("工程不存在");
                    }
                    return;
                }
                
                // 检查数据库文件是否存在
                File dbFile = new File(project.databasePath);
                if (!dbFile.exists()) {
                    if (callback != null) {
                        callback.onError("工程数据库文件不存在");
                    }
                    return;
                }
                
                if (callback != null) {
                    callback.onSuccess(project);
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) {
                    callback.onError("打开工程失败：" + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 获取所有工程
     */
    public interface GetProjectsCallback {
        void onResult(List<ProjectEntity> projects);
    }
    
    public void getAllProjects(GetProjectsCallback callback) {
        executorService.execute(() -> {
            List<ProjectEntity> projects = projectDao.getAllProjects();
            if (callback != null) {
                callback.onResult(projects);
            }
        });
    }
    
    /**
     * 更新工程信息
     */
    public void updateProject(ProjectEntity project) {
        executorService.execute(() -> {
            project.updatedAt = System.currentTimeMillis();
            projectDao.update(project);
        });
    }
    
    /**
     * 删除工程
     */
    public interface DeleteProjectCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public void deleteProject(ProjectEntity project, DeleteProjectCallback callback) {
        executorService.execute(() -> {
            try {
                // 删除数据库文件
                File dbFile = new File(project.databasePath);
                if (dbFile.exists()) {
                    dbFile.delete();
                }
                
                // 删除工程记录
                projectDao.delete(project);
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) {
                    callback.onError("删除工程失败：" + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 更新同步状态
     */
    public void updateSyncStatus(long projectId, boolean synced) {
        executorService.execute(() -> {
            projectDao.updateSyncStatus(projectId, synced);
        });
    }
}
