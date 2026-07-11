package cn.zjl.datacollector.ui.collection.chart;

/**
 * 阅读提示：采集图表模块代码：负责波形数据解码、状态整理和 RECV/SEND/OFF 图表绘制。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import androidx.annotation.Nullable;

import java.util.List;

import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.util.WaveformCodec;

/**
 * 负责把数据库中的二进制波形记录解析成图表层可直接使用的数据模型。
 */
public final class WaveformDecodeHelper {

    public WaveformChartData buildLiveChartData(@Nullable float[] recvTimes,
                                                @Nullable float[] recvValues,
                                                @Nullable float[] sendTimes,
                                                @Nullable float[] sendValues,
                                                @Nullable float[] offTimes,
                                                @Nullable float[] offValues) {
        WaveformChartData chartData = new WaveformChartData();
        chartData.recv = createChannel(recvTimes, recvValues);
        chartData.send = createChannel(sendTimes, sendValues);
        chartData.off = createChannel(offTimes, offValues);
        return chartData;
    }

    public WaveformChartData buildSessionChartData(@Nullable List<WaveformDataEntity> waveforms) {
        WaveformChartData chartData = new WaveformChartData();
        if (waveforms == null || waveforms.isEmpty()) {
            return chartData;
        }

        // 同一次采集可能拆成多行存储，这里汇总成一组 Recv / Send / Off。
        for (WaveformDataEntity waveform : waveforms) {
            WaveformChartData.WaveformChannelData recvChannel = decodeRecvChannel(waveform);
            if (recvChannel.hasData()) {
                chartData.recv = recvChannel;
            }

            WaveformChartData.WaveformChannelData sendChannel = decodeSendChannel(waveform);
            if (sendChannel.hasData()) {
                chartData.send = sendChannel;
            }

            WaveformChartData.WaveformChannelData offChannel = decodeOffChannel(waveform);
            if (offChannel.hasData()) {
                chartData.off = offChannel;
            }
        }
        return chartData;
    }

    public WaveformChartData buildAggregateChartData(@Nullable List<WaveformDataEntity> waveforms) {
        WaveformChartData chartData = new WaveformChartData();
        if (waveforms == null || waveforms.isEmpty()) {
            return chartData;
        }

        for (WaveformDataEntity waveform : waveforms) {
            appendIfHasData(chartData.aggregateRecv, decodeRecvChannel(waveform));
            appendIfHasData(chartData.aggregateSend, decodeSendChannel(waveform));
            appendIfHasData(chartData.aggregateOff, decodeOffChannel(waveform));
        }
        return chartData;
    }

    private void appendIfHasData(List<WaveformChartData.WaveformChannelData> target,
                                 WaveformChartData.WaveformChannelData channel) {
        if (channel.hasData()) {
            target.add(channel);
        }
    }

    private WaveformChartData.WaveformChannelData decodeRecvChannel(@Nullable WaveformDataEntity waveform) {
        return createChannel(
                WaveformCodec.extractRecvTimeAxis(waveform),
                WaveformCodec.extractRecvValues(waveform));
    }

    private WaveformChartData.WaveformChannelData decodeSendChannel(@Nullable WaveformDataEntity waveform) {
        float[] values = WaveformCodec.extractSendValues(waveform);
        return createChannel(
                buildSendPeriodAxis(waveform, values.length),
                values);
    }

    private WaveformChartData.WaveformChannelData decodeOffChannel(@Nullable WaveformDataEntity waveform) {
        float[] values = WaveformCodec.extractOffValues(waveform);
        return createChannel(
                WaveformCodec.extractOffTimeAxis(waveform, values.length),
                values);
    }

    private WaveformChartData.WaveformChannelData createChannel(@Nullable float[] times,
                                                                @Nullable float[] values) {
        WaveformChartData.WaveformChannelData channel = new WaveformChartData.WaveformChannelData();
        channel.times = times == null ? new float[0] : times;
        channel.values = values == null ? new float[0] : values;
        return channel;
    }

    private float[] buildSendPeriodAxis(@Nullable WaveformDataEntity waveform, int count) {
        if (count <= 0) {
            return new float[0];
        }
        float sendFrequencyHz = waveform != null && waveform.getSendFs() > 0f ? waveform.getSendFs() : 25f;
        float[] axis = new float[count];
        if (count == 1) {
            axis[0] = 0f;
            return axis;
        }
        float periodMs = 1000f / sendFrequencyHz;
        for (int i = 0; i < count; i++) {
            axis[i] = periodMs * i / (count - 1);
        }
        return axis;
    }
}
