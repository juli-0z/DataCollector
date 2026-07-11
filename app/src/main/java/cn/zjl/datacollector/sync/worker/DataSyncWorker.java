package cn.zjl.datacollector.sync.worker;

/**
 * 阅读提示：数据同步模块代码：负责登录认证、请求组装、上传执行和同步结果回写。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.zjl.datacollector.data.AppDatabase;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.sync.executor.ProjectSyncExecutor;

public class DataSyncWorker extends Worker {

    private static final String TAG = "DataSyncWorker";
    /** 周期性同步任务的唯一名称，确保系统中同一时间只保留一条周期任务。 */
    private static final String UNIQUE_PERIODIC_WORK = "periodic_data_sync";
    /** 单工程立即同步任务前缀，后面拼接数据库名，避免不同工程互相覆盖。 */
    private static final String UNIQUE_PROJECT_SYNC_PREFIX = "project_sync_";
    /** WorkManager 输入参数：指定只同步某一个工程数据库。 */
    private static final String KEY_DATABASE_NAME = "database_name";
    /** 所有同步任务共用标签，便于统一取消。 */
    private static final String WORK_TAG_SYNC = "data_sync_work";

    public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 如果 inputData 中指定了数据库名，本次任务只同步该工程；否则同步项目索引库中的全部工程。
        String specificDatabase = getInputData().getString(KEY_DATABASE_NAME);
        try {
            List<ProjectEntity> projects = AppDatabase
                    .getInstance(getApplicationContext())
                    .projectDao()
                    .getAllProjects();
            if (specificDatabase != null && !specificDatabase.trim().isEmpty()) {
                projects = filterProjects(projects, specificDatabase);
            }

            ProjectSyncExecutor.SyncRunResult runResult =
                    new ProjectSyncExecutor(getApplicationContext()).syncProjects(projects, false);
            // 网络超时、服务器 5xx 等可重试错误交给 WorkManager 按退避策略再次调度。
            if (runResult.hasRetryableFailure) {
                return Result.retry();
            }

            Data output = new Data.Builder()
                    .putInt("synced_count", runResult.totalSyncedCount)
                    .build();
            if (runResult.hasFatalFailure && runResult.totalSyncedCount == 0) {
                return Result.failure(output);
            }
            return Result.success(output);
        } catch (Exception e) {
            Log.e(TAG, "同步失败", e);
            return Result.retry();
        }
    }

    private List<ProjectEntity> filterProjects(List<ProjectEntity> projects, String databaseName) {
        // WorkManager 只传递简单输入值，这里根据数据库名从项目列表中过滤出目标工程。
        List<ProjectEntity> filtered = new ArrayList<>();
        if (projects == null) {
            return filtered;
        }
        for (ProjectEntity project : projects) {
            if (project != null && databaseName.equals(project.getDatabaseName())) {
                filtered.add(project);
            }
        }
        return filtered;
    }

    public static void schedulePeriodicSync(Context context) {
        // 周期性任务仅在有网络时运行；实际上传仍会在 ProjectSyncExecutor 中逐点处理失败状态。
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(DataSyncWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .addTag(WORK_TAG_SYNC)
                        .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    public static void scheduleProjectSync(Context context, String databaseName) {
        // 单工程同步用于“立即同步”或指定工程补传，REPLACE 可避免用户连续点击导致重复排队。
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data input = new Data.Builder().putString(KEY_DATABASE_NAME, databaseName).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DataSyncWorker.class)
                .setConstraints(constraints)
                .setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(WORK_TAG_SYNC)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PROJECT_SYNC_PREFIX + databaseName,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    public static void cancelAllSync(Context context) {
        // 先取消统一标签下的任务，再异步遍历历史工程，清理按数据库名创建的唯一任务。
        Context appContext = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(appContext);
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK);
        workManager.cancelAllWorkByTag(WORK_TAG_SYNC);
        new Thread(() -> {
            try {
                List<ProjectEntity> projects = AppDatabase
                        .getInstance(appContext)
                        .projectDao()
                        .getAllProjects();
                for (ProjectEntity project : projects) {
                    if (project != null
                            && project.getDatabaseName() != null
                            && !project.getDatabaseName().trim().isEmpty()) {
                        workManager.cancelUniqueWork(UNIQUE_PROJECT_SYNC_PREFIX + project.getDatabaseName());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "取消历史同步任务时发生异常", e);
            }
        }).start();
    }
}
