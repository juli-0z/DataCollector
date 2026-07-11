package cn.zjl.datacollector.net.wifi;

/**
 * 阅读提示：设备 Wi-Fi 模块代码：处理热点配置、权限检查、连接状态和系统网络能力差异。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

/**
 * 设备 WiFi 连接状态枚举，表示 WiFi 自动连接的各个阶段和结果。
 * <p>
 * 该枚举用于 {@link DeviceWifiConnector} 的状态管理，通过回调通知 UI 层当前连接状态，
 * 以便显示相应的提示信息或执行下一步操作。
 */
public enum DeviceWifiState {
    /**
     * 空闲状态：未开始连接或已重置
     */
    IDLE,

    /**
     * 需要权限：缺少 WiFi 连接所需的系统权限
     * <p>通常需要 ACCESS_FINE_LOCATION 或 NEARBY_WIFI_DEVICES 权限</p>
     */
    PERMISSION_REQUIRED,

    /**
     * 连接中：正在尝试连接到指定的 WiFi 网络
     */
    CONNECTING,

    /**
     * 已连接：成功连接到设备 WiFi，可以进行 TCP 通信
     */
    CONNECTED,

    /**
     * 已断开：主动断开 WiFi 连接
     */
    DISCONNECTED,

    /**
     * 不可用：WiFi 网络不存在或无法连接（如密码错误、信号弱等）
     */
    UNAVAILABLE,

    /**
     * 连接丢失：之前已连接，但后来断开（如设备关机、超出范围等）
     */
    LOST,

    /**
     * 错误状态：发生未知错误或异常
     */
    ERROR,

    /**
     * 不支持：当前 Android 版本不支持自动 WiFi 连接功能
     * <p>需要 Android 10 (API 29) 或更高版本</p>
     */
    UNSUPPORTED
}
