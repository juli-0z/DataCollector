package cn.zjl.datacollector.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import cn.zjl.datacollector.data.entity.WaveformDataEntity;

public final class WaveformCodec {

    private WaveformCodec() {
    }

    public static float[] decode(byte[] bytes) {
        return decodeBigEndianDouble(bytes);
    }

    public static float[] extractRecvValues(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.dataRecv);
    }

    public static float[] extractSendValues(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.dataSend);
    }

    public static float[] extractOffValues(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.dataSoff);
    }

    public static float[] extractRecvTimeAxis(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.dataRecvPos);
    }

    public static float[] extractSendTimeAxis(WaveformDataEntity waveform, int valueCount) {
        if (waveform == null || valueCount <= 0) {
            return new float[0];
        }
        return buildUniformAxis(valueCount, waveform.simpleSendFs);
    }

    public static float[] extractOffTimeAxis(WaveformDataEntity waveform, int valueCount) {
        if (waveform == null || valueCount <= 0) {
            return new float[0];
        }
        return buildUniformAxis(valueCount, waveform.simpleOffFs);
    }

    public static float[] extractRecvWindowLengths(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.dataRecvLen);
    }

    public static float[] extractValues(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        switch (waveform.type) {
            case 1:
                return extractSendValues(waveform);
            case 2:
                return extractOffValues(waveform);
            case 0:
            default:
                return extractRecvValues(waveform);
        }
    }

    public static float[] extractTimeAxis(WaveformDataEntity waveform) {
        return extractRecvTimeAxis(waveform);
    }

    private static float[] decodeBigEndianDouble(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 8 != 0) {
            return new float[0];
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        float[] values = new float[bytes.length / 8];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) buffer.getDouble();
        }
        return values;
    }

    private static float[] buildUniformAxis(int count, float sampleRateHz) {
        float[] axis = new float[count];
        float stepUs = sampleRateHz > 0f ? 1_000_000f / sampleRateHz : 1f;
        for (int i = 0; i < count; i++) {
            axis[i] = i * stepUs;
        }
        return axis;
    }
}
