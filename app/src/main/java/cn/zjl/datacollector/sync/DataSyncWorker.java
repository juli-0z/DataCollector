package cn.zjl.datacollector.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.repository.DataRepository;

/**
 * 数据同步 Worker
 * 负责将本地数据同步到远程服务器
 */
public class DataSyncWorker extends Worker {
    private static final String TAG = "DataSyncWorker";
    
    public static final String KEY_SYNC_RESULT = "sync_result";
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAILURE = 0;
    
    private DataRepository dataRepository;
    
    public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        dataRepository = new DataRepository(context);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Starting data sync");
        
        try {
            // 获取未同步的测点数据
            List<MeasurementPointEntity> unsyncedPoints = dataRepository.getUnsyncedPoints();
            
            if (unsyncedPoints.isEmpty()) {
                Log.i(TAG, "No unsynced data");
                return Result.success();
            }
            
            Log.i(TAG, "Found " + unsyncedPoints.size() + " points to sync");
            
            // 逐个同步测点数据
            int successCount = 0;
            for (MeasurementPointEntity point : unsyncedPoints) {
                if (syncPoint(point)) {
                    successCount++;
                    // 标记为已同步
                    dataRepository.markPointAsSynced(point.id);
                    Log.d(TAG, "Synced point: " + point.pointNumber);
                } else {
                    Log.w(TAG, "Failed to sync point: " + point.pointNumber);
                }
                
                // 检查是否被取消
                if (isStopped()) {
                    Log.w(TAG, "Sync cancelled");
                    return Result.failure();
                }
            }
            
            Log.i(TAG, "Sync completed: " + successCount + "/" + unsyncedPoints.size());
            
            Data outputData = new Data.Builder()
                .putInt(KEY_SYNC_RESULT, successCount)
                .build();
            
            return Result.success(outputData);
            
        } catch (Exception e) {
            Log.e(TAG, "Sync error", e);
            return Result.retry();
        }
    }
    
    /**
     * 同步单个测点数据
     */
    private boolean syncPoint(MeasurementPointEntity point) {
        try {
            // TODO: 实现实际的 HTTPS 上传逻辑
            // 这里使用 Retrofit 或其他 HTTP 客户端上传数据到服务器
            
            // 示例伪代码：
            /*
            SyncRequest request = new SyncRequest();
            request.pointNumber = point.pointNumber;
            request.latitude = point.latitude;
            request.longitude = point.longitude;
            request.parameters = dataRepository.getParametersByPointId(point.id);
            request.waveforms = dataRepository.getWaveformsByPointId(point.id);
            request.monitors = dataRepository.getMonitorsByPointId(point.id);
            
            Response<SyncResponse> response = apiService.syncData(request).execute();
            return response.isSuccessful();
            */
            
            // 模拟成功
            Thread.sleep(100);  // 模拟网络延迟
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error syncing point " + point.id, e);
            return false;
        }
    }
    
    /**
     * 调度一次性同步任务
     */
    public static void scheduleOneTimeSync(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build();
        
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(DataSyncWorker.class)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .build();
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            "data_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        );
        
        Log.i(TAG, "One-time sync scheduled");
    }
    
    /**
     * 检查同步任务状态
     */
    public static int getSyncResult(Context context) {
        try {
            List<WorkInfo> workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("data_sync")
                .get();
            
            if (!workInfos.isEmpty()) {
                WorkInfo workInfo = workInfos.get(workInfos.size() - 1);
                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    Data outputData = workInfo.getOutputData();
                    return outputData.getInt(KEY_SYNC_RESULT, 0);
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error getting sync result", e);
        }
        
        return 0;
    }
}
