package cn.zjl.datacollector.data.repository;

import android.content.Context;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    private final ExecutorService executorService;

    public DataRepository(Context context, String databaseName) {
        this.database = AppDatabase.getInstance(context.getApplicationContext(), databaseName);
        this.projectDao = database.projectDao();
        this.lineDao = database.surveyLineDao();
        this.pointDao = database.measurementPointDao();
        this.parameterDao = database.collectionParameterDao();
        this.waveformDao = database.waveformDao();
        this.monitorDao = database.deviceMonitorDao();
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
            line.name = 1f;
            line.note = "默认测线";
            line.projectId = projectId;
            line.type = 0;
            line.use = 1;
            line.createdAt = System.currentTimeMillis();
            line.updatedAt = line.createdAt;
            long lineId = lineDao.insert(line);
            line.id = lineId;
            callback.onSuccess(line);
        });
    }

    public void createSurveyLine(long projectId, float name, String note, SaveCallback<Long> callback) {
        executorService.execute(() -> {
            try {
                SurveyLineEntity line = new SurveyLineEntity();
                line.projectId = projectId;
                line.name = name;
                line.note = note;
                line.type = 0;
                line.use = 1;
                line.createdAt = System.currentTimeMillis();
                line.updatedAt = line.createdAt;
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
            List<PointSummary> summaries = new ArrayList<>();
            long projectId = requireProjectId();
            List<SurveyLineEntity> lines = lineDao.getSurveyLinesByProjectId(projectId);
            for (SurveyLineEntity line : lines) {
                List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(line.id);
                for (MeasurementPointEntity point : points) {
                    PointSummary summary = new PointSummary();
                    summary.line = line;
                    summary.point = point;
                    summary.waveformRowCount = waveformDao.countByPointId(point.id);
                    CollectionParameterEntity parameter = parameterDao.getLatestParametersByPointId(point.id);
                    summary.collectionCount = parameter != null ? parameter.collectionCount : Math.max(1, summary.waveformRowCount / 3);
                    summaries.add(summary);
                }
            }
            if (callback != null) {
                callback.onResult(summaries);
            }
        });
    }

    public void getProjectTree(LoadObjectCallback<ProjectTreeSummary> callback) {
        executorService.execute(() -> {
            ProjectTreeSummary tree = new ProjectTreeSummary();
            tree.project = projectDao.getFirstProject();
            List<SurveyLineEntity> lines = tree.project != null
                    ? lineDao.getSurveyLinesByProjectId(tree.project.id)
                    : lineDao.getAllSurveyLines();
            for (SurveyLineEntity line : lines) {
                LineTreeSummary lineTree = new LineTreeSummary();
                lineTree.line = line;
                List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(line.id);
                for (MeasurementPointEntity point : points) {
                    PointTreeSummary pointTree = new PointTreeSummary();
                    pointTree.point = point;
                    pointTree.sessions = buildSessionSummaries(waveformDao.getWaveformsByPointId(point.id));
                    lineTree.points.add(pointTree);
                    tree.pointCount++;
                }
                tree.lines.add(lineTree);
            }
            if (callback != null) {
                callback.onResult(tree);
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
                if (point.name < currentPointNumber) {
                    if (target == null || point.name > target.name) {
                        target = point;
                    }
                }
            }
            if (callback != null) {
                callback.onResult(target == null ? null : parameterDao.getLatestParametersByPointId(target.id));
            }
        });
    }

    public void getAllDataByPoint(long pointId, LoadObjectCallback<PointData> callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(loadPointData(pointId));
            }
        });
    }

    public PointData getAllDataByPointSync(long pointId) {
        return loadPointData(pointId);
    }

    public void getAllWaveformsByProject(LoadListCallback<WaveformDataEntity> callback) {
        executorService.execute(() -> {
            List<WaveformDataEntity> result = new ArrayList<>();
            long projectId = requireProjectId();
            List<SurveyLineEntity> lines = lineDao.getSurveyLinesByProjectId(projectId);
            for (SurveyLineEntity line : lines) {
                List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(line.id);
                for (MeasurementPointEntity point : points) {
                    result.addAll(waveformDao.getWaveformsByPointId(point.id));
                }
            }
            if (callback != null) {
                callback.onResult(result);
            }
        });
    }

    public void getAllWaveformsByLine(long lineId, LoadListCallback<WaveformDataEntity> callback) {
        executorService.execute(() -> {
            List<WaveformDataEntity> result = new ArrayList<>();
            List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(lineId);
            for (MeasurementPointEntity point : points) {
                result.addAll(waveformDao.getWaveformsByPointId(point.id));
            }
            if (callback != null) {
                callback.onResult(result);
            }
        });
    }

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
                MeasurementPointEntity[] holder = new MeasurementPointEntity[1];
                database.runInTransaction(() -> {
                    long now = System.currentTimeMillis();
                    MeasurementPointEntity point = pointDao.getPointByNumber(surveyLineId, pointNumber);
                    boolean pointExists = point != null;
                    if (point == null) {
                        point = new MeasurementPointEntity();
                        point.dataLineId = surveyLineId;
                        point.createdAt = now;
                    }
                    point.name = pointNumber;
                    point.use = qualified ? 1 : 0;
                    point.type = pointType;
                    point.note = judgeNote != null && !judgeNote.isEmpty() ? judgeNote : pointNote;
                    point.status = STATUS_SAVED;
                    point.isQualified = qualified;
                    point.isSynced = false;
                    point.collectionTime = now;
                    point.latitude = latitude;
                    point.longitude = longitude;
                    point.altitude = altitude;
                    point.syncError = null;
                    point.updatedAt = now;
                    if (pointExists) {
                        pointDao.update(point);
                    } else {
                        long pointId = pointDao.insert(point);
                        point.id = pointId;
                    }

                    long pointId = point.id;

                    if (parameters != null) {
                        parameters.id = 0L;
                        parameters.pointId = pointId;
                        parameters.createdAt = now;
                        parameterDao.insert(parameters);
                    }

                    if (monitor != null) {
                        monitor.id = 0L;
                        monitor.pointId = pointId;
                        if (monitor.timestamp == 0L) {
                            monitor.timestamp = now;
                        }
                        monitorDao.insert(monitor);
                    }

                    // 同一测点的后续采集按“追加会话”写入，保留历史采样次数。
                    waveformDao.insertAll(buildWaveforms(pointId, parameters, timeAxis, recvValues, sendValues, offValues, now));
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

    public void markPointAsSynced(long pointId) {
        executorService.execute(() -> pointDao.updateSyncState(pointId, true, null, System.currentTimeMillis()));
    }

    public void markPointSyncError(long pointId, String error) {
        executorService.execute(() -> pointDao.updateSyncState(pointId, false, error, System.currentTimeMillis()));
    }

    public void updatePointStatus(long pointId, int status) {
        executorService.execute(() -> pointDao.updateStatus(pointId, status, System.currentTimeMillis()));
    }

    private PointData loadPointData(long pointId) {
        PointData data = new PointData();
        data.point = pointDao.getPointById(pointId);
        data.parameters = parameterDao.getLatestParametersByPointId(pointId);
        data.waveforms = waveformDao.getWaveformsByPointId(pointId);
        data.monitors = monitorDao.getMonitorsByPointId(pointId);
        return data;
    }

    private List<CollectionSessionSummary> buildSessionSummaries(List<WaveformDataEntity> waveforms) {
        List<CollectionSessionSummary> sessions = new ArrayList<>();
        if (waveforms == null || waveforms.isEmpty()) {
            return sessions;
        }

        boolean rowContainsFullWaveforms = false;
        for (WaveformDataEntity waveform : waveforms) {
            int channelCount = 0;
            if (waveform.dataRecv != null && waveform.dataRecv.length > 0) {
                channelCount++;
            }
            if (waveform.dataSend != null && waveform.dataSend.length > 0) {
                channelCount++;
            }
            if (waveform.dataSoff != null && waveform.dataSoff.length > 0) {
                channelCount++;
            }
            if (channelCount > 1) {
                rowContainsFullWaveforms = true;
                break;
            }
        }

        if (rowContainsFullWaveforms) {
            for (int i = 0; i < waveforms.size(); i++) {
                WaveformDataEntity waveform = waveforms.get(i);
                CollectionSessionSummary session = new CollectionSessionSummary();
                session.sessionKey = waveform.id;
                session.startTime = waveform.startTime;
                session.collectionIndex = i + 1;
                session.waveformCount = 1;
                sessions.add(session);
            }
            return sessions;
        }

        long currentStartTime = Long.MIN_VALUE;
        CollectionSessionSummary currentSession = null;
        for (WaveformDataEntity waveform : waveforms) {
            if (currentSession == null || waveform.startTime != currentStartTime) {
                currentStartTime = waveform.startTime;
                currentSession = new CollectionSessionSummary();
                currentSession.sessionKey = waveform.startTime;
                currentSession.startTime = waveform.startTime;
                currentSession.collectionIndex = sessions.size() + 1;
                currentSession.waveformCount = 0;
                sessions.add(currentSession);
            }
            currentSession.waveformCount++;
        }
        return sessions;
    }

    private List<WaveformDataEntity> buildWaveforms(long pointId,
                                                    CollectionParameterEntity parameters,
                                                    float[] timeAxis,
                                                    float[] recvValues,
                                                    float[] sendValues,
                                                    float[] offValues,
                                                    long timestamp) {
        List<WaveformDataEntity> waveforms = new ArrayList<>();
        waveforms.add(buildWaveform(pointId, 0, "Recv", timeAxis, recvValues, null, null, parameters, timestamp));
        waveforms.add(buildWaveform(pointId, 1, "Send", timeAxis, null, sendValues, null, parameters, timestamp));
        waveforms.add(buildWaveform(pointId, 2, "Off", timeAxis, null, null, offValues, parameters, timestamp));
        return waveforms;
    }

    private WaveformDataEntity buildWaveform(long pointId,
                                             int type,
                                             String note,
                                             float[] timeAxis,
                                             float[] recvValues,
                                             float[] sendValues,
                                             float[] offValues,
                                             CollectionParameterEntity parameters,
                                             long timestamp) {
        float sendFrequencyHz = resolveSendFrequencyHz(parameters);
        float recvFrequencyHz = resolveRecvFrequencyHz(parameters);
        float waveformSampleRateHz = resolveWaveformSampleRateHz(parameters);
        float[] recvWindowLengths = buildRecvWindowLengths(timeAxis, parameters != null ? parameters.sampleTime : 0f);

        WaveformDataEntity waveform = new WaveformDataEntity();
        waveform.dataPointId = pointId;
        waveform.type = type;
        waveform.note = note;
        waveform.startTime = timestamp;
        // 当前表单中的 collectionCount 已对应 Data_Sample.PERIOD（叠加/发送周期）。
        waveform.period = resolvePeriod(parameters);
        waveform.dataRecvPos = encodeFloatArray(timeAxis);
        waveform.dataRecvLen = encodeFloatArray(recvWindowLengths);
        waveform.dataRecv = recvValues != null ? encodeFloatArray(recvValues) : null;
        waveform.dataSend = sendValues != null ? encodeFloatArray(sendValues) : null;
        waveform.dataSoff = offValues != null ? encodeFloatArray(offValues) : null;
        // 当前表单语义：
        // transmitCurrent -> SendFs（发送频率）
        // sampleFrequency -> RecvFs（接收采样频率）
        // sampleTime(单位 us) -> SampleSendFs / SampleOffFs 的步长换算依据
        waveform.recvFs = recvFrequencyHz;
        waveform.sendFs = sendFrequencyHz;
        waveform.simpleSendFs = waveformSampleRateHz;
        waveform.simpleOffFs = waveformSampleRateHz;
        waveform.use = 1;
        waveform.createdAt = timestamp;
        return waveform;
    }

    private int resolvePeriod(CollectionParameterEntity parameters) {
        if (parameters == null || parameters.collectionCount <= 0) {
            return 1;
        }
        return parameters.collectionCount;
    }

    private float resolveSendFrequencyHz(CollectionParameterEntity parameters) {
        if (parameters == null || parameters.transmitCurrent <= 0f) {
            return 0f;
        }
        return parameters.transmitCurrent;
    }

    private float resolveRecvFrequencyHz(CollectionParameterEntity parameters) {
        if (parameters == null || parameters.sampleFrequency <= 0) {
            return 0f;
        }
        return parameters.sampleFrequency;
    }

    private float resolveWaveformSampleRateHz(CollectionParameterEntity parameters) {
        if (parameters == null) {
            return 0f;
        }
        // sampleTime 约定为 us，数据库中的 SampleSendFs / SampleOffFs 需要的是 Hz。
        if (parameters.sampleTime > 0f) {
            return 1_000_000f / parameters.sampleTime;
        }
        if (parameters.sampleFrequency > 0) {
            return parameters.sampleFrequency;
        }
        return 0f;
    }

    private float[] buildRecvWindowLengths(float[] timeAxis, float fallbackSampleTimeUs) {
        if (timeAxis == null || timeAxis.length == 0) {
            return new float[0];
        }

        float[] values = new float[timeAxis.length];
        if (timeAxis.length == 1) {
            values[0] = sanitizeWindowLength(fallbackSampleTimeUs, 0f);
            return values;
        }

        for (int i = 0; i < timeAxis.length - 1; i++) {
            float delta = timeAxis[i + 1] - timeAxis[i];
            values[i] = sanitizeWindowLength(delta, fallbackSampleTimeUs);
        }
        values[timeAxis.length - 1] = sanitizeWindowLength(values[timeAxis.length - 2], fallbackSampleTimeUs);
        return values;
    }

    private float sanitizeWindowLength(float candidate, float fallback) {
        if (Float.isFinite(candidate) && candidate > 0f) {
            return candidate;
        }
        if (Float.isFinite(fallback) && fallback > 0f) {
            return fallback;
        }
        return 0f;
    }

    private byte[] encodeFloatArray(float[] values) {
        if (values == null || values.length == 0) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 8).order(ByteOrder.BIG_ENDIAN);
        for (float value : values) {
            buffer.putDouble(value);
        }
        return buffer.array();
    }

    private long requireProjectId() {
        ProjectEntity project = projectDao.getFirstProject();
        return project != null ? project.id : 0L;
    }

    private void touchProject(long now) {
        ProjectEntity project = projectDao.getFirstProject();
        if (project != null) {
            project.updatedAt = now;
            projectDao.update(project);
        }
    }
}
