package cn.zjl.datacollector.ui.collection.chart;

/**
 * 阅读提示：采集图表模块代码：负责波形数据解码、状态整理和 RECV/SEND/OFF 图表绘制。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.data.entity.WaveformDataEntity;

/**
 * 负责把原始波形记录整理成“第 n 次采集”的会话概念。
 * 波形解析本身已拆到 WaveformDecodeHelper，这里只保留归组职责。
 */
public final class WaveformSessionResolver {

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

        boolean rowContainsFullWaveforms = false;
        for (WaveformDataEntity waveform : waveforms) {
            int channelCount = 0;
            if (waveform.getDataRecv() != null && waveform.getDataRecv().length > 0) {
                channelCount++;
            }
            if (waveform.getDataSend() != null && waveform.getDataSend().length > 0) {
                channelCount++;
            }
            if (waveform.getDataSoff() != null && waveform.getDataSoff().length > 0) {
                channelCount++;
            }
            if (channelCount > 1) {
                rowContainsFullWaveforms = true;
                break;
            }
        }

        // 兼容两种历史格式：
        // 1. 一行内同时带 Recv/Send/Off，此时一行就是一次采集；
        // 2. 三个通道拆成三行，此时按 startTime 合并。
        if (rowContainsFullWaveforms) {
            for (int i = 0; i < waveforms.size(); i++) {
                WaveformSession session = new WaveformSession();
                session.sessionIndex = i + 1;
                session.sessionKey = waveforms.get(i).getId();
                session.startTime = waveforms.get(i).getStartTime();
                session.waveforms.add(waveforms.get(i));
                sessions.add(session);
            }
            return sessions;
        }

        WaveformSession currentSession = null;
        long currentStartTime = Long.MIN_VALUE;
        for (WaveformDataEntity waveform : waveforms) {
            if (currentSession == null || waveform.getStartTime() != currentStartTime) {
                currentStartTime = waveform.getStartTime();
                currentSession = new WaveformSession();
                currentSession.sessionIndex = sessions.size() + 1;
                currentSession.sessionKey = waveform.getStartTime();
                currentSession.startTime = waveform.getStartTime();
                sessions.add(currentSession);
            }
            currentSession.waveforms.add(waveform);
        }
        return sessions;
    }
}
