package cn.zjl.datacollector.ui.collection.chart;

/**
 * 阅读提示：采集图表模块代码：负责波形数据解码、状态整理和 RECV/SEND/OFF 图表绘制。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.R;

public class WaveformChartRenderer {
    private static final int MAX_MAIN_CHART_POINTS = 1200;
    private static final int MAX_PREVIEW_CHART_POINTS = 600;
    private static final float AXIS_RANGE_EPSILON = 1e-6f;
    private static final float RECV_LOG_EPSILON = 1.0e-12f;
    private static final float LOG_AXIS_LABEL_TOLERANCE = 0.18f;
    private static final int RECV_MAJOR_GRID = Color.argb(140, 0, 0, 0);
    private static final int RECV_MINOR_GRID = Color.argb(72, 120, 120, 120);
    private static final int RECV_AXIS_COLOR = Color.argb(220, 0, 0, 0);
    private static final int RECV_GUIDE_LINE = Color.argb(120, 128, 128, 128);
    private static final String LABEL_RECV = "Recv";
    private static final String LABEL_SEND = "Send";
    private static final String LABEL_OFF = "Off";

    private final Context context;
    private final CombinedChart chartMain;
    private final LineChart chartSend;
    private final LineChart chartOff;

    public WaveformChartRenderer(Context context, CombinedChart chartMain, LineChart chartSend, LineChart chartOff) {
        this.context = context;
        this.chartMain = chartMain;
        this.chartSend = chartSend;
        this.chartOff = chartOff;
    }

    public void initCharts() {
        setupRecvChart();
        setupPreviewChart(chartSend, LABEL_SEND);
        setupPreviewChart(chartOff, LABEL_OFF);
        renderWaveformPanels(new float[0], new float[0], new float[0], new float[0], new float[0], new float[0]);
    }

    public void renderWaveformPanels(float[] recvTimes, float[] recvValues, float[] sendTimes, float[] sendValues, float[] offTimes, float[] offValues) {
        renderRecvChart(recvTimes, recvValues);
        renderPreviewChart(chartSend, sendTimes, sendValues, LABEL_SEND);
        renderPreviewChart(chartOff, offTimes, offValues, LABEL_OFF);
    }

    public void renderWaveformPanels(WaveformChartData chartData) {
        if (chartData == null) {
            renderWaveformPanels(new float[0], new float[0], new float[0], new float[0], new float[0], new float[0]);
            return;
        }
        renderWaveformPanels(chartData.recv.times, chartData.recv.values, chartData.send.times, chartData.send.values, chartData.off.times, chartData.off.values);
    }

    public void renderAggregateWaveforms(WaveformChartData chartData) {
        if (chartData == null) {
            clearRecvChart();
            renderAggregatePreviewChart(chartSend, new ArrayList<>(), LABEL_SEND);
            renderAggregatePreviewChart(chartOff, new ArrayList<>(), LABEL_OFF);
            return;
        }
        renderAggregateRecvChart(chartData.aggregateRecv);
        renderAggregatePreviewChart(chartSend, buildAggregatePreviewDataSets(chartData.aggregateSend, LABEL_SEND), LABEL_SEND);
        renderAggregatePreviewChart(chartOff, buildAggregatePreviewDataSets(chartData.aggregateOff, LABEL_OFF), LABEL_OFF);
    }

    private void setupRecvChart() {
        chartMain.getDescription().setEnabled(false);
        chartMain.getAxisRight().setEnabled(false);
        chartMain.setNoDataText(context.getString(R.string.chart_no_data, LABEL_RECV));
        chartMain.setNoDataTextColor(color(R.color.text_secondary));
        chartMain.setDrawGridBackground(false);
        chartMain.setDrawBorders(false);
        chartMain.setDrawValueAboveBar(false);
        chartMain.setExtraOffsets(18f, 26f, 24f, 22f);
        chartMain.setTouchEnabled(true);
        chartMain.setDragEnabled(true);
        chartMain.setScaleEnabled(true);
        chartMain.setScaleYEnabled(false);
        chartMain.setPinchZoom(false);
        chartMain.setDoubleTapToZoomEnabled(false);
        chartMain.setHighlightPerTapEnabled(false);
        chartMain.setHighlightPerDragEnabled(false);
        chartMain.setDrawOrder(new CombinedChart.DrawOrder[]{CombinedChart.DrawOrder.LINE, CombinedChart.DrawOrder.SCATTER});
        Legend legend = chartMain.getLegend();
        legend.setEnabled(false);
        XAxis xAxis = chartMain.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(RECV_AXIS_COLOR);
        xAxis.setAxisLineWidth(1f);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(RECV_MAJOR_GRID);
        xAxis.setGridLineWidth(0.8f);
        xAxis.enableGridDashedLine(2f, 4f, 0f);
        xAxis.setTextColor(RECV_AXIS_COLOR);
        xAxis.setTextSize(10f);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(1f);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setXOffset(2f);
        xAxis.setYOffset(8f);
        xAxis.setValueFormatter(createRecvLogAxisFormatter());
        xAxis.setDrawLimitLinesBehindData(true);
        YAxis leftAxis = chartMain.getAxisLeft();
        leftAxis.setDrawAxisLine(true);
        leftAxis.setAxisLineColor(RECV_AXIS_COLOR);
        leftAxis.setAxisLineWidth(1f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(RECV_MAJOR_GRID);
        leftAxis.setGridLineWidth(0.8f);
        leftAxis.enableGridDashedLine(2f, 4f, 0f);
        leftAxis.setTextColor(RECV_AXIS_COLOR);
        leftAxis.setTextSize(10f);
        leftAxis.setXOffset(8f);
        leftAxis.setYOffset(2f);
        leftAxis.setSpaceTop(0f);
        leftAxis.setSpaceBottom(0f);
        leftAxis.setValueFormatter(createRecvLogAxisFormatter());
        leftAxis.setDrawLimitLinesBehindData(true);
        applyRecvViewport(1f, 4f, -5f, 0f);
    }

    private void setupPreviewChart(LineChart chart, String label) {
        chart.getDescription().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.setNoDataText(context.getString(R.string.chart_no_data, label));
        chart.setNoDataTextColor(color(R.color.text_secondary));
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setExtraOffsets(8f, 12f, 8f, 6f);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setHighlightPerTapEnabled(true);
        Legend legend = chart.getLegend();
        legend.setEnabled(false);
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(color(R.color.divider));
        leftAxis.setGridLineWidth(0.8f);
        leftAxis.enableGridDashedLine(6f, 6f, 0f);
        leftAxis.setTextColor(color(R.color.text_secondary));
        leftAxis.setTextSize(10f);
        leftAxis.setDrawAxisLine(LABEL_SEND.equals(label));
        leftAxis.setAxisLineColor(color(R.color.divider));
        leftAxis.setAxisLineWidth(LABEL_SEND.equals(label) ? 1f : 0.9f);
        leftAxis.setLabelCount(LABEL_SEND.equals(label) ? 5 : 4, LABEL_SEND.equals(label));
        leftAxis.setSpaceTop(14f);
        leftAxis.setSpaceBottom(10f);
        leftAxis.setDrawZeroLine(LABEL_SEND.equals(label));
        if (LABEL_SEND.equals(label)) { leftAxis.setZeroLineColor(color(R.color.text_secondary)); leftAxis.setZeroLineWidth(1f); }
        leftAxis.setValueFormatter(createAmplitudeFormatter(label, 1f));
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(color(R.color.divider));
        xAxis.setGridLineWidth(0.7f);
        xAxis.enableGridDashedLine(6f, 6f, 0f);
        xAxis.setAxisLineColor(color(R.color.divider));
        xAxis.setAxisLineWidth(0.9f);
        xAxis.setTextColor(color(R.color.text_secondary));
        xAxis.setTextSize(10f);
        xAxis.setGranularityEnabled(true);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setLabelCount(5, false);
        xAxis.setYOffset(6f);
        xAxis.setValueFormatter(createTimeAxisFormatter(1f));
    }

    private void renderRecvChart(float[] xValues, float[] yValues) {
        List<Entry> entries = buildRecvChartEntries(xValues, yValues, MAX_MAIN_CHART_POINTS);
        if (entries.isEmpty()) { clearRecvChart(); return; }
        ScatterDataSet scatterDataSet = buildRecvScatterDataSet(entries, false);
        CombinedData combinedData = new CombinedData();
        combinedData.setData(new ScatterData(scatterDataSet));
        chartMain.setData(combinedData);
        applyRecvViewport(entries);
        chartMain.animateX(180);
        chartMain.invalidate();
    }

    private void renderAggregateRecvChart(List<WaveformChartData.WaveformChannelData> channels) {
        if (channels == null || channels.isEmpty()) { clearRecvChart(); return; }
        ScatterData scatterData = new ScatterData();
        LineData lineData = new LineData();
        List<Entry> combinedEntries = new ArrayList<>();
        int index = 0;
        for (WaveformChartData.WaveformChannelData channel : channels) {
            if (channel == null) { continue; }
            List<Entry> entries = buildRecvChartEntries(channel.times, channel.values, MAX_MAIN_CHART_POINTS);
            if (entries.isEmpty()) { continue; }
            combinedEntries.addAll(entries);
            scatterData.addDataSet(buildRecvScatterDataSet(entries, true));
            lineData.addDataSet(buildRecvGuideLineDataSet(entries, index++));
        }
        if (combinedEntries.isEmpty()) { clearRecvChart(); return; }
        CombinedData combinedData = new CombinedData();
        if (!lineData.getDataSets().isEmpty()) { combinedData.setData(lineData); }
        if (!scatterData.getDataSets().isEmpty()) { combinedData.setData(scatterData); }
        chartMain.setData(combinedData);
        applyRecvViewport(combinedEntries);
        chartMain.invalidate();
    }

    private void clearRecvChart() { chartMain.clear(); chartMain.setNoDataText(context.getString(R.string.chart_no_data, LABEL_RECV)); applyRecvViewport(1f, 4f, -5f, 0f); chartMain.invalidate(); }

    private ScatterDataSet buildRecvScatterDataSet(List<Entry> entries, boolean aggregateMode) {
        ScatterDataSet dataSet = new ScatterDataSet(entries, LABEL_RECV);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        dataSet.setScatterShapeSize(7.5f);
        dataSet.setShapeRenderer(new DiamondShapeRenderer());
        dataSet.setColor(aggregateMode ? ColorUtils.setAlphaComponent(resolveChartColor(LABEL_RECV), 200) : resolveChartColor(LABEL_RECV));
        return dataSet;
    }

    private LineDataSet buildRecvGuideLineDataSet(List<Entry> entries, int index) {
        LineDataSet dataSet = new LineDataSet(entries, LABEL_RECV + "_guide_" + index);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        dataSet.setDrawCircles(false);
        dataSet.setColor(RECV_GUIDE_LINE);
        dataSet.setLineWidth(0.9f);
        dataSet.enableDashedLine(4f, 4f, 0f);
        return dataSet;
    }

    private List<ILineDataSet> buildAggregatePreviewDataSets(List<WaveformChartData.WaveformChannelData> channels, String label) {
        List<ILineDataSet> dataSets = new ArrayList<>();
        if (channels == null || channels.isEmpty()) { return dataSets; }
        int baseColor = resolveChartColor(label);
        int index = 0;
        for (WaveformChartData.WaveformChannelData channel : channels) {
            if (channel == null) { continue; }
            List<Entry> entries = buildChartEntries(channel.times, channel.values, MAX_PREVIEW_CHART_POINTS);
            if (entries.isEmpty()) { continue; }
            LineDataSet dataSet = new LineDataSet(entries, label + "_" + index++);
            stylePreviewDataSet(dataSet, baseColor, true);
            dataSets.add(dataSet);
        }
        return dataSets;
    }

    private void renderAggregatePreviewChart(LineChart chart, List<ILineDataSet> dataSets, String label) {
        if (dataSets == null || dataSets.isEmpty()) { chart.clear(); chart.setNoDataText(context.getString(R.string.chart_no_data, label)); chart.invalidate(); return; }
        LineData lineData = new LineData(dataSets);
        chart.setData(lineData);
        applyPreviewViewport(chart, lineData.getXMin(), lineData.getXMax(), lineData.getYMin(), lineData.getYMax(), label);
        chart.invalidate();
    }

    private void renderPreviewChart(LineChart chart, float[] xValues, float[] yValues, String label) {
        List<Entry> entries = buildChartEntries(xValues, yValues, MAX_PREVIEW_CHART_POINTS);
        if (entries.isEmpty()) { chart.clear(); chart.setNoDataText(context.getString(R.string.chart_no_data, label)); chart.invalidate(); return; }
        LineDataSet dataSet = new LineDataSet(entries, label);
        stylePreviewDataSet(dataSet, resolveChartColor(label), false);
        chart.setData(new LineData(dataSet));
        applyPreviewViewport(chart, entries, label);
        chart.invalidate();
    }

    private void stylePreviewDataSet(LineDataSet dataSet, int baseColor, boolean aggregateMode) {
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(aggregateMode ? 1f : 1.6f);
        dataSet.setColor(aggregateMode ? ColorUtils.setAlphaComponent(baseColor, 130) : baseColor);
    }

    private int resolveChartColor(String label) { return LABEL_RECV.equals(label) ? color(R.color.chart_recv) : LABEL_SEND.equals(label) ? color(R.color.chart_send) : color(R.color.chart_off); }
    private int color(int colorRes) { return ContextCompat.getColor(context, colorRes); }

    private List<Entry> buildChartEntries(float[] xValues, float[] yValues, int maxPoints) {
        int count = Math.min(xValues != null ? xValues.length : 0, yValues != null ? yValues.length : 0);
        List<Entry> entries = new ArrayList<>(Math.min(count, maxPoints));
        if (count <= 0) { return entries; }
        if (count <= maxPoints) {
            for (int i = 0; i < count; i++) if (Float.isFinite(xValues[i]) && Float.isFinite(yValues[i])) entries.add(new Entry(xValues[i], yValues[i]));
            return entries;
        }
        float lastIndex = count - 1f;
        for (int i = 0; i < maxPoints; i++) {
            int sampledIndex = Math.round((i * lastIndex) / (maxPoints - 1f));
            if (Float.isFinite(xValues[sampledIndex]) && Float.isFinite(yValues[sampledIndex])) entries.add(new Entry(xValues[sampledIndex], yValues[sampledIndex]));
        }
        return entries;
    }

    private List<Entry> buildRecvChartEntries(float[] xValues, float[] yValues, int maxPoints) {
        int count = Math.min(xValues != null ? xValues.length : 0, yValues != null ? yValues.length : 0);
        List<Entry> entries = new ArrayList<>(Math.min(count, maxPoints));
        if (count <= 0) { return entries; }
        float minPositive = Float.MAX_VALUE;
        int validCount = 0;
        for (int i = 0; i < count; i++) {
            float xValue = xValues[i], yValue = yValues[i], amplitude = Math.abs(yValue);
            if (xValue > 0f && Float.isFinite(xValue) && Float.isFinite(yValue)) validCount++;
            if (xValue > 0f && amplitude > 0f && Float.isFinite(amplitude)) minPositive = Math.min(minPositive, amplitude);
        }
        if (validCount == 0) { return entries; }
        float floor = minPositive == Float.MAX_VALUE ? RECV_LOG_EPSILON : Math.max(RECV_LOG_EPSILON, minPositive * 0.5f);
        if (validCount <= maxPoints) {
            for (int i = 0; i < count; i++) appendRecvEntry(entries, xValues[i], yValues[i], floor);
            return entries;
        }
        int[] validIndices = new int[validCount];
        int validPointer = 0;
        for (int i = 0; i < count; i++) if (xValues[i] > 0f && Float.isFinite(xValues[i]) && Float.isFinite(yValues[i])) validIndices[validPointer++] = i;
        float lastIndex = validCount - 1f;
        for (int i = 0; i < maxPoints; i++) {
            int sourceIndex = validIndices[Math.round((i * lastIndex) / (maxPoints - 1f))];
            appendRecvEntry(entries, xValues[sourceIndex], yValues[sourceIndex], floor);
        }
        return entries;
    }

    private void appendRecvEntry(List<Entry> entries, float rawTimeUs, float rawValue, float floor) {
        if (rawTimeUs <= 0f || !Float.isFinite(rawTimeUs) || !Float.isFinite(rawValue)) return;
        float amplitude = Math.max(Math.abs(rawValue), floor);
        entries.add(new Entry((float) Math.log10(rawTimeUs), (float) Math.log10(amplitude)));
    }

    private void applyRecvViewport(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) { applyRecvViewport(1f, 4f, -5f, 0f); return; }
        float minX = entries.get(0).getX(), maxX = entries.get(0).getX(), minY = entries.get(0).getY(), maxY = entries.get(0).getY();
        for (int i = 1; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            minX = Math.min(minX, entry.getX()); maxX = Math.max(maxX, entry.getX()); minY = Math.min(minY, entry.getY()); maxY = Math.max(maxY, entry.getY());
        }
        applyRecvViewport(minX, maxX, minY, maxY);
    }

    private void applyRecvViewport(float minX, float maxX, float minY, float maxY) {
        float normalizedMinX = (float) Math.floor(minX), normalizedMaxX = (float) Math.ceil(maxX);
        if (Math.abs(normalizedMaxX - normalizedMinX) < AXIS_RANGE_EPSILON) normalizedMaxX = normalizedMinX + 1f;
        float normalizedMinY = (float) Math.floor(minY), normalizedMaxY = (float) Math.ceil(maxY);
        if (Math.abs(normalizedMaxY - normalizedMinY) < AXIS_RANGE_EPSILON) normalizedMaxY = normalizedMinY + 1f;
        XAxis xAxis = chartMain.getXAxis();
        xAxis.setAxisMinimum(normalizedMinX); xAxis.setAxisMaximum(normalizedMaxX); xAxis.setLabelCount(Math.max(2, Math.round(normalizedMaxX - normalizedMinX) + 1), true);
        YAxis leftAxis = chartMain.getAxisLeft();
        leftAxis.setAxisMinimum(normalizedMinY); leftAxis.setAxisMaximum(normalizedMaxY); leftAxis.setLabelCount(Math.max(2, Math.round(normalizedMaxY - normalizedMinY) + 1), true);
        configureRecvMinorGrid(xAxis, normalizedMinX, normalizedMaxX); configureRecvMinorGrid(leftAxis, normalizedMinY, normalizedMaxY);
    }

    private void configureRecvMinorGrid(XAxis axis, float minValue, float maxValue) {
        axis.removeAllLimitLines();
        for (int exponent = (int) Math.floor(minValue); exponent < (int) Math.ceil(maxValue); exponent++) addMinorLogGridLines(axis, exponent, minValue, maxValue);
    }

    private void configureRecvMinorGrid(YAxis axis, float minValue, float maxValue) {
        axis.removeAllLimitLines();
        for (int exponent = (int) Math.floor(minValue); exponent < (int) Math.ceil(maxValue); exponent++) addMinorLogGridLines(axis, exponent, minValue, maxValue);
    }

    private void addMinorLogGridLines(XAxis axis, int exponent, float minValue, float maxValue) { for (int multiplier = 2; multiplier < 10; multiplier++) addMinorLine(axis, exponent, multiplier, minValue, maxValue); }
    private void addMinorLogGridLines(YAxis axis, int exponent, float minValue, float maxValue) { for (int multiplier = 2; multiplier < 10; multiplier++) addMinorLine(axis, exponent, multiplier, minValue, maxValue); }
    private void addMinorLine(XAxis axis, int exponent, int multiplier, float minValue, float maxValue) { float logValue = exponent + (float) Math.log10(multiplier); if (logValue > minValue + AXIS_RANGE_EPSILON && logValue < maxValue - AXIS_RANGE_EPSILON) axis.addLimitLine(createMinorGridLine(logValue)); }
    private void addMinorLine(YAxis axis, int exponent, int multiplier, float minValue, float maxValue) { float logValue = exponent + (float) Math.log10(multiplier); if (logValue > minValue + AXIS_RANGE_EPSILON && logValue < maxValue - AXIS_RANGE_EPSILON) axis.addLimitLine(createMinorGridLine(logValue)); }
    private LimitLine createMinorGridLine(float value) { LimitLine limitLine = new LimitLine(value); limitLine.setLineColor(RECV_MINOR_GRID); limitLine.setLineWidth(0.6f); limitLine.enableDashedLine(2f, 4f, 0f); return limitLine; }

    private void applyPreviewViewport(LineChart chart, List<Entry> entries, String label) {
        if (entries == null || entries.isEmpty()) return;
        float minX = entries.get(0).getX(), maxX = entries.get(0).getX(), minY = entries.get(0).getY(), maxY = entries.get(0).getY();
        for (int i = 1; i < entries.size(); i++) { Entry entry = entries.get(i); minX = Math.min(minX, entry.getX()); maxX = Math.max(maxX, entry.getX()); minY = Math.min(minY, entry.getY()); maxY = Math.max(maxY, entry.getY()); }
        applyPreviewViewport(chart, minX, maxX, minY, maxY, label);
    }

    private void applyPreviewViewport(LineChart chart, float minX, float maxX, float minY, float maxY, String label) {
        float normalizedMinX = minX, normalizedMaxX = Math.abs(maxX - minX) < AXIS_RANGE_EPSILON ? maxX + Math.max(Math.abs(maxX) * 0.05f, 1f) : maxX;
        if (LABEL_SEND.equals(label) || LABEL_OFF.equals(label)) normalizedMinX = Math.max(0f, minX);
        float normalizedMinY = minY, normalizedMaxY = maxY;
        if (LABEL_SEND.equals(label)) {
            float peak = Math.max(Math.abs(minY), Math.abs(maxY)), sendInterval = calculateSendAxisInterval(peak), sendLimit = sendInterval * 2f;
            normalizedMinY = -sendLimit; normalizedMaxY = sendLimit;
        } else if (Math.abs(maxY - minY) < AXIS_RANGE_EPSILON) {
            float yPad = Math.max(Math.abs(maxY) * 0.15f, 1f);
            normalizedMinY = minY - yPad; normalizedMaxY = maxY + yPad;
        } else {
            float yPad = (maxY - minY) * 0.12f;
            normalizedMinY = minY - yPad; normalizedMaxY = maxY + yPad;
        }
        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinimum(normalizedMinX); xAxis.setAxisMaximum(normalizedMaxX); xAxis.setGranularity(calculateGranularity(normalizedMaxX - normalizedMinX, 4)); xAxis.setValueFormatter(createTimeAxisFormatter(Math.max(Math.abs(normalizedMinX), Math.abs(normalizedMaxX))));
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(normalizedMinY); leftAxis.setAxisMaximum(normalizedMaxY); leftAxis.setLabelCount(LABEL_SEND.equals(label) ? 5 : 4, LABEL_SEND.equals(label)); leftAxis.setGranularity(LABEL_SEND.equals(label) ? calculateSendAxisInterval(Math.max(Math.abs(minY), Math.abs(maxY))) : calculateGranularity(normalizedMaxY - normalizedMinY, 4)); leftAxis.setValueFormatter(createAmplitudeFormatter(label, Math.max(Math.abs(normalizedMinY), Math.abs(normalizedMaxY))));
    }

    private float calculateGranularity(float range, int targetSteps) {
        if (range <= AXIS_RANGE_EPSILON || targetSteps <= 0) return 1f;
        double roughStep = range / targetSteps, magnitude = Math.pow(10d, Math.floor(Math.log10(roughStep))), normalized = roughStep / magnitude;
        double niceNormalized = normalized <= 1d ? 1d : normalized <= 2d ? 2d : normalized <= 5d ? 5d : 10d;
        return (float) (niceNormalized * magnitude);
    }

    private float calculateSendAxisInterval(float peak) { return calculateNiceCeil(Math.max(peak, 1f) / 2f); }

    private float calculateNiceCeil(float value) {
        if (value <= 0f) return 1f;
        double magnitude = Math.pow(10d, Math.floor(Math.log10(value))), normalized = value / magnitude;
        double niceNormalized = normalized <= 1d ? 1d : normalized <= 2d ? 2d : normalized <= 2.5d ? 2.5d : normalized <= 5d ? 5d : 10d;
        return (float) (niceNormalized * magnitude);
    }

    private ValueFormatter createTimeAxisFormatter(float maxAbsX) {
        return new ValueFormatter() {
            private final DecimalFormat decimalFormat = maxAbsX < 10f ? new DecimalFormat("0.0") : maxAbsX < 100f ? new DecimalFormat("0.#") : new DecimalFormat("0");
            @Override public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) { return decimalFormat.format(value); }
        };
    }

    private ValueFormatter createAmplitudeFormatter(String label, float maxAbsY) {
        final AxisUnit axisUnit = chooseAmplitudeUnit(LABEL_SEND.equals(label), maxAbsY);
        return new ValueFormatter() {
            private final DecimalFormat decimalFormat = LABEL_SEND.equals(label) ? (axisUnit.scale >= 1f ? (maxAbsY >= 20f ? new DecimalFormat("0") : maxAbsY >= 5f ? new DecimalFormat("0.#") : new DecimalFormat("0.##")) : new DecimalFormat("0.##")) : new DecimalFormat(axisUnit.scale >= 1f ? "0.##" : "0.#");
            @Override public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) { return decimalFormat.format(value / axisUnit.scale); }
        };
    }

    private ValueFormatter createRecvLogAxisFormatter() {
        return new ValueFormatter() {
            @Override public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                int exponent = Math.round(value);
                if (Math.abs(value - exponent) > LOG_AXIS_LABEL_TOLERANCE) return "";
                if (exponent == 0) return "1";
                if (exponent == 1) return "10";
                return "10^" + exponent;
            }
        };
    }

    private AxisUnit chooseAmplitudeUnit(boolean currentAxis, float maxAbsValue) {
        if (currentAxis) return maxAbsValue >= 1f ? new AxisUnit(1f, "A") : new AxisUnit(0.001f, "mA");
        if (maxAbsValue >= 1f) return new AxisUnit(1f, "V");
        if (maxAbsValue >= 0.001f) return new AxisUnit(0.001f, "mV");
        return new AxisUnit(0.000001f, "uV");
    }

    private static class AxisUnit { final float scale; final String suffix; AxisUnit(float scale, String suffix) { this.scale = scale; this.suffix = suffix; } }
}
