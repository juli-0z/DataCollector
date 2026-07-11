package cn.zjl.datacollector.net.api.response;

import cn.zjl.datacollector.net.api.request.SyncRequest;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

/**
 * 单样本完整详情响应。
 *
 * <p>后端详情字段较多且可能继续扩展，本类先声明 Android 上传后联调最常用的字段；
 * Gson 会自动忽略未声明字段，因此不会影响接口兼容。</p>
 */
public class SampleFullPayload {
    public Long sampleId;
    public Long projectId;
    public Long lineId;
    public Long pointId;
    public String batchNo;
    public String engineeringCode;
    public String lineCode;
    public String pointCode;
    public Integer pointNumber;
    public String qualityStatus;
    public String qualityViewType;
    public Double batteryLevel;
    public Double gpsAccuracy;
    public Double completeness;
    public SyncRequest.MonitorPayload monitor;
    public SyncRequest.ParameterPayload parameters;
    public SyncRequest.WaveformPayload recvWaveform;
    public SyncRequest.WaveformPayload sendWaveform;
    public SyncRequest.WaveformPayload offWaveform;
}
