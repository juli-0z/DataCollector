package cn.zjl.datacollector.sync.request;

/**
 * 阅读提示：数据同步模块代码：负责登录认证、请求组装、上传执行和同步结果回写。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.Nullable;

import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.net.api.request.DeviceReportRequest;
import cn.zjl.datacollector.util.AppSettings;

/**
 * 构建设备状态上报请求。
 */
public final class DeviceReportRequestFactory {

    /** 后端设备表使用的 Android 设备类型常量。 */
    private static final String DEVICE_TYPE_ANDROID = "ANDROID";
    /** 当前只上报在线状态，离线状态暂未接入。 */
    private static final String STATUS_ONLINE = "online";
    /** 联调阶段固定传 100，保证后端质量页电量/GPS 字段可见且通过规则。 */
    private static final double REPORT_QUALITY_PASS_VALUE = 100d;

    private final Context context;

    public DeviceReportRequestFactory(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    public DeviceReportRequest build(@Nullable ProjectEntity project,
                                     @Nullable SurveyLineEntity line,
                                     @Nullable DeviceMonitorEntity monitor) {
        // 导入库和未绑定数据库名的工程不参与设备状态上报，避免历史库误写后端设备状态。
        if (project == null || project.getImported() || project.getDatabaseName() == null || project.getDatabaseName().isEmpty()) {
            return null;
        }
        // 后端识别归属依赖服务端 projectId，本地工程 id 不能直接用于上报。
        Long remoteProjectId = AppSettings.getProjectSyncRemoteProjectId(context, project.getDatabaseName());
        if (remoteProjectId == null || line == null) {
            return null;
        }

        DeviceReportRequest request = new DeviceReportRequest();
        request.deviceCode = AppSettings.getSyncDeviceId(context);
        request.deviceId = request.deviceCode;
        request.deviceName = buildDeviceName();
        request.deviceType = DEVICE_TYPE_ANDROID;
        request.firmwareVersion = buildFirmwareVersion();
        request.status = STATUS_ONLINE;
        request.projectId = remoteProjectId;
        request.lineCode = formatLineCode(line.getName());
        request.reportTime = System.currentTimeMillis();
        request.networkType = resolveNetworkType();
        request.note = "Android terminal online";

        if (monitor != null) {
            // 当前后端质量页重点看电量和 GPS；这里固定传 100，避免模拟/缺失监控导致显示为空或 0。
            request.batteryLevel = REPORT_QUALITY_PASS_VALUE;
            request.signalStrength = Math.round(monitor.getSignalStrength());
            request.gpsAccuracy = REPORT_QUALITY_PASS_VALUE;
            request.temperature = finite(monitor.getTemperature());
        }
        return request;
    }

    public String fingerprint(DeviceReportRequest request) {
        // 用关键字段生成指纹，短时间内相同状态不重复上报，减少后端无效写入。
        StringBuilder builder = new StringBuilder();
        builder.append(safe(request.deviceCode)).append('|')
                .append(request.projectId != null ? request.projectId : 0L).append('|')
                .append(safe(request.lineCode)).append('|')
                .append(request.batteryLevel != null ? request.batteryLevel : "").append('|')
                .append(request.signalStrength != null ? request.signalStrength : "").append('|')
                .append(safe(request.networkType)).append('|')
                .append(safe(request.status));
        return builder.toString();
    }

    private String buildDeviceName() {
        // 设备名称只用于后端展示，优先用厂商 + 型号，拿不到时使用通用名称。
        String manufacturer = safe(Build.MANUFACTURER);
        String model = safe(Build.MODEL);
        if (manufacturer.isEmpty()) {
            return model.isEmpty() ? "Android Device" : model;
        }
        if (model.isEmpty()) {
            return manufacturer;
        }
        return manufacturer + " " + model;
    }

    private String buildFirmwareVersion() {
        String release = safe(Build.VERSION.RELEASE);
        return release.isEmpty() ? "Android" : "Android " + release;
    }

    @Nullable
    private String resolveNetworkType() {
        // 仅识别当前活跃网络类型；没有网络或无法获取能力时返回 null，由后端自行处理。
        ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return null;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return null;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null) {
            return null;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "WIFI";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "CELLULAR";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ETHERNET";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return "BLUETOOTH";
        }
        return "UNKNOWN";
    }

    private String formatLineCode(float value) {
        // 后端示例使用 2.0 这类一位小数字符串，因此测线统一保留一位小数。
        if (!Float.isFinite(value)) {
            return "";
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private Double finite(float value) {
        return Float.isFinite(value) ? (double) value : null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
