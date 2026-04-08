package cn.zjl.datacollector.net;

import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;

/**
 * 数据同步请求对象
 */
public class SyncRequest {
    public long projectId;
    public MeasurementPointEntity point;
    public CollectionParameterEntity parameters;
    public WaveformDataEntity[] waveforms;
    public DeviceMonitorEntity[] monitors;
}
