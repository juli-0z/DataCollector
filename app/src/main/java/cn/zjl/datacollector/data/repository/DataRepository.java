package cn.zjl.datacollector.data.repository;

/**
 * 阅读提示：单个工程数据库仓库：封装工程内测线、测点、参数、监控、波形和同步状态的读写。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.zjl.datacollector.data.AppDatabase;
import cn.zjl.datacollector.data.dao.CollectionParameterDao;
import cn.zjl.datacollector.data.dao.DeviceMonitorDao;
import cn.zjl.datacollector.data.dao.MeasurementPointDao;
import cn.zjl.datacollector.data.dao.ProjectDao;
import cn.zjl.datacollector.data.dao.SurveyLineDao;
import cn.zjl.datacollector.data.dao.WaveformDao;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;

public class DataRepository {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_COLLECTING = 1;
    public static final int STATUS_SAVED = 2;
    public static final int STATUS_SYNCED = 3;

    private final AppDatabase database;
    private final ProjectDao projectDao;
    private final SurveyLineDao lineDao;
    private final MeasurementPointDao pointDao;
    private final CollectionParameterDao parameterDao;
    private final WaveformDao waveformDao;
    private final DeviceMonitorDao monitorDao;
    private final ProjectTreeReader projectTreeReader;
    private final WaveformStorageHelper waveformStorageHelper;
    private final ExecutorService executorService;

    public DataRepository(Context context, String databaseName) {
        this.database = AppDatabase.getInstance(context.getApplicationContext(), databaseName);
        this.projectDao = database.projectDao();
        this.lineDao = database.surveyLineDao();
        this.pointDao = database.measurementPointDao();
        this.parameterDao = database.collectionParameterDao();
        this.waveformDao = database.waveformDao();
        this.monitorDao = database.deviceMonitorDao();
        this.projectTreeReader = new ProjectTreeReader(projectDao, lineDao, pointDao, parameterDao, waveformDao, monitorDao);
        this.waveformStorageHelper = new WaveformStorageHelper();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public interface SaveCallback<T> {
        void onSuccess(T result);

        void onError(String error);
    }

    public interface LoadListCallback<T> {
        void onResult(List<T> list);
    }

    public interface LoadObjectCallback<T> {
        void onResult(T object);
    }

    public static class PointData {
        public MeasurementPointEntity point;
        public CollectionParameterEntity parameters;
        public List<WaveformDataEntity> waveforms;
        public List<DeviceMonitorEntity> monitors;
    }

    public static class PointSummary {
        public SurveyLineEntity line;
        public MeasurementPointEntity point;
        public int waveformRowCount;
        public int collectionCount;
    }

    public static class CollectionSessionSummary {
        public long sessionKey;
        public long startTime;
        public int collectionIndex;
        public int waveformCount;
    }

    public static class PointTreeSummary {
        public MeasurementPointEntity point;
        public List<CollectionSessionSummary> sessions = new ArrayList<>();
    }

    public static class LineTreeSummary {
        public SurveyLineEntity line;
        public List<PointTreeSummary> points = new ArrayList<>();
    }

    public static class ProjectTreeSummary {
        public ProjectEntity project;
        public List<LineTreeSummary> lines = new ArrayList<>();
        public int pointCount;
    }

    public void getProject(LoadObjectCallback<ProjectEntity> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(projectDao.getFirstProject());
            }
        });
    }

    public ProjectEntity getProjectSync() {
        return projectDao.getFirstProject();
    }

    public void getSurveyLinesByProject(long projectId, LoadListCallback<SurveyLineEntity> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(lineDao.getSurveyLinesByProjectId(projectId));
            }
        });
    }

    public void ensureDefaultSurveyLine(long projectId, SaveCallback<SurveyLineEntity> callback) {
        executorService.execute(() -> {
            List<SurveyLineEntity> lines = lineDao.getSurveyLinesByProjectId(projectId);
            if (lines != null && !lines.isEmpty()) {
                callback.onSuccess(lines.get(0));
                return;
            }
            SurveyLineEntity line = new SurveyLineEntity();
            line.setName(1f);
            line.setNote("默认测线");
            line.setProjectId(projectId);
            line.setType(0);
            line.setUse(1);
            line.setCreatedAt(System.currentTimeMillis());
            line.setUpdatedAt(line.getCreatedAt());
            long lineId = lineDao.insert(line);
            line.setId(lineId);
            callback.onSuccess(line);
        });
    }

    public void createSurveyLine(long projectId, float name, String note, SaveCallback<Long> callback) {
        executorService.execute(() -> {
            try {
                SurveyLineEntity line = new SurveyLineEntity();
                line.setProjectId(projectId);
                line.setName(name);
                line.setNote(note);
                line.setType(0);
                line.setUse(1);
                line.setCreatedAt(System.currentTimeMillis());
                line.setUpdatedAt(line.getCreatedAt());
                callback.onSuccess(lineDao.insert(line));
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public void getPointsBySurveyLine(long surveyLineId, LoadListCallback<MeasurementPointEntity> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(pointDao.getPointsBySurveyLineId(surveyLineId));
            }
        });
    }

    public void getProjectPoints(long ignoredProjectId, LoadListCallback<MeasurementPointEntity> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(pointDao.getAllPoints());
            }
        });
    }

    public void getPointSummaries(LoadListCallback<PointSummary> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(projectTreeReader.getPointSummaries());
            }
        });
    }

    public void getProjectTree(LoadObjectCallback<ProjectTreeSummary> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(projectTreeReader.getProjectTree());
            }
        });
    }

    public void getLatestParameters(long pointId, LoadObjectCallback<CollectionParameterEntity> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(parameterDao.getLatestParametersByPointId(pointId));
            }
        });
    }

    public void getPreviousPointParameters(long surveyLineId, float currentPointNumber, LoadObjectCallback<CollectionParameterEntity> callback) {
        executorService.execute(() -> {
            List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(surveyLineId);
            MeasurementPointEntity target = null;
            for (MeasurementPointEntity point : points) {
                if (point.getName() < currentPointNumber) {
                    if (target == null || point.getName() > target.getName()) {
                        target = point;
                    }
                }
            }
            if (callback != null) {
                callback.onResult(target == null ? null : parameterDao.getLatestParametersByPointId(target.getId()));
            }
        });
    }

    public void getAllDataByPoint(long pointId, LoadObjectCallback<PointData> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(projectTreeReader.loadPointData(pointId));
            }
        });
    }

    public PointData getAllDataByPointSync(long pointId) {
        return projectTreeReader.loadPointData(pointId);
    }

    public SurveyLineEntity getSurveyLineSync(long lineId) {
        return lineDao.getSurveyLineById(lineId);
    }

    public void getAllWaveformsByProject(LoadListCallback<WaveformDataEntity> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(projectTreeReader.getAllWaveformsByProject());
            }
        });
    }

    public void getAllWaveformsByLine(long lineId, LoadListCallback<WaveformDataEntity> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(projectTreeReader.getAllWaveformsByLine(lineId));
            }
        });
    }
    /**
     * 保存一次完整的采集会话。
     * <p>这是数据采集系统的核心写入方法，一次调用完成四级联写入：
     * <ol>
     *   <li><b>测点（MeasurementPoint）</b>——新建或更新，记录状态、位置、质量判定</li>
     *   <li><b>采集参数（CollectionParameter）</b>——追加写入，保留历史参数</li>
     *   <li><b>设备监控（DeviceMonitor）</b>——追加写入，记录电压/电流等</li>
     *   <li><b>波形数据（Waveform）</b>——批量追加，按时间轴拆分为逐行记录</li>
     * </ol>
     *
     * <p>线程安全：所有操作包裹在 Room 事务中，通过单线程池串行执行。
     *
     * <p>追加会话机制：同一测点号多次采集时，测点表仅更新状态（覆盖），
     * 参数/监控/波形表每次追加新记录，通过 {@code collectionTime} 区分不同会话。
     *
     * @param surveyLineId  所属测线 ID
     * @param pointNumber   测点号（如 1.0, 2.0, 3.5）
     * @param pointType     测点类型（常规/加密/校验等）
     * @param pointNote     测点备注（用户输入）
     * @param parameters    采集参数实体（包含采样率、增益、通道配置等）
     * @param timeAxis      时间轴数据
     * @param recvValues    接收通道幅值
     * @param sendValues    发送通道幅值
     * @param offValues     偏移通道幅值
     * @param monitor       设备监控数据（电压、电流、温度等）
     * @param latitude      纬度
     * @param longitude     经度
     * @param altitude      海拔高度（米）
     * @param qualified     质量判定：true=合格，false=不合格
     * @param judgeNote     质量判定备注（如"波形噪声过大"），优先级高于 pointNote
     * @param callback      回调：成功返回测点实体，失败返回错误信息
     */
    public void saveCollectionSession(long surveyLineId,
                                      float pointNumber,
                                      int pointType,
                                      String pointNote,
                                      CollectionParameterEntity parameters,
                                      float[] timeAxis,
                                      float[] recvValues,
                                      float[] sendValues,
                                      float[] offValues,
                                      DeviceMonitorEntity monitor,
                                      double latitude,
                                      double longitude,
                                      double altitude,
                                      boolean qualified,
                                      String judgeNote,
                                      SaveCallback<MeasurementPointEntity> callback) {
        executorService.execute(() -> {
            try {
                // 使用单元素数组绕过 lambda 的 effectively final 限制，
                // 使事务内部的 point 对象可以在事务结束后通过回调返回
                MeasurementPointEntity[] holder = new MeasurementPointEntity[1];
                database.runInTransaction(() -> {
                    // 统一时间戳：事务内所有记录的写入时间保持一致
                    long now = System.currentTimeMillis();
                    // === 第一步：查找或创建测点 ===
                    MeasurementPointEntity point = pointDao.getPointByNumber(surveyLineId, pointNumber);
                    boolean pointExists = point != null;
                    if (point == null) {
                        // 新测点：构造实体并设置创建时间
                        point = new MeasurementPointEntity();
                        point.setDataLineId(surveyLineId);
                        point.setCreatedAt(now);
                    }
                    // 填充测点字段（无论新建还是更新，以下字段均会覆盖）
                    point.setName(pointNumber);
                    point.setUse(qualified ? 1 : 0);          // 合格→启用，不合格→禁用
                    point.setType(pointType);
                    // 优先级：判定备注 > 用户原始备注
                    point.setNote(judgeNote != null && !judgeNote.isEmpty() ? judgeNote : pointNote);
                    point.setStatus(STATUS_SAVED);            // 采集完成，标记为已保存
                    point.setQualified(qualified);
                    point.setSynced(false);                 // 强制重置同步状态，确保新数据会被上传
                    point.setCollectionTime(now);             // 记录采集时间
                    point.setLatitude(latitude);
                    point.setLongitude(longitude);
                    point.setAltitude(altitude);
                    point.setSyncError(null);                 // 清除历史同步错误
                    point.setUpdatedAt(now);
                    if (pointExists) {
                        pointDao.update(point);              // 已存在 → UPDATE
                    } else {
                        long pointId = pointDao.insert(point);  // 新测点 → INSERT
                        point.setId(pointId);                  // 回填自增 ID
                    }

                    long pointId = point.getId();

                    // === 第二步：追加采集参数 ===
                    // 每次采集都作为新记录写入，id 置 0 确保走 INSERT 而非 UPDATE
                    if (parameters != null) {
                        parameters.setId(0L);
                        parameters.setPointId(pointId);
                        parameters.setCreatedAt(now);
                        parameterDao.insert(parameters);
                    }

                    if (monitor != null) {
                        monitor.setId(0L);
                        monitor.setPointId(pointId);
                        if (monitor.getTimestamp() == 0L) {
                            monitor.setTimestamp(now);
                        }
                        monitorDao.insert(monitor);
                    }

                    // 同一测点的后续采集按“追加会话”写入，保留历史采样次数。
                    waveformDao.insertAll(waveformStorageHelper.buildWaveforms(
                            pointId,
                            parameters,
                            timeAxis,
                            recvValues,
                            sendValues,
                            offValues,
                            now
                    ));
                    touchProject(now);
                    holder[0] = point;
                });
                callback.onSuccess(holder[0]);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public List<MeasurementPointEntity> getUnsyncedPointsSync() {
        return pointDao.getUnsyncedPoints();
    }

    public List<MeasurementPointEntity> getUploadablePointsSync() {
        return pointDao.getUploadablePoints();
    }

    public List<MeasurementPointEntity> getFailedUnsyncedPointsSync() {
        return pointDao.getFailedUnsyncedPoints();
    }

    public void markPointAsSynced(long pointId) {
        executorService.execute(() -> markPointAsSyncedSync(pointId));
    }

    public void markPointSyncError(long pointId, String error) {
        executorService.execute(() -> markPointSyncErrorSync(pointId, error));
    }

    public void markPointAsSyncedSync(long pointId) {
        pointDao.updateSyncState(pointId, STATUS_SYNCED, true, null, System.currentTimeMillis());
    }

    public void markPointSyncErrorSync(long pointId, String error) {
        pointDao.updateSyncState(pointId, STATUS_SAVED, false, error, System.currentTimeMillis());
    }

    public void updateProjectLastSyncedSync(long syncedAt) {
        ProjectEntity project = projectDao.getFirstProject();
        if (project == null) {
            return;
        }
        project.setLastSyncedAt(syncedAt);
        project.setUpdatedAt(syncedAt);
        projectDao.update(project);
    }

    public void updatePointStatus(long pointId, int status) {
        executorService.execute(() -> pointDao.updateStatus(pointId, status, System.currentTimeMillis()));
    }

    private void touchProject(long now) {
        ProjectEntity project = projectDao.getFirstProject();
        if (project != null) {
            project.setUpdatedAt(now);
            projectDao.update(project);
        }
    }
}
