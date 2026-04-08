package cn.zjl.datacollector.ui.collection;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.util.WaveformCodec;

/**
 * 负责三张波形图的初始化、数据转换和绘制。
 * Activity 只负责把“当前要显示哪些数据”交给它，不再处理具体图表细节。
 */
public class WaveformChartRenderer {

    private static final int MAX_MAIN_CHART_POINTS = 1200;
    private static final int MAX_PREVIEW_CHART_POINTS = 600;
    private static final float AXIS_RANGE_EPSILON = 1e-6f;
    private static final float RECV_LOG_EPSILON = 1.0e-12f;
    private static final float LOG_AXIS_LABEL_TOLERANCE = 0.18f;

    private static final String LABEL_RECV = "Recv";
    private static final String LABEL_SEND = "Send";
    private static final String LABEL_OFF = "Off";

    private final Context context;
    private final LineChart chartMain;
    private final LineChart chartSend;
    private final LineChart chartOff;

    public WaveformChartRenderer(Context context,
                                 LineChart chartMain,
                                 LineChart chartSend,
                                 LineChart chartOff) {
        this.context = context;
        this.chartMain = chartMain;
        this.chartSend = chartSend;
        this.chartOff = chartOff;
    }

    public void initCharts() {
        setupChart(chartMain, LABEL_RECV);
        setupChart(chartSend, LABEL_SEND);
        setupChart(chartOff, LABEL_OFF);
        renderWaveformPanels(new float[0], new float[0], new float[0], new float[0], new float[0], new float[0]);
    }

    public void renderWaveformPanels(float[] recvTimes,
                                     float[] recvValues,
                                     float[] sendTimes,
                                     float[] sendValues,
                                     float[] offTimes,
                                     float[] offValues) {
        renderChart(chartMain, recvTimes, recvValues, LABEL_RECV);
        renderChart(chartSend, sendTimes, sendValues, LABEL_SEND);
        renderChart(chartOff, offTimes, offValues, LABEL_OFF);
    }

    public void renderAggregateWaveforms(List<WaveformDataEntity> waveforms) {
        // 点击测点时叠加该点全部采集次数；点击测线时叠加整条测线的全部波形。
        renderAggregateChart(chartMain, buildAggregateDataSets(waveforms, LABEL_RECV), LABEL_RECV);
        renderAggregateChart(chartSend, buildAggregateDataSets(waveforms, LABEL_SEND), LABEL_SEND);
        renderAggregateChart(chartOff, buildAggregateDataSets(waveforms, LABEL_OFF), LABEL_OFF);
    }

    private void setupChart(LineChart chart, String label) {
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
        chart.setHighlightPerDragEnabled(chart == chartMain);
        chart.setDragDecelerationEnabled(true);
        chart.setDragDecelerationFrictionCoef(0.9f);

        Legend legend = chart.getLegend();
        legend.setEnabled(false);
        legend.setTextColor(color(R.color.text_secondary));
        legend.setTextSize(chart == chartMain ? 11f : 10f);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setFormLineWidth(chart == chartMain ? 2.2f : 1.6f);
        legend.setXEntrySpace(10f);
        legend.setYEntrySpace(4f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(color(R.color.divider));
        leftAxis.setGridLineWidth(0.8f);
        leftAxis.enableGridDashedLine(6f, 6f, 0f);
        leftAxis.setTextColor(color(R.color.text_secondary));
        leftAxis.setTextSize(chart == chartMain ? 11f : 10f);
        leftAxis.setDrawAxisLine(LABEL_SEND.equals(label));
        leftAxis.setAxisLineColor(color(R.color.divider));
        leftAxis.setAxisLineWidth(LABEL_SEND.equals(label) ? 1f : 0.9f);
        leftAxis.setLabelCount(LABEL_SEND.equals(label) ? 5 : (chart == chartMain ? 6 : 4), LABEL_SEND.equals(label));
        leftAxis.setSpaceTop(14f);
        leftAxis.setSpaceBottom(10f);
        leftAxis.setDrawZeroLine(LABEL_SEND.equals(label));
        if (LABEL_SEND.equals(label)) {
            leftAxis.setZeroLineColor(color(R.color.text_secondary));
            leftAxis.setZeroLineWidth(1f);
        }
        leftAxis.setValueFormatter(LABEL_RECV.equals(label)
                ? createRecvLogAxisFormatter()
                : createAmplitudeFormatter(label, 1f));

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(color(R.color.divider));
        xAxis.setGridLineWidth(0.7f);
        xAxis.enableGridDashedLine(6f, 6f, 0f);
        xAxis.setAxisLineColor(color(R.color.divider));
        xAxis.setAxisLineWidth(0.9f);
        xAxis.setTextColor(color(R.color.text_secondary));
        xAxis.setTextSize(chart == chartMain ? 11f : 10f);
        xAxis.setGranularityEnabled(true);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setLabelCount(chart == chartMain ? 7 : 5, false);
        xAxis.setYOffset(6f);
        xAxis.setValueFormatter(LABEL_RECV.equals(label)
                ? createRecvLogAxisFormatter()
                : createTimeAxisFormatter(1f));
    }

    private List<ILineDataSet> buildAggregateDataSets(List<WaveformDataEntity> waveforms, String label) {
        List<ILineDataSet> dataSets = new ArrayList<>();
        if (waveforms == null || waveforms.isEmpty()) {
            return dataSets;
        }

        int baseColor = resolveChartColor(label);
        int index = 0;
        for (WaveformDataEntity waveform : waveforms) {
            float[] xValues;
            float[] yValues;
            if (LABEL_RECV.equals(label)) {
                xValues = WaveformCodec.extractRecvTimeAxis(waveform);
                yValues = WaveformCodec.extractRecvValues(waveform);
            } else if (LABEL_SEND.equals(label)) {
                yValues = WaveformCodec.extractSendValues(waveform);
                xValues = WaveformCodec.extractSendTimeAxis(waveform, yValues.length);
            } else {
                yValues = WaveformCodec.extractOffValues(waveform);
                xValues = WaveformCodec.extractOffTimeAxis(waveform, yValues.length);
            }

            int count = Math.min(xValues != null ? xValues.length : 0, yValues != null ? yValues.length : 0);
            if (count <= 0) {
                continue;
            }

            List<Entry> entries = LABEL_RECV.equals(label)
                    ? buildRecvChartEntries(xValues, yValues, MAX_MAIN_CHART_POINTS)
                    : buildChartEntries(xValues, yValues, MAX_PREVIEW_CHART_POINTS);
            if (entries.isEmpty()) {
                continue;
            }

            LineDataSet dataSet = new LineDataSet(entries, label + "_" + index);
            styleDataSet(dataSet, label, baseColor, true);
            dataSets.add(dataSet);
            index++;
        }
        return dataSets;
    }

    private void renderAggregateChart(LineChart chart, List<ILineDataSet> dataSets, String label) {
        if (dataSets == null || dataSets.isEmpty()) {
            chart.clear();
            chart.setNoDataText(context.getString(R.string.chart_no_data, label));
            chart.invalidate();
            return;
        }
        chart.getLegend().setEnabled(false);
        LineData lineData = new LineData(dataSets);
        chart.setData(lineData);
        applyChartViewport(chart, lineData.getXMin(), lineData.getXMax(), lineData.getYMin(), lineData.getYMax(), label);
        chart.invalidate();
    }

    private void renderChart(LineChart chart, float[] xValues, float[] yValues, String label) {
        int count = Math.min(xValues != null ? xValues.length : 0, yValues != null ? yValues.length : 0);
        if (count <= 0) {
            chart.clear();
            chart.setNoDataText(context.getString(R.string.chart_no_data, label));
            chart.invalidate();
            return;
        }

        List<Entry> entries = LABEL_RECV.equals(label)
                ? buildRecvChartEntries(xValues, yValues, MAX_MAIN_CHART_POINTS)
                : buildChartEntries(
                xValues,
                yValues,
                chart == chartMain ? MAX_MAIN_CHART_POINTS : MAX_PREVIEW_CHART_POINTS);
        if (entries.isEmpty()) {
            chart.clear();
            chart.setNoDataText(context.getString(R.string.chart_no_data, label));
            chart.invalidate();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, label);
        styleDataSet(dataSet, label, resolveChartColor(label), false);
        chart.setData(new LineData(dataSet));
        applyChartViewport(chart, entries, label);
        if (chart == chartMain) {
            chart.animateX(180);
        }
        chart.invalidate();
    }

    private void styleDataSet(LineDataSet dataSet, String label, int baseColor, boolean aggregateMode) {
        dataSet.setDrawCircles(LABEL_RECV.equals(label));
        dataSet.setCircleRadius(LABEL_RECV.equals(label) ? 2.2f : 0f);
        dataSet.setDrawCircleHole(LABEL_RECV.equals(label));
        dataSet.setCircleHoleRadius(LABEL_RECV.equals(label) ? 1f : 0f);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);

        if (LABEL_RECV.equals(label)) {
            dataSet.setLineWidth(0f);
            dataSet.setColor(Color.TRANSPARENT);
            dataSet.setCircleColor(aggregateMode
                    ? ColorUtils.setAlphaComponent(baseColor, 180)
                    : baseColor);
            dataSet.setCircleHoleColor(Color.WHITE);
            return;
        }

        dataSet.setLineWidth(aggregateMode ? 1f : (LABEL_SEND.equals(label) || LABEL_OFF.equals(label)) ? 1.6f : 2.5f);
        dataSet.setColor(aggregateMode
                ? ColorUtils.setAlphaComponent(baseColor, 130)
                : baseColor);
    }

    private int resolveChartColor(String label) {
        if (LABEL_RECV.equals(label)) {
            return color(R.color.chart_recv);
        }
        if (LABEL_SEND.equals(label)) {
            return color(R.color.chart_send);
        }
        return color(R.color.chart_off);
    }

    private List<Entry> buildChartEntries(float[] xValues, float[] yValues, int maxPoints) {
        int count = Math.min(xValues != null ? xValues.length : 0, yValues != null ? yValues.length : 0);
        List<Entry> entries = new ArrayList<>(Math.min(count, maxPoints));
        if (count <= maxPoints) {
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(xValues[i], yValues[i]));
            }
            return entries;
        }

        float lastIndex = count - 1f;
        for (int i = 0; i < maxPoints; i++) {
            int sampledIndex = Math.round((i * lastIndex) / (maxPoints - 1f));
            entries.add(new Entry(xValues[sampledIndex], yValues[sampledIndex]));
        }
        return entries;
    }

    private List<Entry> buildRecvChartEntries(float[] xValues, float[] yValues, int maxPoints) {
        int count = Math.min(xValues != null ? xValues.length : 0, yValues != null ? yValues.length : 0);
        List<Entry> entries = new ArrayList<>(Math.min(count, maxPoints));
        if (count <= 0) {
            return entries;
        }

        // Recv 图使用对数坐标：
        // X 轴是 log10(时间/us)，Y 轴是 log10(|幅值|)。
        // 这里先找到最小正幅值，避免出现 log(0)。
        float minPositive = Float.MAX_VALUE;
        int validCount = 0;
        for (int i = 0; i < count; i++) {
            float xValue = xValues[i];
            float yValue = yValues[i];
            float amplitude = Math.abs(yValue);
            if (xValue > 0f && Float.isFinite(xValue) && Float.isFinite(yValue)) {
                validCount++;
            }
            if (xValue > 0f && amplitude > 0f && Float.isFinite(amplitude)) {
                minPositive = Math.min(minPositive, amplitude);
            }
        }
        if (validCount == 0) {
            return entries;
        }

        float floor = minPositive == Float.MAX_VALUE
                ? RECV_LOG_EPSILON
                : Math.max(RECV_LOG_EPSILON, minPositive * 0.5f);

        if (validCount <= maxPoints) {
            for (int i = 0; i < count; i++) {
                appendRecvEntry(entries, xValues[i], yValues[i], floor);
            }
            return entries;
        }

        // 点数过多时做等比例抽样，减少绘图压力。
        int[] validIndices = new int[validCount];
        int validPointer = 0;
        for (int i = 0; i < count; i++) {
            float xValue = xValues[i];
            float yValue = yValues[i];
            if (xValue > 0f && Float.isFinite(xValue) && Float.isFinite(yValue)) {
                validIndices[validPointer++] = i;
            }
        }

        float lastIndex = validCount - 1f;
        for (int i = 0; i < maxPoints; i++) {
            int sampledIndex = Math.round((i * lastIndex) / (maxPoints - 1f));
            int sourceIndex = validIndices[sampledIndex];
            appendRecvEntry(entries, xValues[sourceIndex], yValues[sourceIndex], floor);
        }
        return entries;
    }

    private void appendRecvEntry(List<Entry> entries, float rawTimeUs, float rawValue, float floor) {
        if (rawTimeUs <= 0f || !Float.isFinite(rawTimeUs) || !Float.isFinite(rawValue)) {
            return;
        }

        float amplitude = Math.max(Math.abs(rawValue), floor);
        entries.add(new Entry(
                (float) Math.log10(rawTimeUs),
                (float) Math.log10(amplitude)));
    }

    private void applyChartViewport(LineChart chart, List<Entry> entries, String label) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        float minX = entries.get(0).getX();
        float maxX = entries.get(0).getX();
        float minY = entries.get(0).getY();
        float maxY = entries.get(0).getY();
        for (int i = 1; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            minX = Math.min(minX, entry.getX());
            maxX = Math.max(maxX, entry.getX());
            minY = Math.min(minY, entry.getY());
            maxY = Math.max(maxY, entry.getY());
        }

        applyChartViewport(chart, minX, maxX, minY, maxY, label);
    }

    private void applyChartViewport(LineChart chart,
                                    float minX,
                                    float maxX,
                                    float minY,
                                    float maxY,
                                    String label) {
        float normalizedMinX = minX;
        float normalizedMaxX = maxX;
        if (Math.abs(maxX - minX) < AXIS_RANGE_EPSILON) {
            float xPad = Math.max(Math.abs(maxX) * 0.05f, 1f);
            normalizedMinX = minX;
            normalizedMaxX = maxX + xPad;
        } else if (LABEL_RECV.equals(label)) {
            float xPad = Math.max((maxX - minX) * 0.03f, 0.04f);
            normalizedMinX = minX - xPad;
            normalizedMaxX = maxX + xPad;
        } else if (LABEL_SEND.equals(label) || LABEL_OFF.equals(label)) {
            normalizedMinX = Math.max(0f, minX);
            normalizedMaxX = maxX;
        }

        float normalizedMinY = minY;
        float normalizedMaxY = maxY;
        if (LABEL_RECV.equals(label)) {
            float yPad = Math.max((maxY - minY) * 0.06f, 0.08f);
            normalizedMinY = minY - yPad;
            normalizedMaxY = maxY + yPad;
            if (Math.abs(normalizedMaxY - normalizedMinY) < 0.5f) {
                normalizedMaxY = normalizedMinY + 0.5f;
            }
        } else if (LABEL_SEND.equals(label)) {
            // Send 图强调双极性电流脉冲，纵轴围绕 0 对称，并优先使用规整刻度。
            float peak = Math.max(Math.abs(minY), Math.abs(maxY));
            float sendInterval = calculateSendAxisInterval(peak);
            float sendLimit = sendInterval * 2f;
            normalizedMinY = -sendLimit;
            normalizedMaxY = sendLimit;
        } else if (Math.abs(maxY - minY) < AXIS_RANGE_EPSILON) {
            float yPad = Math.max(Math.abs(maxY) * 0.15f, 1f);
            normalizedMinY = minY - yPad;
            normalizedMaxY = maxY + yPad;
        } else {
            float yPad = (maxY - minY) * 0.12f;
            normalizedMinY = minY - yPad;
            normalizedMaxY = maxY + yPad;
        }

        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinimum(normalizedMinX);
        xAxis.setAxisMaximum(normalizedMaxX);
        xAxis.setGranularity(LABEL_RECV.equals(label)
                ? 1f
                : calculateGranularity(normalizedMaxX - normalizedMinX, chart == chartMain ? 6 : 4));
        xAxis.setValueFormatter(LABEL_RECV.equals(label)
                ? createRecvLogAxisFormatter()
                : createTimeAxisFormatter(Math.max(Math.abs(normalizedMinX), Math.abs(normalizedMaxX))));

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(normalizedMinY);
        leftAxis.setAxisMaximum(normalizedMaxY);
        leftAxis.setLabelCount(LABEL_SEND.equals(label) ? 5 : (chart == chartMain ? 6 : 4), LABEL_SEND.equals(label));
        leftAxis.setGranularity(LABEL_RECV.equals(label)
                ? 1f
                : LABEL_SEND.equals(label)
                ? calculateSendAxisInterval(Math.max(Math.abs(minY), Math.abs(maxY)))
                : calculateGranularity(normalizedMaxY - normalizedMinY, chart == chartMain ? 6 : 4));
        leftAxis.setValueFormatter(LABEL_RECV.equals(label)
                ? createRecvLogAxisFormatter()
                : createAmplitudeFormatter(label, Math.max(Math.abs(normalizedMinY), Math.abs(normalizedMaxY))));
    }

    private float calculateGranularity(float range, int targetSteps) {
        if (range <= AXIS_RANGE_EPSILON || targetSteps <= 0) {
            return 1f;
        }

        double roughStep = range / targetSteps;
        double magnitude = Math.pow(10d, Math.floor(Math.log10(roughStep)));
        double normalized = roughStep / magnitude;
        double niceNormalized;
        if (normalized <= 1d) {
            niceNormalized = 1d;
        } else if (normalized <= 2d) {
            niceNormalized = 2d;
        } else if (normalized <= 5d) {
            niceNormalized = 5d;
        } else {
            niceNormalized = 10d;
        }
        return (float) (niceNormalized * magnitude);
    }

    private float calculateSendAxisInterval(float peak) {
        float normalizedPeak = Math.max(peak, 1f);
        return calculateNiceCeil(normalizedPeak / 2f);
    }

    private float calculateNiceCeil(float value) {
        if (value <= 0f) {
            return 1f;
        }

        // 把数值向上规整到工程上更好读的档位，避免出现 21 / 42 这样的刻度。
        double magnitude = Math.pow(10d, Math.floor(Math.log10(value)));
        double normalized = value / magnitude;
        double niceNormalized;
        if (normalized <= 1d) {
            niceNormalized = 1d;
        } else if (normalized <= 2d) {
            niceNormalized = 2d;
        } else if (normalized <= 2.5d) {
            niceNormalized = 2.5d;
        } else if (normalized <= 5d) {
            niceNormalized = 5d;
        } else {
            niceNormalized = 10d;
        }
        return (float) (niceNormalized * magnitude);
    }

    private ValueFormatter createTimeAxisFormatter(float maxAbsX) {
        return new ValueFormatter() {
            private final DecimalFormat decimalFormat = createTimeAxisDecimalFormat(maxAbsX);

            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                return decimalFormat.format(value) + " us";
            }
        };
    }

    private DecimalFormat createTimeAxisDecimalFormat(float maxAbsX) {
        if (maxAbsX < 10f) {
            return new DecimalFormat("0.0");
        }
        if (maxAbsX < 100f) {
            return new DecimalFormat("0.#");
        }
        return new DecimalFormat("0");
    }

    private ValueFormatter createAmplitudeFormatter(String label, float maxAbsY) {
        final boolean currentAxis = LABEL_SEND.equals(label);
        final AxisUnit axisUnit = chooseAmplitudeUnit(currentAxis, maxAbsY);
        return new ValueFormatter() {
            private final DecimalFormat decimalFormat = createAmplitudeDecimalFormat(label, axisUnit, maxAbsY);

            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                return decimalFormat.format(value / axisUnit.scale) + " " + axisUnit.suffix;
            }
        };
    }

    private DecimalFormat createAmplitudeDecimalFormat(String label, AxisUnit axisUnit, float maxAbsY) {
        if (LABEL_SEND.equals(label)) {
            if (axisUnit.scale >= 1f) {
                if (maxAbsY >= 20f) {
                    return new DecimalFormat("0");
                }
                if (maxAbsY >= 5f) {
                    return new DecimalFormat("0.#");
                }
            }
            return new DecimalFormat("0.##");
        }
        return new DecimalFormat(axisUnit.scale >= 1f ? "0.##" : "0.#");
    }

    private ValueFormatter createRecvLogAxisFormatter() {
        return new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                int exponent = Math.round(value);
                if (Math.abs(value - exponent) > LOG_AXIS_LABEL_TOLERANCE) {
                    return "";
                }
                if (exponent == 0) {
                    return "1";
                }
                if (exponent == 1) {
                    return "10";
                }
                return "10^" + exponent;
            }
        };
    }

    private AxisUnit chooseAmplitudeUnit(boolean currentAxis, float maxAbsValue) {
        if (currentAxis) {
            if (maxAbsValue >= 1f) {
                return new AxisUnit(1f, "A");
            }
            return new AxisUnit(0.001f, "mA");
        }

        if (maxAbsValue >= 1f) {
            return new AxisUnit(1f, "V");
        }
        if (maxAbsValue >= 0.001f) {
            return new AxisUnit(0.001f, "mV");
        }
        return new AxisUnit(0.000001f, "uV");
    }

    private int color(int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    private static class AxisUnit {
        final float scale;
        final String suffix;

        AxisUnit(float scale, String suffix) {
            this.scale = scale;
            this.suffix = suffix;
        }
    }
}
