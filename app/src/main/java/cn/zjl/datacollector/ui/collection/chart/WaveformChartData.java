package cn.zjl.datacollector.ui.collection.chart;

/**
 * 阅读提示：采集图表模块代码：负责波形数据解码、状态整理和 RECV/SEND/OFF 图表绘制。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import java.util.ArrayList;
import java.util.List;

/**
 * 图表层统一使用的波形数据结构。
 * 这里不再暴露数据库实体，避免渲染层直接依赖表结构。
 */
public final class WaveformChartData {

    public WaveformChannelData recv = new WaveformChannelData();
    public WaveformChannelData send = new WaveformChannelData();
    public WaveformChannelData off = new WaveformChannelData();

    public List<WaveformChannelData> aggregateRecv = new ArrayList<>();
    public List<WaveformChannelData> aggregateSend = new ArrayList<>();
    public List<WaveformChannelData> aggregateOff = new ArrayList<>();

    public static final class WaveformChannelData {
        public float[] times = new float[0];
        public float[] values = new float[0];

        public boolean hasData() {
            return times != null
                    && values != null
                    && times.length > 0
                    && values.length > 0;
        }
    }
}
