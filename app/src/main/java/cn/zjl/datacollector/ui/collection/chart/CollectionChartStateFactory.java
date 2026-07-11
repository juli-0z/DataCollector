package cn.zjl.datacollector.ui.collection.chart;

/**
 * 阅读提示：采集图表模块代码：负责波形数据解码、状态整理和 RECV/SEND/OFF 图表绘制。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import androidx.annotation.Nullable;

import java.util.List;

import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.ui.collection.screen.CollectionViewModel;

/**
 * 负责把实时波形或数据库波形映射成页面可消费的图表状态。
 */
public class CollectionChartStateFactory {

    public static class LiveWaveformResult {
        public float[] recvTimeAxis = new float[0];
        public float[] sendTimeAxis = new float[0];
        public float[] offTimeAxis = new float[0];
        public float[] recvValues = new float[0];
        public float[] sendValues = new float[0];
        public float[] offValues = new float[0];
        public boolean hasNewData;
        public CollectionViewModel.ChartDisplayState chartState =
                new CollectionViewModel.ChartDisplayState();
    }

    private final WaveformSessionResolver waveformSessionResolver;
    private final WaveformDecodeHelper waveformDecodeHelper;

    public CollectionChartStateFactory(WaveformSessionResolver waveformSessionResolver) {
        this(waveformSessionResolver, new WaveformDecodeHelper());
    }

    CollectionChartStateFactory(WaveformSessionResolver waveformSessionResolver,
                                WaveformDecodeHelper waveformDecodeHelper) {
        this.waveformSessionResolver = waveformSessionResolver;
        this.waveformDecodeHelper = waveformDecodeHelper;
    }

    public LiveWaveformResult processIncomingWaveform(
            @Nullable float[] timePoints,
            @Nullable float[] recvValues,
            @Nullable float[] sendValues,
            @Nullable float[] offValues,
            int currentSampleFrequency,
            float currentSendFrequency,
            float auxiliarySampleFrequency) {
        LiveWaveformResult result = new LiveWaveformResult();

        float[] baseRecvAxis = trimAxis(timePoints, recvValues, sendValues, offValues);
        result.recvValues = trimValues(recvValues, baseRecvAxis.length);
        result.sendValues = trimValues(sendValues, effectiveLength(sendValues));
        result.offValues = trimValues(offValues, effectiveLength(offValues));
        result.recvTimeAxis = baseRecvAxis;
        result.sendTimeAxis = buildSendPeriodAxis(
                result.sendValues.length,
                parametersSafeSendFrequency(currentSendFrequency),
                auxiliarySampleFrequency,
                currentSampleFrequency);
        result.offTimeAxis = buildUniformTimeAxis(
                result.offValues.length,
                auxiliarySampleFrequency,
                currentSampleFrequency);
        result.hasNewData = result.recvValues.length > 0
                || result.sendValues.length > 0
                || result.offValues.length > 0;

        CollectionViewModel.ChartDisplayState chartState = new CollectionViewModel.ChartDisplayState();
        chartState.recvFs = currentSampleFrequency;
        chartState.sendFs = currentSendFrequency;
        chartState.sampleSendFs = auxiliarySampleFrequency;
        chartState.sampleOffFs = auxiliarySampleFrequency;
        chartState.waveformData = waveformDecodeHelper.buildLiveChartData(
                result.recvTimeAxis,
                result.recvValues,
                result.sendTimeAxis,
                result.sendValues,
                result.offTimeAxis,
                result.offValues);
        result.chartState = chartState;
        return result;
    }

    public CollectionViewModel.ChartDisplayState buildAggregateChartState(@Nullable List<WaveformDataEntity> waveforms) {
        CollectionViewModel.ChartDisplayState chartState = new CollectionViewModel.ChartDisplayState();
        chartState.aggregateMode = true;
        chartState.waveformData = waveformDecodeHelper.buildAggregateChartData(waveforms);
        applyWaveformMeta(chartState, waveforms);
        return chartState;
    }

    public CollectionViewModel.ChartDisplayState buildSessionChartState(
            @Nullable List<WaveformDataEntity> waveforms,
            int sessionIndex) {
        WaveformSessionResolver.WaveformSession session =
                waveformSessionResolver.selectWaveformSession(waveforms, sessionIndex);
        if (session == null) {
            return new CollectionViewModel.ChartDisplayState();
        }

        CollectionViewModel.ChartDisplayState chartState = new CollectionViewModel.ChartDisplayState();
        chartState.waveformData = waveformDecodeHelper.buildSessionChartData(session.waveforms);
        applyWaveformMeta(chartState, session.waveforms);
        return chartState;
    }

    public float resolveAuxiliarySampleFrequency(@Nullable cn.zjl.datacollector.data.entity.CollectionParameterEntity parameters) {
        if (parameters == null) {
            return 0f;
        }
        if (parameters.getSampleTime() > 0f) {
            return 1_000_000f / parameters.getSampleTime();
        }
        if (parameters.getSampleFrequency() > 0) {
            return parameters.getSampleFrequency();
        }
        return 0f;
    }

    private void applyWaveformMeta(@Nullable CollectionViewModel.ChartDisplayState chartState,
                                   @Nullable List<WaveformDataEntity> waveforms) {
        if (chartState == null || waveforms == null || waveforms.isEmpty()) {
            return;
        }
        for (int i = waveforms.size() - 1; i >= 0; i--) {
            WaveformDataEntity waveform = waveforms.get(i);
            if (waveform == null) {
                continue;
            }
            if (chartState.recvFs <= 0f && waveform.getRecvFs() > 0f) {
                chartState.recvFs = waveform.getRecvFs();
            }
            if (chartState.sendFs <= 0f && waveform.getSendFs() > 0f) {
                chartState.sendFs = waveform.getSendFs();
            }
            if (chartState.sampleSendFs <= 0f && waveform.getSimpleSendFs() > 0f) {
                chartState.sampleSendFs = waveform.getSimpleSendFs();
            }
            if (chartState.sampleOffFs <= 0f && waveform.getSimpleOffFs() > 0f) {
                chartState.sampleOffFs = waveform.getSimpleOffFs();
            }
            if (chartState.period <= 0 && waveform.getPeriod() > 0) {
                chartState.period = waveform.getPeriod();
            }
        }
        chartState.repeatCount = waveformSessionResolver.buildWaveformSessions(waveforms).size();
        if (chartState.repeatCount < 0) {
            chartState.repeatCount = 0;
        }
    }

    private float[] trimAxis(@Nullable float[] axis,
                             @Nullable float[] recvValues,
                             @Nullable float[] sendValues,
                             @Nullable float[] offValues) {
        int length = Math.max(
                effectiveLength(axis),
                Math.max(effectiveLength(recvValues), Math.max(effectiveLength(sendValues), effectiveLength(offValues))));
        if (length <= 0 || axis == null || axis.length == 0) {
            return new float[0];
        }
        float[] trimmed = new float[Math.min(length, axis.length)];
        System.arraycopy(axis, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private float[] trimValues(@Nullable float[] values, int length) {
        if (values == null || values.length == 0 || length <= 0) {
            return new float[0];
        }
        int safeLength = Math.min(length, values.length);
        float[] trimmed = new float[safeLength];
        System.arraycopy(values, 0, trimmed, 0, safeLength);
        return trimmed;
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

    private float[] buildUniformTimeAxis(int count, float sampleRateHz, int currentSampleFrequency) {
        if (count <= 0) {
            return new float[0];
        }
        float sampleFrequency = sampleRateHz > 0f
                ? sampleRateHz
                : (currentSampleFrequency > 0 ? currentSampleFrequency : 300f);
        float stepUs = sampleFrequency > 0f ? 1_000_000f / sampleFrequency : 1f;
        float[] axis = new float[count];
        for (int i = 0; i < count; i++) {
            axis[i] = i * stepUs;
        }
        return axis;
    }

    private float[] buildSendPeriodAxis(int count,
                                        float sendFrequencyHz,
                                        float sampleRateHz,
                                        int currentSampleFrequency) {
        if (count <= 0) {
            return new float[0];
        }
        float safeSendFrequency = sendFrequencyHz > 0f ? sendFrequencyHz : 25f;
        float[] axis = new float[count];
        if (count == 1) {
            axis[0] = 0f;
            return axis;
        }
        float periodMs = 1000f / safeSendFrequency;
        for (int i = 0; i < count; i++) {
            axis[i] = periodMs * i / (count - 1);
        }
        return axis;
    }

    private float parametersSafeSendFrequency(float sendFrequency) {
        return sendFrequency > 0f ? sendFrequency : 25f;
    }
}
