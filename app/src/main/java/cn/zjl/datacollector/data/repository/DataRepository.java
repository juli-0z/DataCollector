package cn.zjl.datacollector.data.repository;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.zjl.datacollector.data.AppDatabase;
import cn.zjl.datacollector.data.dao.CollectionParameterDao;
import cn.zjl.datacollector.data.dao.DeviceMonitorDao;
import cn.zjl.datacollector.data.dao.MeasurementPointDao;
import cn.zjl.datacollector.data.dao.SurveyLineDao;
import cn.zjl.datacollector.data.dao.WaveformDao;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;

/**
 * 综合数据仓库
 */
public class DataRepository {
    private final MeasurementPointDao pointDao;
    private final SurveyLineDao lineDao;
    private final CollectionParameterDao parameterDao;
    private final WaveformDao waveformDao;
    private final DeviceMonitorDao monitorDao;
    private final ExecutorService executorService;
    
    public DataRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        pointDao = database.measurementPointDao();
        lineDao = database.surveyLineDao();
        parameterDao = database.collectionParameterDao();
        waveformDao = database.waveformDao();
        monitorDao = database.deviceMonitorDao();
        executorService = Executors.newSingleThreadExecutor();
    }
    
    // ========== 测线相关 ==========
    
    public void createSurveyLine(long projectId, String name, String description, SaveCallback<Long> callback) {
        executorService.execute(() -> {
            SurveyLineEntity line = new SurveyLineEntity();
            line.projectId = projectId;
            line.name = name;
            line.description = description;
            line.createdAt = System.currentTimeMillis();
            line.updatedAt = System.currentTimeMillis();
            
            long lineId = lineDao.insert(line);
            if (callback != null) {
                callback.onSuccess(lineId);
            }
        });
    }
    
    public interface SaveCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
    
    public void getSurveyLinesByProject(long projectId, LoadListCallback<SurveyLineEntity> callback) {
        executorService.execute(() -> {
            List<SurveyLineEntity> lines = lineDao.getSurveyLinesByProjectId(projectId);
            if (callback != null) {
                callback.onResult(lines);
            }
        });
    }
    
    // ========== 测点相关 ==========
    
    public void createMeasurementPoint(long surveyLineId, int pointNumber, SaveCallback<Long> callback) {
        executorService.execute(() -> {
            MeasurementPointEntity point = new MeasurementPointEntity();
            point.surveyLineId = surveyLineId;
            point.pointNumber = pointNumber;
            point.status = 0;  // 未采集
            point.isQualified = false;
            point.collectionTime = System.currentTimeMillis();
            
            long pointId = pointDao.insert(point);
            if (callback != null) {
                callback.onSuccess(pointId);
            }
        });
    }
    
    public void getPointsBySurveyLine(long surveyLineId, LoadListCallback<MeasurementPointEntity> callback) {
        executorService.execute(() -> {
            List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(surveyLineId);
            if (callback != null) {
                callback.onResult(points);
            }
        });
    }
    
    public interface LoadListCallback<T> {
        void onResult(List<T> list);
    }
    
    public void updatePointStatus(long pointId, int status) {
        executorService.execute(() -> {
            pointDao.updateStatus(pointId, status);
        });
    }
    
    public void markPointAsSynced(long pointId) {
        executorService.execute(() -> {
            pointDao.updateStatus(pointId, 3);  // 状态 3: 已同步
        });
    }
    
    public List<MeasurementPointEntity> getUnsyncedPoints() {
        // 获取已保存但未同步的点（状态 2）
        return pointDao.getQualifiedPoints();
    }
    
    // ========== 参数相关 ==========
    
    public void saveParameters(CollectionParameterEntity parameters, SaveCallback<Long> callback) {
        executorService.execute(() -> {
            long paramId = parameterDao.insert(parameters);
            if (callback != null) {
                callback.onSuccess(paramId);
            }
        });
    }
    
    public void getLatestParameters(long pointId, LoadObjectCallback<CollectionParameterEntity> callback) {
        executorService.execute(() -> {
            CollectionParameterEntity params = parameterDao.getLatestParametersByPointId(pointId);
            if (callback != null) {
                callback.onResult(params);
            }
        });
    }
    
    // ========== 波形数据相关 ==========
    
    public void saveWaveforms(List<WaveformDataEntity> waveforms, SaveCallback<List<Long>> callback) {
        executorService.execute(() -> {
            List<Long> ids = waveformDao.insertAll(waveforms);
            if (callback != null) {
                callback.onSuccess(ids);
            }
        });
    }
    
    public void getWaveformsByPoint(long pointId, LoadListCallback<WaveformDataEntity> callback) {
        executorService.execute(() -> {
            List<WaveformDataEntity> waveforms = waveformDao.getWaveformsByPointId(pointId);
            if (callback != null) {
                callback.onResult(waveforms);
            }
        });
    }
    
    // ========== 设备监控相关 ==========
    
    public void saveMonitor(DeviceMonitorEntity monitor, SaveCallback<Long> callback) {
        executorService.execute(() -> {
            long id = monitorDao.insert(monitor);
            if (callback != null) {
                callback.onSuccess(id);
            }
        });
    }
    
    public void getMonitorsByPoint(long pointId, LoadListCallback<DeviceMonitorEntity> callback) {
        executorService.execute(() -> {
            List<DeviceMonitorEntity> monitors = monitorDao.getMonitorsByPointId(pointId);
            if (callback != null) {
                callback.onResult(monitors);
            }
        });
    }
    
    public interface LoadObjectCallback<T> {
        void onResult(T object);
    }
}
