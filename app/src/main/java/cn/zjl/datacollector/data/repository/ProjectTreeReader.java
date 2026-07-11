package cn.zjl.datacollector.data.repository;

/**
 * 阅读提示：数据仓库类：把 DAO 的底层读写封装成界面和业务层更容易调用的数据操作。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.data.dao.CollectionParameterDao;
import cn.zjl.datacollector.data.dao.DeviceMonitorDao;
import cn.zjl.datacollector.data.dao.MeasurementPointDao;
import cn.zjl.datacollector.data.dao.ProjectDao;
import cn.zjl.datacollector.data.dao.SurveyLineDao;
import cn.zjl.datacollector.data.dao.WaveformDao;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;

/**
 * 负责读取工程树、测点树以及回放所需的聚合数据。
 */
final class ProjectTreeReader {

    private final ProjectDao projectDao;
    private final SurveyLineDao lineDao;
    private final MeasurementPointDao pointDao;
    private final CollectionParameterDao parameterDao;
    private final WaveformDao waveformDao;
    private final DeviceMonitorDao monitorDao;

    ProjectTreeReader(ProjectDao projectDao,
                      SurveyLineDao lineDao,
                      MeasurementPointDao pointDao,
                      CollectionParameterDao parameterDao,
                      WaveformDao waveformDao,
                      DeviceMonitorDao monitorDao) {
        this.projectDao = projectDao;
        this.lineDao = lineDao;
        this.pointDao = pointDao;
        this.parameterDao = parameterDao;
        this.waveformDao = waveformDao;
        this.monitorDao = monitorDao;
    }

    DataRepository.PointData loadPointData(long pointId) {
        DataRepository.PointData data = new DataRepository.PointData();
        data.point = pointDao.getPointById(pointId);
        data.parameters = parameterDao.getLatestParametersByPointId(pointId);
        data.waveforms = waveformDao.getWaveformsByPointId(pointId);
        data.monitors = monitorDao.getMonitorsByPointId(pointId);
        return data;
    }

    List<DataRepository.PointSummary> getPointSummaries() {
        List<DataRepository.PointSummary> summaries = new ArrayList<>();
        long projectId = requireProjectId();
        List<SurveyLineEntity> lines = lineDao.getSurveyLinesByProjectId(projectId);
        for (SurveyLineEntity line : lines) {
            List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(line.getId());
            for (MeasurementPointEntity point : points) {
                DataRepository.PointSummary summary = new DataRepository.PointSummary();
                summary.line = line;
                summary.point = point;
                summary.waveformRowCount = waveformDao.countByPointId(point.getId());
                CollectionParameterEntity parameter = parameterDao.getLatestParametersByPointId(point.getId());
                summary.collectionCount = parameter != null
                        ? parameter.getCollectionCount()
                        : Math.max(1, summary.waveformRowCount / 3);
                summaries.add(summary);
            }
        }
        return summaries;
    }

    DataRepository.ProjectTreeSummary getProjectTree() {
        DataRepository.ProjectTreeSummary tree = new DataRepository.ProjectTreeSummary();
        tree.project = projectDao.getFirstProject();
        List<SurveyLineEntity> lines = tree.project != null
                ? lineDao.getSurveyLinesByProjectId(tree.project.getId())
                : lineDao.getAllSurveyLines();
        for (SurveyLineEntity line : lines) {
            DataRepository.LineTreeSummary lineTree = new DataRepository.LineTreeSummary();
            lineTree.line = line;
            List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(line.getId());
            for (MeasurementPointEntity point : points) {
                DataRepository.PointTreeSummary pointTree = new DataRepository.PointTreeSummary();
                pointTree.point = point;
                pointTree.sessions = buildSessionSummaries(waveformDao.getWaveformsByPointId(point.getId()));
                lineTree.points.add(pointTree);
                tree.pointCount++;
            }
            tree.lines.add(lineTree);
        }
        return tree;
    }

    List<WaveformDataEntity> getAllWaveformsByProject() {
        List<WaveformDataEntity> result = new ArrayList<>();
        long projectId = requireProjectId();
        List<SurveyLineEntity> lines = lineDao.getSurveyLinesByProjectId(projectId);
        for (SurveyLineEntity line : lines) {
            List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(line.getId());
            for (MeasurementPointEntity point : points) {
                result.addAll(waveformDao.getWaveformsByPointId(point.getId()));
            }
        }
        return result;
    }

    List<WaveformDataEntity> getAllWaveformsByLine(long lineId) {
        List<WaveformDataEntity> result = new ArrayList<>();
        List<MeasurementPointEntity> points = pointDao.getPointsBySurveyLineId(lineId);
        for (MeasurementPointEntity point : points) {
            result.addAll(waveformDao.getWaveformsByPointId(point.getId()));
        }
        return result;
    }

    long requireProjectId() {
        ProjectEntity project = projectDao.getFirstProject();
        return project != null ? project.getId() : 0L;
    }

    /**
     * 兼容两种存储方式：
     * 1. 每次采集一行内同时带 Recv/Send/Off；
     * 2. 每次采集拆成三行，通过 startTime 归组。
     */
    private List<DataRepository.CollectionSessionSummary> buildSessionSummaries(List<WaveformDataEntity> waveforms) {
        List<DataRepository.CollectionSessionSummary> sessions = new ArrayList<>();
        if (waveforms == null || waveforms.isEmpty()) {
            return sessions;
        }

        boolean rowContainsFullWaveforms = false;
        for (WaveformDataEntity waveform : waveforms) {
            int channelCount = 0;
            if (waveform.getDataRecv() != null && waveform.getDataRecv().length > 0) {
                channelCount++;
            }
            if (waveform.getDataSend() != null && waveform.getDataSend().length > 0) {
                channelCount++;
            }
            if (waveform.getDataSoff() != null && waveform.getDataSoff().length > 0) {
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
                DataRepository.CollectionSessionSummary session = new DataRepository.CollectionSessionSummary();
                session.sessionKey = waveform.getId();
                session.startTime = waveform.getStartTime();
                session.collectionIndex = i + 1;
                session.waveformCount = 1;
                sessions.add(session);
            }
            return sessions;
        }

        long currentStartTime = Long.MIN_VALUE;
        DataRepository.CollectionSessionSummary currentSession = null;
        for (WaveformDataEntity waveform : waveforms) {
            if (currentSession == null || waveform.getStartTime() != currentStartTime) {
                currentStartTime = waveform.getStartTime();
                currentSession = new DataRepository.CollectionSessionSummary();
                currentSession.sessionKey = waveform.getStartTime();
                currentSession.startTime = waveform.getStartTime();
                currentSession.collectionIndex = sessions.size() + 1;
                currentSession.waveformCount = 0;
                sessions.add(currentSession);
            }
            currentSession.waveformCount++;
        }
        return sessions;
    }
}
