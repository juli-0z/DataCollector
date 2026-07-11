package cn.zjl.datacollector.net.api.request;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import java.util.ArrayList;
import java.util.List;

/**
 * 数据同步请求对象，用于批量上传野外采集的测点数据。
 * <p>
 * 该类对应 POST /receive/android 接口的请求体，包含一个批次的所有测点信息，
 * 每个测点包括坐标、采集参数、设备监控数据和波形数据。
 * 服务器接收到后会进行去重检查、数据存储和统计分析。
 */
public class SyncRequest {
    /**
     * 批次号（唯一标识一次上传任务）
     * <p>通常由客户端生成 UUID 或时间戳+随机数，用于追踪上传进度和去重</p>
     */
    public String batchNo;

    /**
     * 设备唯一标识符
     */
    public String deviceId;

    /**
     * 设备类型（如 "Android"）
     */
    public String deviceType;

    /**
     * 项目 ID（服务器分配）
     */
    public Long projectId;

    public String projectCode;

    public String projectName;

    /**
     * 工程编码（业务唯一标识）
     */
    public String engineeringCode;

    /**
     * 测线编码（业务唯一标识）
     */
    public String lineCode;

    public String taskName;

    /**
     * 测点列表，包含本次上传的所有测点数据
     */
    public List<PointPayload> points = new ArrayList<>();

    /**
     * 测点数据内部类，封装单个测点的完整信息。
     */
    public static class PointPayload {
        /**
         * 测点编码（业务唯一标识，通常为“测线号-测点号”）
         */
        public String pointCode;

        /**
         * 测点号。后端当前正式定义为整数，小数点号请放在 pointCode 中表达。
         */
        public Integer pointNumber;

        /**
         * 纬度坐标（WGS84 坐标系）
         */
        public Double latitude;

        /**
         * 经度坐标（WGS84 坐标系）
         */
        public Double longitude;

        /**
         * 海拔高度（单位：米）
         */
        public Double altitude;

        /**
         * 高程（相对高程或绝对高程，单位：米）
         */
        public Double elevation;

        /**
         * 采集参数（发射电流、采样频率等）
         */
        public ParameterPayload parameter;

        /**
         * 设备监控数据（电池电压、温度、信号强度等）
         */
        public MonitorPayload monitor;

        /**
         * 波形数据列表，可能包含多个类型的波形（接收波、发送波、关断波等）
         */
        public List<WaveformPayload> waveforms = new ArrayList<>();
    }

    /**
     * 采集参数内部类，记录测量时的仪器配置。
     */
    public static class ParameterPayload {
        /**
         * 发射电流（单位：安培 A）
         */
        public Double sendCurrent;

        /**
         * 采样频率（单位：赫兹 Hz）
         */
        public Integer samplingFrequency;

        /**
         * 叠加次数（采集次数，用于提高信噪比）
         */
        public Integer collectionCount;

        /**
         * 采样时间长度（单位：秒 s）
         */
        public Double samplingTime;

        /**
         * 极距（电极间距，单位：米 m）
         */
        public Double poleDistance;

        /**
         * 线圈方向（如 "X", "Y", "Z" 或角度描述）
         */
        public String coilDirection;
    }

    /**
     * 设备监控数据内部类，记录采集时的设备状态。
     */
    public static class MonitorPayload {
        /**
         * 电池电压（单位：伏特 V）
         */
        public Double batteryVoltage;

        public Double batteryLevel;

        /**
         * 工作电流（单位：安培 A）
         */
        public Double current;

        /**
         * 设备温度（单位：摄氏度 ℃）
         */
        public Double temperature;

        /**
         * 信号强度（单位：dBm）
         */
        public Integer signalStrength;

        /**
         * GPS 定位精度（单位：米）
         */
        public Double gpsAccuracy;

        /**
         * 数据采集时间戳（ISO 8601 格式字符串）
         */
        public String timestamp;
    }

    /**
     * 波形数据内部类，存储单个通道的时域波形。
     */
    public static class WaveformPayload {
        /**
         * 波形类型标识
         * <ul>
         *   <li>1: 发送波形（Send）</li>
         *   <li>2: 关断波形（Off）</li>
         *   <li>3: 接收波形（Recv）</li>
         * </ul>
         */
        public Integer waveformType;

        /**
         * 时间轴数据（单位：微秒 μs 或毫秒 ms）
         */
        public List<Double> timeSeries = new ArrayList<>();

        /**
         * 电压幅值数据（单位：毫伏 mV 或伏特 V）
         */
        public List<Double> voltageSeries = new ArrayList<>();
    }
}
