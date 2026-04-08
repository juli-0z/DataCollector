package cn.zjl.datacollector.ui.collection;

import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.util.WaveformCodec;

/**
 * 负责把数据库中的波形记录整理成“某次采集”的概念，
 * 并进一步提炼出图表渲染所需的三路波形状态。
 */
public final class WaveformSessionResolver {

    public static class WaveformRenderState {
        public float[] recvTimes = new float[0];
        public float[] recvValues = new float[0];
        public float[] sendTimes = new float[0];
        public float[] sendValues = new float[0];
        public float[] offTimes = new float[0];
        public float[] offValues = new float[0];
    }

    public static class WaveformSession {
        public int sessionIndex;
        public long sessionKey;
        public long startTime;
        public List<WaveformDataEntity> waveforms = new ArrayList<>();
    }

    public WaveformSession selectWaveformSession(List<WaveformDataEntity> waveforms, int sessionIndex) {
        List<WaveformSession> sessions = buildWaveformSessions(waveforms);
        if (sessions.isEmpty()) {
            return null;
        }
        if (sessionIndex <= 0) {
            return sessions.get(sessions.size() - 1);
        }
        for (WaveformSession session : sessions) {
            if (session.sessionIndex == sessionIndex) {
                return session;
            }
        }
        return sessions.get(sessions.size() - 1);
    }

    public List<WaveformSession> buildWaveformSessions(List<WaveformDataEntity> waveforms) {
        List<WaveformSession> sessions = new ArrayList<>();
        if (waveforms == null || waveforms.isEmpty()) {
            return sessions;
        }

        // 兼容两类历史数据：
        // 1. 一行同时包含 Recv/Send/Off，此时一行就是一次采集；
        // 2. 三个通道分别存为三行，此时按 startTime 合并成一次采集。
        boolean rowContainsFullWaveforms = false;
        for (WaveformDataEntity waveform : waveforms) {
            int channelCount = 0;
            if (waveform.dataRecv != null && waveform.dataRecv.length > 0) {
                channelCount++;
            }
            if (waveform.dataSend != null && waveform.dataSend.length > 0) {
                channelCount++;
            }
            if (waveform.dataSoff != null && waveform.dataSoff.length > 0) {
                channelCount++;
            }
            if (channelCount > 1) {
                rowContainsFullWaveforms = true;
                break;
            }
        }

        if (rowContainsFullWaveforms) {
            for (int i = 0; i < waveforms.size(); i++) {
                WaveformSession session = new WaveformSession();
                session.sessionIndex = i + 1;
                session.sessionKey = waveforms.get(i).id;
                session.startTime = waveforms.get(i).startTime;
                session.waveforms.add(waveforms.get(i));
                sessions.add(session);
            }
            return sessions;
        }

        WaveformSession currentSession = null;
        long currentStartTime = Long.MIN_VALUE;
        for (WaveformDataEntity waveform : waveforms) {
            if (currentSession == null || waveform.startTime != currentStartTime) {
                currentStartTime = waveform.startTime;
                currentSession = new WaveformSession();
                currentSession.sessionIndex = sessions.size() + 1;
                currentSession.sessionKey = waveform.startTime;
                currentSession.startTime = waveform.startTime;
                sessions.add(currentSession);
            }
            currentSession.waveforms.add(waveform);
        }
        return sessions;
    }

    public WaveformRenderState prepareWaveformState(List<WaveformDataEntity> waveforms) {
        WaveformRenderState state = new WaveformRenderState();
        if (waveforms == null || waveforms.isEmpty()) {
            return state;
        }

        // 同一次采集中，哪个通道有值就从对应记录提取出来，
        // 最终拼成一套完整的 Recv / Send / Off 渲染状态。
        for (WaveformDataEntity waveform : waveforms) {
            float[] recvValues = WaveformCodec.extractRecvValues(waveform);
            if (recvValues.length > 0) {
                state.recvValues = recvValues;
                state.recvTimes = WaveformCodec.extractRecvTimeAxis(waveform);
            }

            float[] sendValues = WaveformCodec.extractSendValues(waveform);
            if (sendValues.length > 0) {
                state.sendValues = sendValues;
                state.sendTimes = WaveformCodec.extractSendTimeAxis(waveform, sendValues.length);
            }

            float[] offValues = WaveformCodec.extractOffValues(waveform);
            if (offValues.length > 0) {
                state.offValues = offValues;
                state.offTimes = WaveformCodec.extractOffTimeAxis(waveform, offValues.length);
            }
        }
        return state;
    }
}
