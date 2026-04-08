package cn.zjl.datacollector.sync;

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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.zjl.datacollector.data.AppDatabase;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.net.RetrofitClient;
import cn.zjl.datacollector.net.SyncApiService;
import cn.zjl.datacollector.net.SyncRequest;
import cn.zjl.datacollector.net.SyncResponse;
import retrofit2.Response;

public class DataSyncWorker extends Worker {

    private static final String TAG = "DataSyncWorker";
    private static final String UNIQUE_PERIODIC_WORK = "periodic_data_sync";
    private static final String UNIQUE_PROJECT_SYNC_PREFIX = "project_sync_";
    private static final String KEY_DATABASE_NAME = "database_name";

    public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String specificDatabase = getInputData().getString(KEY_DATABASE_NAME);
        try {
            List<ProjectEntity> projects = AppDatabase.getInstance(getApplicationContext()).projectDao().getAllProjects();
            int syncedCount = 0;
            for (ProjectEntity project : projects) {
                if (specificDatabase != null && !specificDatabase.equals(project.databaseName)) {
                    continue;
                }
                syncedCount += syncProject(project);
            }
            return Result.success(new Data.Builder().putInt("synced_count", syncedCount).build());
        } catch (Exception e) {
            Log.e(TAG, "同步失败", e);
            return Result.retry();
        }
    }

    private int syncProject(ProjectEntity project) throws IOException {
        if (project.databaseName == null || project.databaseName.isEmpty()) {
            return 0;
        }
        DataRepository repository = new DataRepository(getApplicationContext(), project.databaseName);
        List<MeasurementPointEntity> unsyncedPoints = repository.getUnsyncedPointsSync();
        if (unsyncedPoints.isEmpty()) {
            return 0;
        }

        SyncApiService apiService = RetrofitClient.getSyncApiService(getApplicationContext());
        int successCount = 0;
        for (MeasurementPointEntity point : unsyncedPoints) {
            DataRepository.PointData pointData = repository.getAllDataByPointSync(point.id);
            SyncRequest request = new SyncRequest();
            request.projectId = project.id;
            request.point = pointData.point;
            request.parameters = pointData.parameters;
            request.waveforms = pointData.waveforms.toArray(new cn.zjl.datacollector.data.entity.WaveformDataEntity[0]);
            request.monitors = pointData.monitors.toArray(new cn.zjl.datacollector.data.entity.DeviceMonitorEntity[0]);

            Response<SyncResponse> response = apiService.uploadData(request).execute();
            if (response.isSuccessful() && response.body() != null && response.body().success) {
                repository.markPointAsSynced(point.id);
                successCount++;
            } else {
                String error = response.body() != null ? response.body().message : "服务端返回失败";
                repository.markPointSyncError(point.id, error);
            }
        }
        return successCount;
    }

    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(DataSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    public static void scheduleProjectSync(Context context, String databaseName) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data input = new Data.Builder().putString(KEY_DATABASE_NAME, databaseName).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DataSyncWorker.class)
                .setConstraints(constraints)
                .setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PROJECT_SYNC_PREFIX + databaseName,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }
}
