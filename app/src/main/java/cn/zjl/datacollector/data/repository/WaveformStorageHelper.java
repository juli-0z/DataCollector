package cn.zjl.datacollector.data.repository;

/**
 * 阅读提示：数据仓库类：把 DAO 的底层读写封装成界面和业务层更容易调用的数据操作。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;

/**
 * 负责把页面采集结果转换为数据库中 data_sample 表所需的字段结构。
 */
final class WaveformStorageHelper {

    List<WaveformDataEntity> buildWaveforms(long pointId,
                                            CollectionParameterEntity parameters,
                                            float[] timeAxis,
                                            float[] recvValues,
                                            float[] sendValues,
                                            float[] offValues,
                                            long timestamp) {
        List<WaveformDataEntity> waveforms = new ArrayList<>();
        waveforms.add(buildWaveform(pointId, 0, "Recv", timeAxis, recvValues, null, null, parameters, timestamp));
        waveforms.add(buildWaveform(pointId, 1, "Send", timeAxis, null, sendValues, null, parameters, timestamp));
        waveforms.add(buildWaveform(pointId, 2, "Off", timeAxis, null, null, offValues, parameters, timestamp));
        return waveforms;
    }

    private WaveformDataEntity buildWaveform(long pointId,
                                             int type,
                                             String note,
                                             float[] timeAxis,
                                             float[] recvValues,
                                             float[] sendValues,
                                             float[] offValues,
                                             CollectionParameterEntity parameters,
                                             long timestamp) {
        float sendFrequencyHz = resolveSendFrequencyHz(parameters);
        float recvFrequencyHz = resolveRecvFrequencyHz(parameters);
        float waveformSampleRateHz = resolveWaveformSampleRateHz(parameters);
        float[] recvWindowLengths = buildRecvWindowLengths(timeAxis, parameters != null ? parameters.getSampleTime() : 0f);

        WaveformDataEntity waveform = new WaveformDataEntity();
        waveform.setDataPointId(pointId);
        waveform.setType(type);
        waveform.setNote(note);
        waveform.setStartTime(timestamp);
        // 当前表单中的 collectionCount 对齐到 Data_Sample.PERIOD。
        waveform.setPeriod(resolvePeriod(parameters));
        waveform.setDataRecvPos(encodeFloatArray(timeAxis));
        waveform.setDataRecvLen(encodeFloatArray(recvWindowLengths));
        waveform.setDataRecv(recvValues != null ? encodeFloatArray(recvValues) : null);
        waveform.setDataSend(sendValues != null ? encodeFloatArray(sendValues) : null);
        waveform.setDataSoff(offValues != null ? encodeFloatArray(offValues) : null);
        // 当前表单语义：
        // transmitCurrent -> SendFs（发送频率）
        // sampleFrequency -> RecvFs（接收采样频率）
        // sampleTime(单位 us) -> SampleSendFs / SampleOffFs 的步长换算依据
        waveform.setRecvFs(recvFrequencyHz);
        waveform.setSendFs(sendFrequencyHz);
        waveform.setSimpleSendFs(waveformSampleRateHz);
        waveform.setSimpleOffFs(waveformSampleRateHz);
        waveform.setUse(1);
        waveform.setCreatedAt(timestamp);
        return waveform;
    }

    private int resolvePeriod(CollectionParameterEntity parameters) {
        if (parameters == null || parameters.getCollectionCount() <= 0) {
            return 1;
        }
        return parameters.getCollectionCount();
    }

    private float resolveSendFrequencyHz(CollectionParameterEntity parameters) {
        if (parameters == null || parameters.getTransmitCurrent() <= 0f) {
            return 0f;
        }
        return parameters.getTransmitCurrent();
    }

    private float resolveRecvFrequencyHz(CollectionParameterEntity parameters) {
        if (parameters == null || parameters.getSampleFrequency() <= 0) {
            return 0f;
        }
        return parameters.getSampleFrequency();
    }

    private float resolveWaveformSampleRateHz(CollectionParameterEntity parameters) {
        if (parameters == null) {
            return 0f;
        }
        // sampleTime 约定为 us，数据库里的 SampleSendFs / SampleOffFs 需要的是 Hz。
        if (parameters.getSampleTime() > 0f) {
            return 1_000_000f / parameters.getSampleTime();
        }
        if (parameters.getSampleFrequency() > 0) {
            return parameters.getSampleFrequency();
        }
        return 0f;
    }

    private float[] buildRecvWindowLengths(float[] timeAxis, float fallbackSampleTimeUs) {
        if (timeAxis == null || timeAxis.length == 0) {
            return new float[0];
        }

        float[] values = new float[timeAxis.length];
        if (timeAxis.length == 1) {
            values[0] = sanitizeWindowLength(fallbackSampleTimeUs, 0f);
            return values;
        }

        for (int i = 0; i < timeAxis.length - 1; i++) {
            float delta = timeAxis[i + 1] - timeAxis[i];
            values[i] = sanitizeWindowLength(delta, fallbackSampleTimeUs);
        }
        values[timeAxis.length - 1] = sanitizeWindowLength(values[timeAxis.length - 2], fallbackSampleTimeUs);
        return values;
    }

    private float sanitizeWindowLength(float candidate, float fallback) {
        if (Float.isFinite(candidate) && candidate > 0f) {
            return candidate;
        }
        if (Float.isFinite(fallback) && fallback > 0f) {
            return fallback;
        }
        return 0f;
    }

    /**
     * 统一按大端 double[] 写入，和内置 jjsk008 数据库保持一致。
     */
    private byte[] encodeFloatArray(float[] values) {
        if (values == null || values.length == 0) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 8).order(ByteOrder.BIG_ENDIAN);
        for (float value : values) {
            buffer.putDouble(value);
        }
        return buffer.array();
    }
}
