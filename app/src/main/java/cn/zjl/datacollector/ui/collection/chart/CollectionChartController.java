package cn.zjl.datacollector.ui.collection.chart;

/**
 * 阅读提示：采集图表模块代码：负责波形数据解码、状态整理和 RECV/SEND/OFF 图表绘制。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import androidx.annotation.Nullable;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.ui.collection.panel.CollectionFormHelper;
import cn.zjl.datacollector.ui.collection.panel.CollectionUiRenderer;
import cn.zjl.datacollector.ui.collection.screen.CollectionViewModel;
import cn.zjl.datacollector.ui.playback.PointListItem;

/**
 * 统一负责图表区域的状态应用：
 * 1. 接收 ViewModel 下发的 ChartDisplayState；
 * 2. 驱动三张图表刷新；
 * 3. 同步波形元数据到表单；
 * 4. 维护参数卡片依赖的最近一次图表状态。
 */
public class CollectionChartController {

    private final WaveformChartRenderer waveformChartRenderer;
    private final CollectionUiRenderer uiRenderer;
    private final CollectionFormHelper formHelper;

    private CollectionViewModel.SelectionState currentSelectionState;
    private CollectionViewModel.ChartDisplayState lastChartDisplayState =
            new CollectionViewModel.ChartDisplayState();

    public CollectionChartController(WaveformChartRenderer waveformChartRenderer,
                                     CollectionUiRenderer uiRenderer,
                                     CollectionFormHelper formHelper) {
        this.waveformChartRenderer = waveformChartRenderer;
        this.uiRenderer = uiRenderer;
        this.formHelper = formHelper;
    }

    public void clearChartsOnly() {
        waveformChartRenderer.renderWaveformPanels(new WaveformChartData());
    }

    public void onSelectionStateChanged(@Nullable CollectionViewModel.SelectionState state) {
        currentSelectionState = state;
        refreshMainChartTitle();
        refreshParameterCards();
    }

    public void onFormStateApplied() {
        refreshMainChartTitle();
        refreshParameterCards();
    }

    public void onChartStateChanged(@Nullable CollectionViewModel.ChartDisplayState chartState) {
        if (chartState == null) {
            lastChartDisplayState = new CollectionViewModel.ChartDisplayState();
            waveformChartRenderer.renderWaveformPanels(new WaveformChartData());
            uiRenderer.renderWaveformStatus(null);
            refreshParameterCards();
            return;
        }

        lastChartDisplayState = chartState;
        formHelper.syncWaveformMetaToFields(chartState, shouldUseWaveformMeta());
        if (chartState.aggregateMode) {
            waveformChartRenderer.renderAggregateWaveforms(chartState.waveformData);
        } else {
            waveformChartRenderer.renderWaveformPanels(chartState.waveformData);
        }
        uiRenderer.renderWaveformStatus(chartState);
        refreshMainChartTitle();
        refreshParameterCards();
    }

    public void refreshParameterCards() {
        formHelper.refreshParameterCards(currentSelectionState, lastChartDisplayState);
    }

    @Nullable
    public CollectionViewModel.SelectionState getCurrentSelectionState() {
        return currentSelectionState;
    }

    public CollectionViewModel.ChartDisplayState getLastChartDisplayState() {
        return lastChartDisplayState;
    }

    private void refreshMainChartTitle() {
        uiRenderer.renderMainChartTitle(buildMainChartTitle());
    }

    private boolean shouldUseWaveformMeta() {
        return currentSelectionState != null
                && (currentSelectionState.type == PointListItem.TYPE_POINT
                || currentSelectionState.type == PointListItem.TYPE_SESSION);
    }

    private String buildMainChartTitle() {
        if (currentSelectionState == null) {
            return null;
        }

        if (currentSelectionState.type == PointListItem.TYPE_PROJECT) {
            String projectName = extractProjectNameFromBreadcrumb(currentSelectionState.breadcrumb);
            return projectName.isEmpty()
                    ? null
                    : formHelper.getContext().getString(R.string.recv_chart_title_project, projectName);
        }

        String lineNumber = sanitizeNumber(formHelper.readLineNumberText());
        String pointNumber = sanitizeNumber(formHelper.readPointNumberText());

        if (lineNumber.isEmpty() || (requiresPointNumber(currentSelectionState.type) && pointNumber.isEmpty())) {
            String[] valuesFromBreadcrumb = extractValuesFromBreadcrumb(currentSelectionState.breadcrumb);
            if (lineNumber.isEmpty()) {
                lineNumber = valuesFromBreadcrumb[0];
            }
            if (pointNumber.isEmpty()) {
                pointNumber = valuesFromBreadcrumb[1];
            }
        }

        if (currentSelectionState.type == PointListItem.TYPE_SESSION
                && !lineNumber.isEmpty()
                && !pointNumber.isEmpty()
                && currentSelectionState.sessionIndex > 0) {
            return formHelper.getContext().getString(
                    R.string.recv_chart_title_session,
                    lineNumber,
                    pointNumber,
                    String.valueOf(currentSelectionState.sessionIndex));
        }
        if ((currentSelectionState.type == PointListItem.TYPE_POINT
                || currentSelectionState.type == PointListItem.TYPE_SESSION)
                && !lineNumber.isEmpty()
                && !pointNumber.isEmpty()) {
            return formHelper.getContext().getString(
                    R.string.recv_chart_title_point,
                    lineNumber,
                    pointNumber);
        }
        if (!lineNumber.isEmpty()) {
            return formHelper.getContext().getString(R.string.recv_chart_title_line, lineNumber);
        }
        return null;
    }

    private boolean requiresPointNumber(int selectionType) {
        return selectionType == PointListItem.TYPE_POINT
                || selectionType == PointListItem.TYPE_SESSION;
    }

    private String[] extractValuesFromBreadcrumb(@Nullable String breadcrumb) {
        String[] values = new String[]{"", ""};
        if (breadcrumb == null || breadcrumb.trim().isEmpty()) {
            return values;
        }

        String[] segments = breadcrumb.split("/");
        if (segments.length > 1) {
            values[0] = sanitizeNumber(segments[1]);
        }
        if (segments.length > 2) {
            values[1] = sanitizeNumber(segments[2]);
        }
        return values;
    }

    private String extractProjectNameFromBreadcrumb(@Nullable String breadcrumb) {
        if (breadcrumb == null || breadcrumb.trim().isEmpty()) {
            return "";
        }
        String[] segments = breadcrumb.split("/");
        return segments.length == 0 ? "" : segments[0].trim();
    }

    private String sanitizeNumber(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("[^0-9.\\-]", "");
        if (digits.endsWith(".0")) {
            digits = digits.substring(0, digits.length() - 2);
        }
        return digits;
    }
}
