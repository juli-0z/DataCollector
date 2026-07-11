package cn.zjl.datacollector.net.api.request;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

/**
 * 设备状态上报请求体，用于定期向服务器报告设备的运行状态。
 * <p>
 * 该类对应 POST /device/report/android 接口的请求参数，
 * 包含设备的硬件信息、网络状态、位置信息和当前任务信息。
 * 服务器可根据此信息进行设备监控、故障诊断和任务调度。
 */
public class DeviceReportRequest {
    /**
     * 设备记录 ID（服务器分配）
     * <p>首次上报时可为 null，服务器返回后需保存</p>
     */
    public Long id;

    /**
     * 设备编码（业务唯一标识）
     */
    public String deviceCode;

    /**
     * 设备唯一标识符（如 Android ID 或 IMEI）
     */
    public String deviceId;

    /**
     * 设备名称（用户自定义或默认名称）
     */
    public String deviceName;

    /**
     * 设备类型（如 "Android", "Qt", "Embedded"）
     */
    public String deviceType;

    /**
     * 设备序列号（硬件唯一标识）
     */
    public String serialNumber;

    /**
     * 固件版本号
     */
    public String firmwareVersion;

    /**
     * 设备当前状态（如 "online", "offline", "collecting", "idle"）
     */
    public String status;

    /**
     * 当前关联的项目 ID
     */
    public Long projectId;

    /**
     * 当前关联的测线 ID
     */
    public Long lineId;

    /**
     * 当前测线编码
     */
    public String lineCode;

    /**
     * 电池电量百分比（0.0 - 100.0）
     */
    public Double batteryLevel;

    /**
     * 信号强度（单位：dBm，通常为负值，越接近 0 信号越好）
     */
    public Integer signalStrength;

    /**
     * GPS 定位精度（单位：米）
     */
    public Double gpsAccuracy;

    /**
     * 设备温度（单位：摄氏度 ℃）
     */
    public Double temperature;

    /**
     * 纬度坐标
     */
    public Double latitude;

    /**
     * 经度坐标
     */
    public Double longitude;

    /**
     * 网络类型（如 "WiFi", "4G", "5G", "Ethernet"）
     */
    public String networkType;

    /**
     * 设备 IP 地址
     */
    public String ipAddress;

    /**
     * 上报时间戳（Unix 毫秒时间戳）
     */
    public Long reportTime;

    /**
     * 备注信息（可选，用于记录特殊状态或错误信息）
     */
    public String note;
}
