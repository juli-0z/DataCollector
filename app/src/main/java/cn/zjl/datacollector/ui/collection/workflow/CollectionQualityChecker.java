package cn.zjl.datacollector.ui.collection.workflow;

/**
 * 阅读提示：采集业务流程模块代码：负责连接、质检、保存和参数沿用等采集前后流程。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.util.AppSettings;

/**
 * 对当前待保存的采集结果做轻量、可解释的基础质检。
 */
public class CollectionQualityChecker {

    private final Context context;

    public CollectionQualityChecker(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public CollectionQualityCheckResult evaluate(@Nullable float[] recvValues,
                                                 @Nullable float[] sendValues,
                                                 @Nullable float[] offValues,
                                                 @Nullable DeviceMonitorEntity monitor,
                                                 @Nullable CollectionParameterEntity parameters) {
        AppSettings.CollectionQualityConfig config = AppSettings.getCollectionQualityConfig(context);
        int recvSampleCount = effectiveLength(recvValues);
        int sendSampleCount = effectiveLength(sendValues);
        int offSampleCount = effectiveLength(offValues);

        List<String> issues = new ArrayList<>();
        boolean suggestSave = true;
        boolean hasAnyWaveform = recvSampleCount > 0 || sendSampleCount > 0 || offSampleCount > 0;

        if (!hasAnyWaveform) {
            issues.add(context.getString(R.string.judge_issue_waveform_missing));
            suggestSave = false;
        } else if (recvSampleCount <= 0) {
            issues.add(context.getString(R.string.judge_issue_recv_missing));
            suggestSave = false;
        }

        if (recvSampleCount > 0 && recvSampleCount < config.minRecvPoints) {
            issues.add(context.getString(
                    R.string.judge_issue_recv_too_short,
                    recvSampleCount,
                    config.minRecvPoints));
            suggestSave = false;
        }

        float recvAmplitude = maxAbs(recvValues, recvSampleCount);
        if (recvSampleCount > 0 && recvAmplitude < config.minRecvAmplitude) {
            issues.add(context.getString(
                    R.string.judge_issue_recv_flat,
                    formatNumber(recvAmplitude, 6),
                    formatNumber(config.minRecvAmplitude, 6)));
            suggestSave = false;
        }

        if (sendSampleCount <= 0) {
            issues.add(context.getString(R.string.judge_issue_send_missing));
        }
        if (offSampleCount <= 0) {
            issues.add(context.getString(R.string.judge_issue_off_missing));
        }

        if (validateParameters(parameters, issues)) {
            suggestSave = false;
        }
        if (validateMonitor(monitor, issues, config)) {
            suggestSave = false;
        }

        return new CollectionQualityCheckResult(
                suggestSave,
                config.blockSaveOnFailure,
                recvSampleCount,
                sendSampleCount,
                offSampleCount,
                monitor != null,
                issues);
    }

    private boolean validateParameters(@Nullable CollectionParameterEntity parameters,
                                       @NonNull List<String> issues) {
        boolean blockingIssue = false;
        if (parameters == null) {
            issues.add(context.getString(R.string.judge_issue_parameter_missing));
            return true;
        }
        if (!Float.isFinite(parameters.getTransmitCurrent()) || parameters.getTransmitCurrent() <= 0f) {
            issues.add(context.getString(R.string.judge_issue_transmit_current_invalid));
            blockingIssue = true;
        }
        if (parameters.getSampleFrequency() <= 0) {
            issues.add(context.getString(R.string.judge_issue_sample_frequency_invalid));
            blockingIssue = true;
        }
        if (parameters.getCollectionCount() <= 0) {
            issues.add(context.getString(R.string.judge_issue_collection_count_invalid));
            blockingIssue = true;
        }
        if (!Float.isFinite(parameters.getSampleTime()) || parameters.getSampleTime() <= 0f) {
            issues.add(context.getString(R.string.judge_issue_sample_time_invalid));
            blockingIssue = true;
        }
        return blockingIssue;
    }

    private boolean validateMonitor(@Nullable DeviceMonitorEntity monitor,
                                    @NonNull List<String> issues,
                                    @NonNull AppSettings.CollectionQualityConfig config) {
        boolean blockingIssue = false;
        if (monitor == null) {
            issues.add(context.getString(R.string.judge_issue_monitor_missing));
            return false;
        }
        if (!Float.isFinite(monitor.getBatteryVoltage())
                || monitor.getBatteryVoltage() <= config.minBatteryVoltage) {
            issues.add(context.getString(
                    R.string.judge_issue_battery_invalid,
                    formatNumber(monitor.getBatteryVoltage(), 2),
                    formatNumber(config.minBatteryVoltage, 2)));
            blockingIssue = true;
        }
        if (!Float.isFinite(monitor.getCurrent()) || monitor.getCurrent() < 0f) {
            issues.add(context.getString(
                    R.string.judge_issue_monitor_current_invalid,
                    formatNumber(monitor.getCurrent(), 2)));
            blockingIssue = true;
        }
        if (!Float.isFinite(monitor.getGpsAccuracy())
                || monitor.getGpsAccuracy() < config.minGpsAccuracy) {
            issues.add(context.getString(
                    R.string.judge_issue_gps_accuracy_invalid,
                    formatNumber(monitor.getGpsAccuracy(), 1),
                    formatNumber(config.minGpsAccuracy, 1)));
            blockingIssue = true;
        }
        if (!Float.isFinite(monitor.getTemperature())
                || monitor.getTemperature() < -40f
                || monitor.getTemperature() > config.maxTemperature) {
            issues.add(context.getString(
                    R.string.judge_issue_temperature_invalid,
                    formatNumber(monitor.getTemperature(), 1),
                    formatNumber(config.maxTemperature, 1)));
            blockingIssue = true;
        }
        return blockingIssue;
    }

    private int effectiveLength(@Nullable float[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        for (int i = values.length - 1; i >= 0; i--) {
            float value = values[i];
            if (Float.isFinite(value) && Math.abs(value) > 1.0e-9f) {
                return i + 1;
            }
        }
        return 0;
    }

    private float maxAbs(@Nullable float[] values, int length) {
        if (values == null || values.length == 0 || length <= 0) {
            return 0f;
        }
        int safeLength = Math.min(length, values.length);
        float max = 0f;
        for (int i = 0; i < safeLength; i++) {
            float value = values[i];
            if (!Float.isFinite(value)) {
                continue;
            }
            max = Math.max(max, Math.abs(value));
        }
        return max;
    }

    @NonNull
    private String formatNumber(float value, int decimals) {
        if (!Float.isFinite(value)) {
            return context.getString(R.string.default_status_value);
        }
        return String.format(Locale.getDefault(), "%." + decimals + "f", value);
    }
}
