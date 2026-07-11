package cn.zjl.datacollector.ui.collection.panel;

/**
 * 阅读提示：采集操作面板代码：负责参数输入、连接弹窗、质检设置和界面控件渲染。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.net.tcp.TcpClientManager;
import cn.zjl.datacollector.net.wifi.DeviceWifiState;
import cn.zjl.datacollector.ui.collection.screen.CollectionViewModel;

/**
 * 统一负责采集页主要状态区和监控区的渲染。
 */
public class CollectionUiRenderer {

    private final Context context;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private final TextView textConnectionStatus;
    private final TextView textWifiStatus;
    private final TextView textCollectionProgress;
    private final TextView textTemperature;
    private final TextView textBreadcrumb;
    private final TextView textMainChartTitle;
    private final TextView textToggleSecondaryCharts;
    private final TextView textStatusFrequency;
    private final TextView textStatusPeriod;
    private final TextView textStatusRepeat;
    private final TextView textStatusCurrent;
    private final TextView textStatusOff;
    private final TextView textMonitorProtocol;
    private final TextView textMonitorBattery;
    private final TextView textMonitorSignal;
    private final TextView textMonitorGps;
    private final TextView textMonitorDataRate;
    private final TextView textMonitorPacketLoss;
    private final TextView textMonitorSystemStatus;
    private final TextView textMonitorLastUpdate;
    private final EditText editPointNumber;
    private final Button btnConnect;
    private final Button btnStartCollection;
    private final Button btnStopCollection;
    private final Button btnSave;
    private final Button btnNextPoint;
    private final Button btnApplyPreviousParams;
    private final Button btnParameterTemplates;
    private final View cardConnection;
    private final View cardMonitor;
    private final View cardParameters;
    private final View cardActions;
    private final View layoutSecondaryCharts;
    private final MaterialCardView cardCurrentPoint;

    private DeviceMonitorEntity lastMonitor;
    private CollectionViewModel.ChartDisplayState lastChartState;

    public CollectionUiRenderer(
            Context context,
            TextView textConnectionStatus,
            TextView textWifiStatus,
            TextView textCollectionProgress,
            TextView textTemperature,
            TextView textBreadcrumb,
            TextView textMainChartTitle,
            TextView textToggleSecondaryCharts,
            TextView textStatusFrequency,
            TextView textStatusPeriod,
            TextView textStatusRepeat,
            TextView textStatusCurrent,
            TextView textStatusOff,
            TextView textMonitorProtocol,
            TextView textMonitorBattery,
            TextView textMonitorSignal,
            TextView textMonitorGps,
            TextView textMonitorDataRate,
            TextView textMonitorPacketLoss,
            TextView textMonitorSystemStatus,
            TextView textMonitorLastUpdate,
            EditText editPointNumber,
            Button btnConnect,
            Button btnStartCollection,
            Button btnStopCollection,
            Button btnSave,
            Button btnNextPoint,
            Button btnApplyPreviousParams,
            Button btnParameterTemplates,
            View cardConnection,
            View cardMonitor,
            View cardParameters,
            View cardActions,
            View layoutSecondaryCharts,
            MaterialCardView cardCurrentPoint) {
        this.context = context;
        this.textConnectionStatus = textConnectionStatus;
        this.textWifiStatus = textWifiStatus;
        this.textCollectionProgress = textCollectionProgress;
        this.textTemperature = textTemperature;
        this.textBreadcrumb = textBreadcrumb;
        this.textMainChartTitle = textMainChartTitle;
        this.textToggleSecondaryCharts = textToggleSecondaryCharts;
        this.textStatusFrequency = textStatusFrequency;
        this.textStatusPeriod = textStatusPeriod;
        this.textStatusRepeat = textStatusRepeat;
        this.textStatusCurrent = textStatusCurrent;
        this.textStatusOff = textStatusOff;
        this.textMonitorProtocol = textMonitorProtocol;
        this.textMonitorBattery = textMonitorBattery;
        this.textMonitorSignal = textMonitorSignal;
        this.textMonitorGps = textMonitorGps;
        this.textMonitorDataRate = textMonitorDataRate;
        this.textMonitorPacketLoss = textMonitorPacketLoss;
        this.textMonitorSystemStatus = textMonitorSystemStatus;
        this.textMonitorLastUpdate = textMonitorLastUpdate;
        this.editPointNumber = editPointNumber;
        this.btnConnect = btnConnect;
        this.btnStartCollection = btnStartCollection;
        this.btnStopCollection = btnStopCollection;
        this.btnSave = btnSave;
        this.btnNextPoint = btnNextPoint;
        this.btnApplyPreviousParams = btnApplyPreviousParams;
        this.btnParameterTemplates = btnParameterTemplates;
        this.cardConnection = cardConnection;
        this.cardMonitor = cardMonitor;
        this.cardParameters = cardParameters;
        this.cardActions = cardActions;
        this.layoutSecondaryCharts = layoutSecondaryCharts;
        this.cardCurrentPoint = cardCurrentPoint;
    }

    public void renderCollectionControlsVisible(boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        cardConnection.setVisibility(state);
        cardMonitor.setVisibility(state);
        cardParameters.setVisibility(state);
        cardActions.setVisibility(state);
    }

    public void renderSecondaryCharts(boolean expanded) {
        if (layoutSecondaryCharts == null || textToggleSecondaryCharts == null) {
            return;
        }
        layoutSecondaryCharts.setVisibility(expanded ? View.VISIBLE : View.GONE);
        textToggleSecondaryCharts.setText(
                expanded
                        ? R.string.action_collapse_secondary_charts
                        : R.string.action_expand_secondary_charts);
        textToggleSecondaryCharts.setSelected(expanded);
    }

    public void renderBreadcrumb(@Nullable String breadcrumb) {
        if (breadcrumb == null || breadcrumb.trim().isEmpty()) {
            textBreadcrumb.setText(R.string.breadcrumb_empty);
            return;
        }
        textBreadcrumb.setText(context.getString(R.string.breadcrumb_format, breadcrumb));
    }

    public void renderMainChartTitle(@Nullable String title) {
        if (title == null || title.trim().isEmpty()) {
            textMainChartTitle.setText(R.string.label_recv);
            return;
        }
        textMainChartTitle.setText(title);
    }

    public void renderMonitor(@Nullable DeviceMonitorEntity monitor) {
        lastMonitor = monitor;
        if (monitor == null) {
            textTemperature.setText(R.string.default_degree_celsius);
            renderMonitorDetails(null);
            renderStatusTiles(lastChartState, null);
            return;
        }

        textTemperature.setText(context.getString(R.string.monitor_temperature_value, monitor.getTemperature()));
        renderMonitorDetails(monitor);
        renderStatusTiles(lastChartState, monitor);
    }

    public void renderWaveformStatus(@Nullable CollectionViewModel.ChartDisplayState state) {
        lastChartState = state;
        renderStatusTiles(state, lastMonitor);
    }

    public void renderActionState(@Nullable CollectionViewModel.ActionState state) {
        if (state == null) {
            return;
        }
        textConnectionStatus.setText(state.connectionStatusRes);
        textCollectionProgress.setText(state.progressStatusRes);
        renderConnectionStatusAppearance(state.connectionStatusRes);
        btnConnect.setText(state.connectButtonRes);
        btnConnect.setEnabled(state.connectEnabled);
        btnStartCollection.setEnabled(state.startEnabled);
        btnStopCollection.setEnabled(state.stopEnabled);
        btnSave.setEnabled(state.saveEnabled);
        btnNextPoint.setEnabled(state.nextEnabled);
        if (btnApplyPreviousParams != null) {
            btnApplyPreviousParams.setEnabled(state.nextEnabled);
        }
        if (btnParameterTemplates != null) {
            btnParameterTemplates.setEnabled(state.nextEnabled);
        }
    }

    public void renderWifiState(@Nullable DeviceWifiState state) {
        if (textWifiStatus == null) {
            return;
        }
        int statusRes = resolveWifiStateRes(state);
        textWifiStatus.setText(statusRes);
        renderWifiStateAppearance(state);
    }

    public void renderCurrentPointHighlight(boolean hasFocus) {
        if (cardCurrentPoint == null) {
            return;
        }
        renderCurrentPointCursorVisible(hasFocus);
        cardCurrentPoint.setStrokeColor(ContextCompat.getColor(
                context,
                hasFocus ? R.color.blue_primary : R.color.divider));
        cardCurrentPoint.setStrokeWidth(dpToPx(hasFocus ? 2 : 1));
    }

    public void renderCurrentPointCursorVisible(boolean visible) {
        if (editPointNumber != null) {
            editPointNumber.setCursorVisible(visible);
        }
    }

    private void renderMonitorDetails(@Nullable DeviceMonitorEntity monitor) {
        if (monitor == null) {
            textMonitorProtocol.setText(R.string.default_status_value);
            textMonitorBattery.setText(R.string.default_status_value);
            textMonitorSignal.setText(R.string.default_status_value);
            textMonitorGps.setText(R.string.default_status_value);
            textMonitorDataRate.setText(R.string.default_status_value);
            textMonitorPacketLoss.setText(R.string.default_status_value);
            textMonitorSystemStatus.setText(R.string.default_status_value);
            textMonitorLastUpdate.setText(R.string.default_status_value);
            return;
        }

        textMonitorProtocol.setText(
                TcpClientManager.ProtocolState.fromCode(monitor.getProtocolStateCode()).getDisplayName());
        textMonitorBattery.setText(formatBattery(monitor));
        textMonitorSignal.setText(formatSignal(monitor.getSignalStrength()));
        textMonitorGps.setText(formatGps(monitor.getGpsAccuracy()));
        textMonitorDataRate.setText(formatDataRate(monitor.getDataRate()));
        textMonitorPacketLoss.setText(formatPacketLoss(monitor.getPacketLoss()));
        textMonitorSystemStatus.setText(formatSystemStatus(monitor.getSystemStatus()));
        textMonitorLastUpdate.setText(formatTime(monitor.getDeviceTimestamp() > 0 ? monitor.getDeviceTimestamp() : monitor.getTimestamp()));
    }

    private void renderStatusTiles(@Nullable CollectionViewModel.ChartDisplayState state,
                                   @Nullable DeviceMonitorEntity monitor) {
        if (state == null) {
            textStatusFrequency.setText(R.string.default_status_value);
            textStatusPeriod.setText(R.string.default_status_value);
            textStatusRepeat.setText(R.string.default_status_value);
        } else {
            textStatusFrequency.setText(formatFrequency(state.recvFs));
            textStatusPeriod.setText(formatPeriod(state.period));
            textStatusRepeat.setText(formatRepeatCount(state.repeatCount));
        }

        if (monitor == null) {
            textStatusCurrent.setText(R.string.default_status_value);
            textStatusOff.setText(R.string.default_status_value);
        } else {
            textStatusCurrent.setText(formatCurrent(monitor));
            textStatusOff.setText(formatOffTime(monitor.getOffTime()));
        }
    }

    private void renderConnectionStatusAppearance(int statusRes) {
        int backgroundRes;
        int textColorRes;
        if (statusRes == R.string.connection_status_connected) {
            backgroundRes = R.drawable.bg_status_active;
            textColorRes = R.color.status_active_fg;
        } else if (statusRes == R.string.connection_status_connecting) {
            backgroundRes = R.drawable.bg_status_pending;
            textColorRes = R.color.status_pending_fg;
        } else {
            backgroundRes = R.drawable.bg_status_idle;
            textColorRes = R.color.status_idle_fg;
        }
        textConnectionStatus.setBackgroundResource(backgroundRes);
        textConnectionStatus.setTextColor(ContextCompat.getColor(context, textColorRes));
    }

    private void renderWifiStateAppearance(@Nullable DeviceWifiState state) {
        int backgroundRes;
        int textColorRes;
        if (state == DeviceWifiState.CONNECTED) {
            backgroundRes = R.drawable.bg_status_active;
            textColorRes = R.color.status_active_fg;
        } else if (state == DeviceWifiState.CONNECTING) {
            backgroundRes = R.drawable.bg_status_pending;
            textColorRes = R.color.status_pending_fg;
        } else {
            backgroundRes = R.drawable.bg_status_idle;
            textColorRes = R.color.status_idle_fg;
        }
        textWifiStatus.setBackgroundResource(backgroundRes);
        textWifiStatus.setTextColor(ContextCompat.getColor(context, textColorRes));
    }

    private int resolveWifiStateRes(@Nullable DeviceWifiState state) {
        if (state == null) {
            return R.string.wifi_status_idle;
        }
        switch (state) {
            case CONNECTING:
                return R.string.wifi_status_connecting;
            case CONNECTED:
                return R.string.wifi_status_connected;
            case DISCONNECTED:
                return R.string.wifi_status_disconnected;
            case PERMISSION_REQUIRED:
                return R.string.wifi_status_permission_required;
            case UNAVAILABLE:
                return R.string.wifi_status_unavailable;
            case LOST:
                return R.string.wifi_status_lost;
            case ERROR:
                return R.string.wifi_status_error;
            case UNSUPPORTED:
                return R.string.wifi_status_unsupported;
            case IDLE:
            default:
                return R.string.wifi_status_idle;
        }
    }

    private String formatFrequency(float recvFs) {
        if (!Float.isFinite(recvFs) || recvFs <= 0f) {
            return context.getString(R.string.default_status_value);
        }
        return context.getString(R.string.summary_unit_hz, formatNumber(recvFs));
    }

    private String formatPeriod(int period) {
        return period > 0 ? context.getString(R.string.summary_unit_count, String.valueOf(period))
                : context.getString(R.string.default_status_value);
    }

    private String formatRepeatCount(int repeatCount) {
        return repeatCount > 0
                ? context.getString(R.string.summary_unit_count, String.valueOf(repeatCount))
                : context.getString(R.string.default_status_value);
    }

    private String formatCurrent(DeviceMonitorEntity monitor) {
        float value = monitor.getSendCurrent() > 0f ? monitor.getSendCurrent() : monitor.getCurrent();
        if (!Float.isFinite(value) || value <= 0f) {
            return context.getString(R.string.default_status_value);
        }
        return context.getString(R.string.summary_unit_ampere, formatNumber(value));
    }

    private String formatOffTime(float offTime) {
        if (!Float.isFinite(offTime) || offTime <= 0f) {
            return context.getString(R.string.default_status_value);
        }
        return context.getString(R.string.summary_unit_us, formatNumber(offTime));
    }

    private String formatBattery(DeviceMonitorEntity monitor) {
        if (Float.isFinite(monitor.getBatteryVoltage()) && monitor.getBatteryVoltage() > 0f
                && Float.isFinite(monitor.getBatteryLevel()) && monitor.getBatteryLevel() > 0f) {
            return context.getString(
                    R.string.monitor_battery_value_with_level,
                    monitor.getBatteryVoltage(),
                    Math.round(monitor.getBatteryLevel()));
        }
        if (Float.isFinite(monitor.getBatteryVoltage()) && monitor.getBatteryVoltage() > 0f) {
            return context.getString(R.string.monitor_battery_value, monitor.getBatteryVoltage());
        }
        if (Float.isFinite(monitor.getBatteryLevel()) && monitor.getBatteryLevel() > 0f) {
            return context.getString(R.string.monitor_battery_level_only, Math.round(monitor.getBatteryLevel()));
        }
        return context.getString(R.string.default_status_value);
    }

    private String formatSignal(float signalStrength) {
        if (!Float.isFinite(signalStrength) || signalStrength == 0f) {
            return context.getString(R.string.default_status_value);
        }
        return context.getString(R.string.monitor_signal_value, Math.round(signalStrength));
    }

    private String formatGps(float gpsAccuracy) {
        if (!Float.isFinite(gpsAccuracy) || gpsAccuracy <= 0f) {
            return context.getString(R.string.default_status_value);
        }
        return context.getString(R.string.monitor_gps_accuracy_value, gpsAccuracy);
    }

    private String formatDataRate(float dataRate) {
        if (!Float.isFinite(dataRate) || dataRate <= 0f) {
            return context.getString(R.string.default_status_value);
        }
        return context.getString(R.string.monitor_data_rate_value, dataRate);
    }

    private String formatPacketLoss(float packetLoss) {
        if (!Float.isFinite(packetLoss) || packetLoss < 0f) {
            return context.getString(R.string.default_status_value);
        }
        return context.getString(R.string.monitor_packet_loss_value, packetLoss);
    }

    private String formatSystemStatus(int systemStatus) {
        switch (systemStatus) {
            case 1:
                return context.getString(R.string.monitor_system_status_ready);
            case 2:
                return context.getString(R.string.monitor_system_status_collecting);
            case 3:
                return context.getString(R.string.monitor_system_status_stopping);
            case 4:
                return context.getString(R.string.monitor_system_status_error);
            case 0:
            default:
                return context.getString(R.string.monitor_system_status_idle);
        }
    }

    private String formatTime(long timeMillis) {
        if (timeMillis <= 0L) {
            return context.getString(R.string.default_status_value);
        }
        return timeFormat.format(new Date(timeMillis));
    }

    private String formatNumber(float value) {
        return value == (int) value ? String.valueOf((int) value) : String.format(Locale.US, "%.1f", value);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
