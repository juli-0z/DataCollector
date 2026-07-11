package cn.zjl.datacollector.ui.collection.panel;

/**
 * 阅读提示：采集操作面板代码：负责参数输入、连接弹窗、质检设置和界面控件渲染。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.ui.collection.screen.CollectionViewModel;
import cn.zjl.datacollector.ui.playback.PointListItem;

/**
 * 集中处理采集表单的读写、格式化和参数卡片刷新，降低 Activity 中的界面细节代码占比。
 */
public class CollectionFormHelper {

    private final Context context;
    private final EditText editLineNumber;
    private final EditText editPointNumber;
    private final EditText editTransmitCurrent;
    private final EditText editSampleFrequency;
    private final EditText editCollectionCount;
    private final EditText editSampleTime;
    private final EditText editElectrodeDistance;
    private final TextView textCurrentLineValue;
    private final TextView textTransmitCurrentValue;
    private final TextView textCollectionCountValue;
    private final TextView textSampleFrequencyValue;

    public CollectionFormHelper(
            Context context,
            EditText editLineNumber,
            EditText editPointNumber,
            EditText editTransmitCurrent,
            EditText editSampleFrequency,
            EditText editCollectionCount,
            EditText editSampleTime,
            EditText editElectrodeDistance,
            TextView textCurrentLineValue,
            TextView textTransmitCurrentValue,
            TextView textCollectionCountValue,
            TextView textSampleFrequencyValue) {
        this.context = context;
        this.editLineNumber = editLineNumber;
        this.editPointNumber = editPointNumber;
        this.editTransmitCurrent = editTransmitCurrent;
        this.editSampleFrequency = editSampleFrequency;
        this.editCollectionCount = editCollectionCount;
        this.editSampleTime = editSampleTime;
        this.editElectrodeDistance = editElectrodeDistance;
        this.textCurrentLineValue = textCurrentLineValue;
        this.textTransmitCurrentValue = textTransmitCurrentValue;
        this.textCollectionCountValue = textCollectionCountValue;
        this.textSampleFrequencyValue = textSampleFrequencyValue;
    }

    public CollectionParameterEntity collectParameters() {
        CollectionParameterEntity parameters = new CollectionParameterEntity();
        parameters.setTransmitCurrent(parseFloat(editTransmitCurrent, 25f));
        parameters.setSampleFrequency(parseInt(editSampleFrequency, 300));
        parameters.setCollectionCount(parseInt(editCollectionCount, 2));
        parameters.setSampleTime(parseFloat(editSampleTime, 10f));
        parameters.setElectrodeDistance(parseFloat(editElectrodeDistance, 0f));
        parameters.setTransmitterDirection("");
        parameters.setCustomParameters("");
        return parameters;
    }

    public void applyFormState(@Nullable CollectionViewModel.FormState state) {
        if (state == null) {
            return;
        }
        if (state.point != null) {
            setFieldValue(editPointNumber, formatNumber(state.point.getName()));
        }
        applyParameters(state.parameters);
    }

    public void applyParameters(@Nullable CollectionParameterEntity parameters) {
        if (parameters == null) {
            return;
        }
        setFieldValue(editTransmitCurrent, formatNumber(parameters.getTransmitCurrent()));
        setFieldValue(editSampleFrequency, String.valueOf(parameters.getSampleFrequency()));
        setFieldValue(editCollectionCount, String.valueOf(parameters.getCollectionCount()));
        setFieldValue(editSampleTime, formatNumber(parameters.getSampleTime()));
        setFieldValue(editElectrodeDistance, formatNumber(parameters.getElectrodeDistance()));
    }

    public String buildParameterTemplateSummary(@Nullable CollectionParameterEntity parameters) {
        if (parameters == null) {
            return context.getString(R.string.collection_template_slot_empty);
        }
        return context.getString(
                R.string.collection_template_summary,
                formatNumber(parameters.getTransmitCurrent()),
                String.valueOf(parameters.getSampleFrequency()),
                String.valueOf(parameters.getCollectionCount()),
                formatNumber(parameters.getSampleTime()));
    }

    public void syncWaveformMetaToFields(
            @Nullable CollectionViewModel.ChartDisplayState chartState,
            boolean shouldUseWaveformMeta) {
        if (chartState == null || !shouldUseWaveformMeta) {
            return;
        }
        if (chartState.recvFs > 0f) {
            setFieldValue(editSampleFrequency, String.valueOf(Math.round(chartState.recvFs)));
        }
        if (chartState.sendFs > 0f) {
            setFieldValue(editTransmitCurrent, formatNumber(chartState.sendFs));
        }
        if (chartState.period > 0) {
            setFieldValue(editCollectionCount, String.valueOf(chartState.period));
        }
        float sampleAxisFrequency = chartState.sampleSendFs > 0f
                ? chartState.sampleSendFs
                : chartState.sampleOffFs;
        if (sampleAxisFrequency > 0f) {
            setFieldValue(editSampleTime, formatNumber(1_000_000f / sampleAxisFrequency));
        }
    }

    public void refreshParameterCards(
            @Nullable CollectionViewModel.SelectionState selectionState,
            @Nullable CollectionViewModel.ChartDisplayState chartState) {
        if (textCurrentLineValue != null) {
            String lineValue = getTextValue(editLineNumber);
            textCurrentLineValue.setText(lineValue.isEmpty()
                    ? context.getString(R.string.collection_unset_value)
                    : lineValue);
        }
        if (textTransmitCurrentValue != null) {
            textTransmitCurrentValue.setText(resolveSendTileValue(selectionState, chartState));
        }
        if (textCollectionCountValue != null) {
            textCollectionCountValue.setText(resolvePeriodTileValue(selectionState, chartState));
        }
        if (textSampleFrequencyValue != null) {
            textSampleFrequencyValue.setText(resolveRepeatTileValue(selectionState, chartState));
        }
    }

    public void updateLineValue(@Nullable String lineTitle) {
        setFieldValue(editLineNumber, extractLineValue(lineTitle));
    }

    public float readPointNumber(float defaultValue) {
        return parseFloat(editPointNumber, defaultValue);
    }

    public String readLineNumberText() {
        return getTextValue(editLineNumber);
    }

    public String readPointNumberText() {
        return getTextValue(editPointNumber);
    }

    public void updatePointNumber(float pointNumber) {
        setFieldValue(editPointNumber, formatNumber(pointNumber));
    }

    public void setFieldValue(@Nullable EditText editText, @Nullable String value) {
        if (editText == null) {
            return;
        }
        String safeValue = value == null ? "" : value;
        if (safeValue.equals(getTextValue(editText))) {
            return;
        }
        editText.setText(safeValue);
    }

    public String getTextValue(@Nullable EditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    public Context getContext() {
        return context;
    }

    public int parseInt(@Nullable EditText editText, int defaultValue) {
        if (editText == null || editText.getText() == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    public float parseFloat(@Nullable EditText editText, float defaultValue) {
        if (editText == null || editText.getText() == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(editText.getText().toString().trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    public String formatNumber(float value) {
        return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
    }

    private String resolveSendTileValue(
            @Nullable CollectionViewModel.SelectionState selectionState,
            @Nullable CollectionViewModel.ChartDisplayState chartState) {
        if (shouldUseWaveformMeta(selectionState) && chartState != null && chartState.sendFs > 0f) {
            return context.getString(R.string.collection_unit_hz_value, formatNumber(chartState.sendFs));
        }
        return buildDisplayValue(editTransmitCurrent, R.string.collection_unit_hz_value, 25f);
    }

    private String resolvePeriodTileValue(
            @Nullable CollectionViewModel.SelectionState selectionState,
            @Nullable CollectionViewModel.ChartDisplayState chartState) {
        if (shouldUseWaveformMeta(selectionState) && chartState != null && chartState.period > 0) {
            return context.getString(R.string.collection_unit_period_value, String.valueOf(chartState.period));
        }
        return buildDisplayValue(editCollectionCount, R.string.collection_unit_period_value, 2);
    }

    private String resolveRepeatTileValue(
            @Nullable CollectionViewModel.SelectionState selectionState,
            @Nullable CollectionViewModel.ChartDisplayState chartState) {
        if (!shouldUseWaveformMeta(selectionState) || chartState == null || chartState.repeatCount <= 0) {
            return context.getString(R.string.collection_unset_value);
        }
        return context.getString(R.string.collection_unit_repeat_value, String.valueOf(chartState.repeatCount));
    }

    private boolean shouldUseWaveformMeta(@Nullable CollectionViewModel.SelectionState selectionState) {
        return selectionState != null
                && (selectionState.type == PointListItem.TYPE_POINT
                || selectionState.type == PointListItem.TYPE_SESSION);
    }

    private String buildDisplayValue(EditText field, int formatRes, float defaultValue) {
        float value = parseFloat(field, defaultValue);
        return context.getString(formatRes, formatNumber(value));
    }

    private String buildDisplayValue(EditText field, int formatRes, int defaultValue) {
        int value = parseInt(field, defaultValue);
        return context.getString(formatRes, String.valueOf(value));
    }

    private String extractLineValue(@Nullable String lineTitle) {
        if (lineTitle == null) {
            return "";
        }
        String trimmed = lineTitle.trim();
        int index = trimmed.lastIndexOf(' ');
        return index >= 0 && index < trimmed.length() - 1
                ? trimmed.substring(index + 1)
                : trimmed;
    }
}
