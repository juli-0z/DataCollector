package cn.zjl.datacollector.sync.request;

import android.content.Context;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.net.api.request.SyncRequest;
import cn.zjl.datacollector.util.AppSettings;
import cn.zjl.datacollector.util.WaveformCodec;

/**
 * 将本地工程库中的测点数据转换为后端 /receive/android 接口要求的 JSON 请求体。
 *
 * <p>后端当前约定：points[] 中的每一个对象都会生成一条样本；同一个测点多次采集时，
 * 可以重复使用同一个 pointCode。这个工厂会按 data_sample.startTime 将同一测点下的
 * 波形记录拆分为多个原始样本，并分别生成 points[] 元素。</p>
 */
public final class SinglePointSyncRequestFactory {

    /** 判断测点号是否可视为整数的误差范围，避免 float 存储带来的极小误差。 */
    private static final float INTEGER_EPSILON = 0.0001f;
    /** 联调阶段固定上传通过值，避免后端质量页因本地模拟数据缺失而显示空或 0。 */
    private static final double UPLOAD_QUALITY_PASS_VALUE = 100d;
    /** 后端把 timestamp 视为采集时间，因此上传时统一伪造为上传时刻前 30 秒。 */
    private static final long UPLOAD_CAPTURE_TIME_OFFSET_MS = 30_000L;

    private final Context context;
    private final SimpleDateFormat monitorTimestampFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public SinglePointSyncRequestFactory(Context context) {
        this.context = context.getApplicationContext();
    }

    public SyncRequest build(ProjectEntity project,
                             SurveyLineEntity line,
                             DataRepository.PointData pointData) {
        // 请求组装阶段直接校验核心对象，避免错误数据继续进入网络层后才暴露。
        if (project == null) {
            throw new IllegalStateException(context.getString(R.string.error_sync_missing_project_data));
        }
        if (line == null) {
            throw new IllegalStateException(context.getString(R.string.error_sync_missing_line_data));
        }
        if (pointData == null || pointData.point == null) {
            throw new IllegalStateException(context.getString(R.string.error_sync_invalid_point_number));
        }

        MeasurementPointEntity point = pointData.point;
        Long remoteProjectId = requireRemoteProjectId(project);
        // 测线编号来自测点真实所属测线，统一按后端示例保留一位小数，如 2.0。
        String lineCode = formatLineCode(line.getName());

        SyncRequest request = new SyncRequest();
        request.batchNo = buildBatchNo(project, point);
        request.deviceId = AppSettings.getSyncDeviceId(context);
        request.projectId = remoteProjectId;
        request.projectCode = buildProjectCode(remoteProjectId);
        request.projectName = resolveProjectName(project, remoteProjectId);
        request.engineeringCode = resolveEngineeringCode(project);
        request.lineCode = lineCode;
        request.taskName = buildTaskName(lineCode);
        request.points.addAll(buildPointPayloads(point, pointData));
        return request;
    }

    private List<SyncRequest.PointPayload> buildPointPayloads(MeasurementPointEntity point,
                                                              DataRepository.PointData pointData) {
        // 同一测点可能多次采集。后端当前是一个 point 对象生成一条 sample，
        // 因此这里按采集会话拆分，而不是把多次采集的波形塞进同一个 point.waveforms。
        Map<Long, List<WaveformDataEntity>> sessions = groupWaveformsBySession(pointData.waveforms);
        if (sessions.isEmpty()) {
            throw new IllegalStateException(context.getString(R.string.error_sync_missing_waveforms));
        }

        List<SyncRequest.PointPayload> payloads = new ArrayList<>();
        for (Map.Entry<Long, List<WaveformDataEntity>> entry : sessions.entrySet()) {
            SyncRequest.PointPayload pointPayload = buildBasePointPayload(point, pointData.parameters);
            pointPayload.monitor = buildMonitor(selectMonitorForSession(pointData.monitors, entry.getKey()));
            pointPayload.waveforms.addAll(buildWaveforms(entry.getValue()));
            if (!pointPayload.waveforms.isEmpty()) {
                payloads.add(pointPayload);
            }
        }
        if (payloads.isEmpty()) {
            throw new IllegalStateException(context.getString(R.string.error_sync_missing_waveforms));
        }
        return payloads;
    }

    private SyncRequest.PointPayload buildBasePointPayload(MeasurementPointEntity point,
                                                           CollectionParameterEntity parameters) {
        // 多个样本属于同一测点时，pointCode/pointNumber 可以重复；每个 point 对象只代表一条原始样本。
        SyncRequest.PointPayload pointPayload = new SyncRequest.PointPayload();
        pointPayload.pointCode = formatNumericCode(point.getName());
        pointPayload.pointNumber = requireInteger(point.getName());
        pointPayload.latitude = finite(point.getLatitude());
        pointPayload.longitude = finite(point.getLongitude());
        pointPayload.altitude = finite(point.getAltitude());
        pointPayload.elevation = finite(point.getAltitude());
        pointPayload.parameter = buildParameter(parameters);
        return pointPayload;
    }

    private SyncRequest.ParameterPayload buildParameter(CollectionParameterEntity parameters) {
        if (parameters == null) {
            return null;
        }
        // 本地参数字段名与后端字段名不完全一致，这里集中做一次映射。
        SyncRequest.ParameterPayload payload = new SyncRequest.ParameterPayload();
        payload.sendCurrent = finite(parameters.getTransmitCurrent());
        payload.samplingFrequency = parameters.getSampleFrequency() > 0 ? parameters.getSampleFrequency() : null;
        payload.collectionCount = parameters.getCollectionCount() > 0 ? parameters.getCollectionCount() : null;
        payload.samplingTime = finite(parameters.getSampleTime());
        payload.poleDistance = finite(parameters.getElectrodeDistance());
        payload.coilDirection = emptyToNull(parameters.getTransmitterDirection());
        return payload;
    }

    private SyncRequest.MonitorPayload buildMonitor(DeviceMonitorEntity monitor) {
        // 即使本地没有监控记录，也必须上传 monitor 对象，避免后端质量页电量为空、GPS 为 0。
        SyncRequest.MonitorPayload payload = new SyncRequest.MonitorPayload();
        payload.batteryVoltage = UPLOAD_QUALITY_PASS_VALUE;
        payload.batteryLevel = UPLOAD_QUALITY_PASS_VALUE;
        payload.gpsAccuracy = UPLOAD_QUALITY_PASS_VALUE;
        if (monitor != null) {
            payload.current = finite(monitor.getCurrent());
            payload.temperature = finite(monitor.getTemperature());
            payload.signalStrength = Math.round(monitor.getSignalStrength());
        }
        payload.timestamp = monitorTimestampFormat.format(
                new Date(System.currentTimeMillis() - UPLOAD_CAPTURE_TIME_OFFSET_MS));
        return payload;
    }

    private List<SyncRequest.WaveformPayload> buildWaveforms(List<WaveformDataEntity> waveforms) {
        List<SyncRequest.WaveformPayload> payloads = new ArrayList<>();
        if (waveforms == null) {
            return payloads;
        }
        for (WaveformDataEntity waveform : waveforms) {
            // 后端固定约定：1=Send，2=Off，3=Recv。
            appendWaveform(payloads, 1,
                    WaveformCodec.extractSendTimeAxis(waveform, WaveformCodec.extractSendValues(waveform).length),
                    WaveformCodec.extractSendValues(waveform));
            appendWaveform(payloads, 2,
                    WaveformCodec.extractOffTimeAxis(waveform, WaveformCodec.extractOffValues(waveform).length),
                    WaveformCodec.extractOffValues(waveform));
            appendWaveform(payloads, 3,
                    WaveformCodec.extractRecvTimeAxis(waveform),
                    WaveformCodec.extractRecvValues(waveform));
        }
        return payloads;
    }

    private void appendWaveform(List<SyncRequest.WaveformPayload> payloads,
                                int waveformType,
                                float[] timeAxis,
                                float[] values) {
        // 时间轴和值数组必须一一对应；长度不一致时取较短长度，避免上传非法 JSON 数组。
        int count = Math.min(timeAxis != null ? timeAxis.length : 0, values != null ? values.length : 0);
        if (count <= 0) {
            return;
        }
        SyncRequest.WaveformPayload payload = new SyncRequest.WaveformPayload();
        payload.waveformType = waveformType;
        for (int i = 0; i < count; i++) {
            float timeValue = timeAxis[i];
            if (waveformType == 3 && timeValue <= 0f) {
                // Recv 时间轴后端建议按 us 且全部大于 0，遇到 0 或负数时做最小修正。
                timeValue = Math.max(1f, i + 1f);
            }
            payload.timeSeries.add((double) timeValue);
            payload.voltageSeries.add((double) values[i]);
        }
        payloads.add(payload);
    }

    private Map<Long, List<WaveformDataEntity>> groupWaveformsBySession(List<WaveformDataEntity> waveforms) {
        // data_sample.startTime 相同的一组记录视为同一次采集会话。
        Map<Long, List<WaveformDataEntity>> sessions = new LinkedHashMap<>();
        if (waveforms == null || waveforms.isEmpty()) {
            return sessions;
        }

        List<WaveformDataEntity> sorted = new ArrayList<>(waveforms);
        Collections.sort(sorted, (left, right) -> {
            int timeCompare = Long.compare(left.getStartTime(), right.getStartTime());
            if (timeCompare != 0) {
                return timeCompare;
            }
            int typeCompare = Integer.compare(left.getType(), right.getType());
            if (typeCompare != 0) {
                return typeCompare;
            }
            return Long.compare(left.getId(), right.getId());
        });

        int fallbackIndex = 0;
        for (WaveformDataEntity waveform : sorted) {
            long sessionKey = waveform.getStartTime() > 0L ? waveform.getStartTime() : Long.MIN_VALUE + fallbackIndex++;
            List<WaveformDataEntity> sessionWaveforms = sessions.get(sessionKey);
            if (sessionWaveforms == null) {
                sessionWaveforms = new ArrayList<>();
                sessions.put(sessionKey, sessionWaveforms);
            }
            sessionWaveforms.add(waveform);
        }
        return sessions;
    }

    private DeviceMonitorEntity selectMonitorForSession(List<DeviceMonitorEntity> monitors, long sessionTime) {
        // 监控表没有显式 sessionId，只能按时间选择离当前波形会话最近的一条监控记录。
        if (monitors == null || monitors.isEmpty()) {
            return null;
        }
        DeviceMonitorEntity fallback = monitors.get(monitors.size() - 1);
        DeviceMonitorEntity closest = null;
        long closestDistance = Long.MAX_VALUE;
        for (DeviceMonitorEntity monitor : monitors) {
            long timestamp = monitor.getDeviceTimestamp() > 0L ? monitor.getDeviceTimestamp() : monitor.getTimestamp();
            if (timestamp <= 0L || sessionTime <= 0L) {
                continue;
            }
            long distance = Math.abs(timestamp - sessionTime);
            if (distance < closestDistance) {
                closest = monitor;
                closestDistance = distance;
            }
        }
        return closest == null ? fallback : closest;
    }

    private String buildBatchNo(ProjectEntity project, MeasurementPointEntity point) {
        // batchNo 需要相对稳定，避免同一测点反复上传时每次都生成完全不可追踪的新批次。
        long anchorTime = point.getCollectionTime() > 0L ? point.getCollectionTime() : point.getCreatedAt();
        return String.format(Locale.US, "AND-%tY%<tm%<td-%d-%d", new Date(anchorTime), project.getId(), point.getId());
    }

    private String buildProjectCode(Long projectId) {
        if (projectId == null || projectId <= 0L) {
            return null;
        }
        return String.format(Locale.US, "PRJ-%03d", projectId);
    }

    private String resolveProjectName(ProjectEntity project, Long projectId) {
        String primary = emptyToNull(project.getName());
        if (primary != null) {
            return primary;
        }
        if (projectId != null && projectId > 0L) {
            return "测试项目" + projectId;
        }
        return emptyToNull(project.getDatabaseName());
    }

    private String resolveEngineeringCode(ProjectEntity project) {
        String configured = emptyToNull(AppSettings.getProjectSyncEngineeringCode(context, project.getDatabaseName()));
        if (configured != null) {
            return configured;
        }
        String primary = emptyToNull(project.getName());
        if (primary != null) {
            return primary;
        }
        String fallback = emptyToNull(project.getDatabaseName());
        if (fallback != null) {
            return fallback;
        }
        return "ENG-" + project.getId();
    }

    private String buildTaskName(String lineCode) {
        String safeLineCode = emptyToNull(lineCode);
        if (safeLineCode == null) {
            return null;
        }
        return "测线" + safeLineCode + "任务";
    }

    private Long requireRemoteProjectId(ProjectEntity project) {
        // 服务端 projectId 来自上传页绑定配置，不能使用本地 Room 自增工程 ID。
        Long remoteProjectId = AppSettings.getProjectSyncRemoteProjectId(context, project.getDatabaseName());
        if (remoteProjectId != null) {
            return remoteProjectId;
        }
        throw new IllegalStateException(context.getString(R.string.error_sync_missing_remote_project_id));
    }

    private Integer requireInteger(float value) {
        // 后端 pointNumber 当前按整数处理，小数测点号只应放在 pointCode 中表达。
        if (!Float.isFinite(value)) {
            throw new IllegalStateException(context.getString(R.string.error_sync_invalid_point_number));
        }
        int rounded = Math.round(value);
        if (Math.abs(value - rounded) > INTEGER_EPSILON) {
            throw new IllegalStateException(context.getString(R.string.error_sync_invalid_point_number));
        }
        return rounded;
    }

    private String formatNumericCode(float value) {
        if (!Float.isFinite(value)) {
            return "";
        }
        return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
    }

    private String formatLineCode(float value) {
        // 后端示例使用 “2.0” 这类格式；真实测线编号统一保留一位小数上传。
        if (!Float.isFinite(value)) {
            return "";
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private Double finite(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private Double finite(float value) {
        return Float.isFinite(value) ? (double) value : null;
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
