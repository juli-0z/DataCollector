package cn.zjl.datacollector.util;

/**
 * 阅读提示：通用工具类：封装设置、导出、波形编码等跨模块复用能力。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

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
        return decodeBigEndianDouble(waveform.getDataRecv());
    }

    public static float[] extractSendValues(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.getDataSend());
    }

    public static float[] extractOffValues(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.getDataSoff());
    }

    public static float[] extractRecvTimeAxis(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.getDataRecvPos());
    }

    public static float[] extractSendTimeAxis(WaveformDataEntity waveform, int valueCount) {
        if (waveform == null || valueCount <= 0) {
            return new float[0];
        }
        return buildUniformAxis(valueCount, waveform.getSimpleSendFs());
    }

    public static float[] extractOffTimeAxis(WaveformDataEntity waveform, int valueCount) {
        if (waveform == null || valueCount <= 0) {
            return new float[0];
        }
        return buildUniformAxis(valueCount, waveform.getSimpleOffFs());
    }

    public static float[] extractRecvWindowLengths(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        return decodeBigEndianDouble(waveform.getDataRecvLen());
    }

    public static float[] extractValues(WaveformDataEntity waveform) {
        if (waveform == null) {
            return new float[0];
        }
        switch (waveform.getType()) {
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
