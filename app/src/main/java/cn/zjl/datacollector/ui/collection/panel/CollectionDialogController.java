package cn.zjl.datacollector.ui.collection.panel;

/**
 * 阅读提示：采集操作面板代码：负责参数输入、连接弹窗、质检设置和界面控件渲染。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.text.InputType;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputLayout;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.ui.collection.selection.CollectionTreeHelper;
import cn.zjl.datacollector.ui.collection.workflow.CollectionQualityCheckResult;
import cn.zjl.datacollector.ui.log.OperationLogStore;
import cn.zjl.datacollector.ui.playback.PointListItem;
import cn.zjl.datacollector.util.AppSettings;

/**
 * 统一承接采集页里的弹窗交互，避免 Activity 同时承担页面编排和具体输入对话框实现。
 */
public class CollectionDialogController {

    private final AppCompatActivity activity;
    private final CollectionTreeHelper treeHelper;
    private final CollectionFormHelper formHelper;
    private final ActionHandler actionHandler;
    private final OperationLogStore operationLogStore;

    public CollectionDialogController(
            AppCompatActivity activity,
            CollectionTreeHelper treeHelper,
            CollectionFormHelper formHelper,
            ActionHandler actionHandler) {
        this.activity = activity;
        this.treeHelper = treeHelper;
        this.formHelper = formHelper;
        this.actionHandler = actionHandler;
        this.operationLogStore = new OperationLogStore(activity);
    }

    public void showJudgeDialog() {
        if (!actionHandler.canSavePendingResult()) {
            Toast.makeText(activity, activity.getString(R.string.toast_no_pending_result), Toast.LENGTH_SHORT).show();
            return;
        }

        CollectionParameterEntity parameters = formHelper.collectParameters();
        CollectionQualityCheckResult qualityCheckResult = actionHandler.evaluatePendingResult(parameters);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_data_judge)
                .setMessage(qualityCheckResult.buildDialogMessage(activity));

        if (qualityCheckResult.isSaveBlockedByPolicy()) {
            builder.setPositiveButton(R.string.action_recollect, (dialog, which) -> actionHandler.discardPendingResult())
                    .setNegativeButton(R.string.action_cancel, null)
                    .setNeutralButton(R.string.action_quality_check_settings, (dialog, which) ->
                            showQualityCheckSettingsDialog(null));
        } else {
            builder.setPositiveButton(R.string.action_save, (dialog, which) -> actionHandler.saveCurrentPoint(qualityCheckResult))
                    .setNegativeButton(R.string.action_recollect, (dialog, which) -> actionHandler.discardPendingResult())
                    .setNeutralButton(R.string.action_cancel, null);
        }
        builder.show();
    }

    public void showTcpDialog() {
        android.view.View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_device_connection, null);
        TextInputLayout layoutWifiSsid = dialogView.findViewById(R.id.layout_device_wifi_ssid);
        TextInputLayout layoutWifiPassword = dialogView.findViewById(R.id.layout_device_wifi_password);
        TextInputLayout layoutIp = dialogView.findViewById(R.id.layout_tcp_ip);
        TextInputLayout layoutPort = dialogView.findViewById(R.id.layout_tcp_port);
        EditText editWifiSsid = dialogView.findViewById(R.id.edit_device_wifi_ssid);
        EditText editWifiPassword = dialogView.findViewById(R.id.edit_device_wifi_password);
        EditText editIp = dialogView.findViewById(R.id.edit_tcp_ip);
        EditText editPort = dialogView.findViewById(R.id.edit_tcp_port);
        MaterialCheckBox checkboxAutoWifi = dialogView.findViewById(R.id.checkbox_device_wifi_auto_connect);
        MaterialCheckBox checkboxHiddenSsid = dialogView.findViewById(R.id.checkbox_device_wifi_hidden_ssid);
        MaterialCheckBox checkboxSimulation = dialogView.findViewById(R.id.checkbox_tcp_simulation);
        TextView textWifiHint = dialogView.findViewById(R.id.text_device_wifi_hint);
        TextView textSimulationHint = dialogView.findViewById(R.id.text_tcp_simulation_hint);
        editWifiSsid.setText(AppSettings.getDeviceWifiSsid(activity));
        editWifiPassword.setText(AppSettings.getDeviceWifiPassword(activity));
        editIp.setText(AppSettings.getTcpIp(activity));
        editPort.setText(String.valueOf(AppSettings.getTcpPort(activity)));
        checkboxAutoWifi.setChecked(AppSettings.isDeviceWifiAutoConnectEnabled(activity));
        checkboxHiddenSsid.setChecked(AppSettings.isDeviceWifiHiddenSsid(activity));
        checkboxSimulation.setChecked(AppSettings.isTcpSimulationEnabled(activity));
        applyDeviceFieldState(
                layoutWifiSsid,
                layoutWifiPassword,
                layoutIp,
                layoutPort,
                editWifiSsid,
                editWifiPassword,
                editIp,
                editPort,
                checkboxAutoWifi,
                checkboxHiddenSsid,
                textWifiHint,
                textSimulationHint,
                checkboxAutoWifi.isChecked(),
                checkboxSimulation.isChecked());
        checkboxAutoWifi.setOnCheckedChangeListener((buttonView, isChecked) ->
                applyDeviceFieldState(
                        layoutWifiSsid,
                        layoutWifiPassword,
                        layoutIp,
                        layoutPort,
                        editWifiSsid,
                        editWifiPassword,
                        editIp,
                        editPort,
                        checkboxAutoWifi,
                        checkboxHiddenSsid,
                        textWifiHint,
                        textSimulationHint,
                        isChecked,
                        checkboxSimulation.isChecked()));
        checkboxSimulation.setOnCheckedChangeListener((buttonView, isChecked) ->
                applyDeviceFieldState(
                        layoutWifiSsid,
                        layoutWifiPassword,
                        layoutIp,
                        layoutPort,
                        editWifiSsid,
                        editWifiPassword,
                        editIp,
                        editPort,
                        checkboxAutoWifi,
                        checkboxHiddenSsid,
                        textWifiHint,
                        textSimulationHint,
                        checkboxAutoWifi.isChecked(),
                        isChecked));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_device_connection_settings)
                .setView(dialogView)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    AppSettings.saveDeviceWifiConfig(
                            activity,
                            checkboxAutoWifi.isChecked(),
                            editWifiSsid.getText() == null ? "" : editWifiSsid.getText().toString(),
                            editWifiPassword.getText() == null ? "" : editWifiPassword.getText().toString(),
                            checkboxHiddenSsid.isChecked());
                    String ip = editIp.getText() == null ? "" : editIp.getText().toString().trim();
                    int port = formHelper.parseInt(editPort, 8080);
                    actionHandler.requestConnect(ip, port, checkboxSimulation.isChecked());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    public void showQualityCheckSettingsDialog(@Nullable String databaseName) {
        android.view.View dialogView = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_collection_quality_settings, null);
        MaterialCheckBox checkboxBlockSave = dialogView.findViewById(R.id.checkbox_quality_check_block_save);
        RadioGroup radioGroupProfile = dialogView.findViewById(R.id.radio_group_quality_profile);
        TextView textProfileSummary = dialogView.findViewById(R.id.text_quality_profile_summary);

        applyQualityCheckConfigToViews(
                AppSettings.getCollectionQualityConfig(activity),
                checkboxBlockSave,
                radioGroupProfile,
                textProfileSummary);
        radioGroupProfile.setOnCheckedChangeListener((group, checkedId) ->
                updateQualityProfileSummary(
                        textProfileSummary,
                        AppSettings.createCollectionQualityConfig(
                                resolveSelectedQualityProfile(radioGroupProfile),
                                checkboxBlockSave.isChecked())));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_quality_check_settings)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.action_reset_defaults, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                applyQualityCheckConfigToViews(
                        AppSettings.createDefaultCollectionQualityConfig(),
                        checkboxBlockSave,
                        radioGroupProfile,
                        textProfileSummary);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                AppSettings.CollectionQualityConfig config = AppSettings.createCollectionQualityConfig(
                        resolveSelectedQualityProfile(radioGroupProfile),
                        checkboxBlockSave.isChecked());
                AppSettings.saveCollectionQualityConfig(activity, config);
                operationLogStore.record(
                        OperationLogStore.CATEGORY_SETTINGS,
                        activity.getString(R.string.operation_log_title_save_quality_check_settings),
                        buildQualityCheckSettingsSummary(config),
                        databaseName);
                Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_quality_check_settings_saved),
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void applyDeviceFieldState(TextInputLayout layoutWifiSsid,
                                       TextInputLayout layoutWifiPassword,
                                       TextInputLayout layoutIp,
                                       TextInputLayout layoutPort,
                                       EditText editWifiSsid,
                                       EditText editWifiPassword,
                                       EditText editIp,
                                       EditText editPort,
                                       MaterialCheckBox checkboxAutoWifi,
                                       MaterialCheckBox checkboxHiddenSsid,
                                       TextView wifiHint,
                                       TextView simulationHint,
                                       boolean autoWifiEnabled,
                                       boolean simulationEnabled) {
        boolean enableRealDeviceFields = !simulationEnabled;
        boolean enableWifiFields = enableRealDeviceFields && autoWifiEnabled;
        if (checkboxAutoWifi != null) {
            checkboxAutoWifi.setEnabled(enableRealDeviceFields);
        }
        if (checkboxHiddenSsid != null) {
            checkboxHiddenSsid.setEnabled(enableWifiFields);
            checkboxHiddenSsid.setAlpha(enableWifiFields ? 1f : 0.55f);
        }
        if (layoutWifiSsid != null) {
            layoutWifiSsid.setEnabled(enableWifiFields);
            layoutWifiSsid.setAlpha(enableWifiFields ? 1f : 0.55f);
        }
        if (layoutWifiPassword != null) {
            layoutWifiPassword.setEnabled(enableWifiFields);
            layoutWifiPassword.setAlpha(enableWifiFields ? 1f : 0.55f);
        }
        if (layoutIp != null) {
            layoutIp.setEnabled(enableRealDeviceFields);
            layoutIp.setAlpha(enableRealDeviceFields ? 1f : 0.55f);
        }
        if (layoutPort != null) {
            layoutPort.setEnabled(enableRealDeviceFields);
            layoutPort.setAlpha(enableRealDeviceFields ? 1f : 0.55f);
        }
        if (editWifiSsid != null) {
            editWifiSsid.setEnabled(enableWifiFields);
        }
        if (editWifiPassword != null) {
            editWifiPassword.setEnabled(enableWifiFields);
        }
        if (editIp != null) {
            editIp.setEnabled(enableRealDeviceFields);
        }
        if (editPort != null) {
            editPort.setEnabled(enableRealDeviceFields);
        }
        if (wifiHint != null) {
            int wifiHintRes = simulationEnabled
                    ? R.string.text_device_wifi_hint_simulation
                    : (autoWifiEnabled
                    ? R.string.text_device_wifi_hint_enabled
                    : R.string.text_device_wifi_hint_disabled);
            wifiHint.setText(activity.getString(wifiHintRes));
        }
        if (simulationHint != null) {
            simulationHint.setText(activity.getString(simulationEnabled
                    ? R.string.text_tcp_simulation_hint_enabled
                    : R.string.text_tcp_simulation_hint));
        }
    }

    private void applyQualityCheckConfigToViews(@NonNull AppSettings.CollectionQualityConfig config,
                                                @NonNull MaterialCheckBox checkboxBlockSave,
                                                @NonNull RadioGroup radioGroupProfile,
                                                @NonNull TextView textProfileSummary) {
        checkboxBlockSave.setChecked(config.blockSaveOnFailure);
        int checkedId = config.profile == AppSettings.CollectionQualityProfile.RELAXED
                ? R.id.radio_quality_profile_relaxed
                : R.id.radio_quality_profile_standard;
        radioGroupProfile.check(checkedId);
        updateQualityProfileSummary(textProfileSummary, config);
    }

    @NonNull
    private AppSettings.CollectionQualityProfile resolveSelectedQualityProfile(
            @Nullable RadioGroup radioGroupProfile) {
        if (radioGroupProfile == null) {
            return AppSettings.CollectionQualityProfile.STANDARD;
        }
        return radioGroupProfile.getCheckedRadioButtonId() == R.id.radio_quality_profile_relaxed
                ? AppSettings.CollectionQualityProfile.RELAXED
                : AppSettings.CollectionQualityProfile.STANDARD;
    }

    private void updateQualityProfileSummary(@NonNull TextView textProfileSummary,
                                             @NonNull AppSettings.CollectionQualityConfig config) {
        textProfileSummary.setText(activity.getString(
                R.string.quality_check_profile_summary_format,
                qualityProfileLabel(config.profile),
                config.minRecvPoints,
                String.valueOf(config.minRecvAmplitude),
                formHelper.formatNumber(config.minBatteryVoltage),
                formHelper.formatNumber(config.minGpsAccuracy),
                formHelper.formatNumber(config.maxTemperature)));
    }

    @NonNull
    private String buildQualityCheckSettingsSummary(@NonNull AppSettings.CollectionQualityConfig config) {
        return activity.getString(
                R.string.operation_log_detail_quality_check_settings,
                qualityProfileLabel(config.profile),
                activity.getString(config.blockSaveOnFailure
                        ? R.string.quality_check_policy_block
                        : R.string.quality_check_policy_allow_manual_save),
                config.minRecvPoints,
                String.valueOf(config.minRecvAmplitude),
                formHelper.formatNumber(config.minBatteryVoltage),
                formHelper.formatNumber(config.minGpsAccuracy),
                formHelper.formatNumber(config.maxTemperature));
    }

    @NonNull
    private String qualityProfileLabel(@Nullable AppSettings.CollectionQualityProfile profile) {
        return activity.getString(profile == AppSettings.CollectionQualityProfile.RELAXED
                ? R.string.quality_check_profile_relaxed
                : R.string.quality_check_profile_standard);
    }

    public void showParameterTemplateDialog(@NonNull String databaseName) {
        String[] items = new String[]{
                activity.getString(R.string.collection_template_action_apply),
                activity.getString(R.string.collection_template_action_save)
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.collection_template_dialog_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showParameterTemplateSlotDialog(databaseName, false);
                    } else {
                        showParameterTemplateSlotDialog(databaseName, true);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    public void showLineSelectionDialog(@Nullable List<SurveyLineEntity> lines) {
        List<SurveyLineEntity> safeLines = lines == null ? new ArrayList<>() : lines;
        boolean readOnlyProject = actionHandler.isReadOnlyProject();
        int actionCount = readOnlyProject ? safeLines.size() : safeLines.size() + 1;
        String[] items = new String[actionCount];
        for (int i = 0; i < safeLines.size(); i++) {
            items[i] = treeHelper.buildLineTitle(safeLines.get(i).getName());
        }
        if (!readOnlyProject) {
            items[items.length - 1] = activity.getString(R.string.dialog_new_line_option);
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_line_manage)
                .setItems(items, (dialog, which) -> {
                    if (!readOnlyProject && which == safeLines.size()) {
                        showCreateLineDialog();
                        return;
                    }
                    SurveyLineEntity line = safeLines.get(which);
                    PointListItem item = PointListItem.createLine(line.getId(), line.getName(), 0);
                    item.title = treeHelper.buildLineTitle(line.getName());
                    item.breadcrumb = actionHandler.buildLineBreadcrumb(item.title);
                    actionHandler.selectLine(item);
                })
                .show();
    }

    public void showCreateLineDialog() {
        android.view.View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_create_project, null);
        EditText editName = dialogView.findViewById(R.id.edit_project_name);
        EditText editDescription = dialogView.findViewById(R.id.edit_project_description);
        editName.setHint(R.string.hint_line_number);
        editDescription.setHint(R.string.hint_note);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_create_line)
                .setView(dialogView)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    float lineNumber = formHelper.parseFloat(editName, Float.NaN);
                    if (!Float.isFinite(lineNumber)) {
                        Toast.makeText(activity, activity.getString(R.string.toast_invalid_line_number), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String note = editDescription.getText() == null
                            ? ""
                            : editDescription.getText().toString().trim();
                    actionHandler.createLine(lineNumber, note);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    public void showTransmitCurrentDialog(float[] presets) {
        String[] items = new String[presets.length + 1];
        for (int i = 0; i < presets.length; i++) {
            items[i] = activity.getString(R.string.collection_unit_hz_value, formHelper.formatNumber(presets[i]));
        }
        items[items.length - 1] = activity.getString(R.string.collection_option_custom);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.collection_set_send_frequency)
                .setItems(items, (dialog, which) -> {
                    if (which == presets.length) {
                        showCustomValueDialog(
                                R.string.collection_set_send_frequency,
                                R.string.collection_custom_send_frequency_hint,
                                formHelper.getTextValue(actionHandler.getTransmitCurrentField()),
                                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                                value -> {
                                    formHelper.setFieldValue(actionHandler.getTransmitCurrentField(), value);
                                    actionHandler.refreshCollectionParameterCards();
                                });
                        return;
                    }
                    formHelper.setFieldValue(actionHandler.getTransmitCurrentField(), formHelper.formatNumber(presets[which]));
                    actionHandler.refreshCollectionParameterCards();
                })
                .show();
    }

    public void showCollectionCountDialog(int[] presets) {
        String[] items = new String[presets.length + 1];
        for (int i = 0; i < presets.length; i++) {
            items[i] = activity.getString(R.string.collection_unit_period_value, String.valueOf(presets[i]));
        }
        items[items.length - 1] = activity.getString(R.string.collection_option_custom);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.collection_set_period)
                .setItems(items, (dialog, which) -> {
                    if (which == presets.length) {
                        showCustomValueDialog(
                                R.string.collection_set_period,
                                R.string.collection_custom_period_hint,
                                formHelper.getTextValue(actionHandler.getCollectionCountField()),
                                InputType.TYPE_CLASS_NUMBER,
                                value -> {
                                    formHelper.setFieldValue(actionHandler.getCollectionCountField(), value);
                                    actionHandler.refreshCollectionParameterCards();
                                });
                        return;
                    }
                    formHelper.setFieldValue(actionHandler.getCollectionCountField(), String.valueOf(presets[which]));
                    actionHandler.refreshCollectionParameterCards();
                })
                .show();
    }

    public void showSampleFrequencyDialog(int[] presets) {
        String[] items = new String[presets.length + 1];
        for (int i = 0; i < presets.length; i++) {
            items[i] = activity.getString(R.string.collection_unit_hz_value, String.valueOf(presets[i]));
        }
        items[items.length - 1] = activity.getString(R.string.collection_option_custom);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.collection_set_frequency)
                .setItems(items, (dialog, which) -> {
                    if (which == presets.length) {
                        showCustomValueDialog(
                                R.string.collection_set_frequency,
                                R.string.collection_custom_frequency_hint,
                                formHelper.getTextValue(actionHandler.getSampleFrequencyField()),
                                InputType.TYPE_CLASS_NUMBER,
                                value -> {
                                    formHelper.setFieldValue(actionHandler.getSampleFrequencyField(), value);
                                    actionHandler.refreshCollectionParameterCards();
                                });
                        return;
                    }
                    formHelper.setFieldValue(actionHandler.getSampleFrequencyField(), String.valueOf(presets[which]));
                    actionHandler.refreshCollectionParameterCards();
                })
                .show();
    }

    private void showCustomValueDialog(
            int titleRes,
            int hintRes,
            String currentValue,
            int inputType,
            ValueCommitter committer) {
        EditText input = new EditText(activity);
        input.setInputType(inputType);
        input.setText(currentValue);
        input.setHint(hintRes);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);

        FrameLayout container = new FrameLayout(activity);
        int horizontalPadding = dpToPx(20);
        int verticalPadding = dpToPx(8);
        container.setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0);
        container.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle(titleRes)
                .setView(container)
                .setPositiveButton(R.string.collection_dialog_confirm, (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        committer.commit(value);
                    }
                })
                .setNegativeButton(R.string.collection_dialog_cancel, null)
                .show();
    }

    private void showParameterTemplateSlotDialog(@NonNull String databaseName, boolean saveMode) {
        int slotCount = AppSettings.getCollectionTemplateSlotCount();
        String[] items = new String[slotCount];
        for (int i = 0; i < slotCount; i++) {
            int slot = i + 1;
            CollectionParameterEntity template = AppSettings.getCollectionTemplate(activity, databaseName, slot);
            items[i] = activity.getString(
                    R.string.collection_template_slot_label,
                    slot,
                    formHelper.buildParameterTemplateSummary(template));
        }

        new AlertDialog.Builder(activity)
                .setTitle(saveMode
                        ? R.string.collection_template_save_dialog_title
                        : R.string.collection_template_apply_dialog_title)
                .setItems(items, (dialog, which) -> {
                    int slot = which + 1;
                    if (saveMode) {
                        CollectionParameterEntity currentParameters = formHelper.collectParameters();
                        AppSettings.saveCollectionTemplate(
                                activity,
                                databaseName,
                                slot,
                                currentParameters);
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_TEMPLATE,
                                activity.getString(R.string.operation_log_title_save_parameter_template),
                                activity.getString(
                                        R.string.operation_log_detail_template_slot_summary,
                                        slot,
                                        formHelper.buildParameterTemplateSummary(currentParameters)),
                                databaseName);
                        Toast.makeText(
                                activity,
                                activity.getString(R.string.toast_collection_template_saved, slot),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CollectionParameterEntity template = AppSettings.getCollectionTemplate(activity, databaseName, slot);
                    if (template == null) {
                        operationLogStore.record(
                                OperationLogStore.CATEGORY_TEMPLATE,
                                activity.getString(R.string.operation_log_title_apply_parameter_template_failed),
                                activity.getString(
                                        R.string.operation_log_detail_template_slot_empty,
                                        slot),
                                databaseName);
                        Toast.makeText(
                                activity,
                                activity.getString(R.string.toast_collection_template_empty, slot),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    formHelper.applyParameters(template);
                    actionHandler.refreshCollectionParameterCards();
                    operationLogStore.record(
                            OperationLogStore.CATEGORY_TEMPLATE,
                            activity.getString(R.string.operation_log_title_apply_parameter_template),
                            activity.getString(
                                    R.string.operation_log_detail_template_slot_summary,
                                    slot,
                                    formHelper.buildParameterTemplateSummary(template)),
                            databaseName);
                    Toast.makeText(
                            activity,
                            activity.getString(R.string.toast_collection_template_applied, slot),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }

    public interface ActionHandler {
        boolean isReadOnlyProject();

        boolean canSavePendingResult();

        @NonNull
        CollectionQualityCheckResult evaluatePendingResult(@Nullable CollectionParameterEntity parameters);

        void saveCurrentPoint(@Nullable CollectionQualityCheckResult qualityCheckResult);

        void discardPendingResult();

        void requestConnect(String ip, int port, boolean simulationEnabled);

        void createLine(float lineNumber, String note);

        void selectLine(PointListItem item);

        String buildLineBreadcrumb(String lineTitle);

        EditText getTransmitCurrentField();

        EditText getCollectionCountField();

        EditText getSampleFrequencyField();

        void refreshCollectionParameterCards();
    }

    private interface ValueCommitter {
        void commit(String value);
    }
}
