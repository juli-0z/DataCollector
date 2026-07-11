package cn.zjl.datacollector.net.wifi;

/**
 * 阅读提示：设备 Wi-Fi 模块代码：处理热点配置、权限检查、连接状态和系统网络能力差异。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import androidx.annotation.Nullable;

/**
 * 设备 WiFi 连接配置类，封装连接到采集设备所需的 WiFi 参数。
 * <p>
 * 该类是不可变对象（Immutable），一旦创建后不能修改，保证线程安全。
 * 主要用于 Android 10+ 的 WiFi NetworkSpecifier API，自动连接到指定的 WiFi 网络。
 */
public final class DeviceWifiConfig {

    /**
     * 是否启用自动连接功能
     * <p>如果为 false，则不会尝试自动连接 WiFi</p>
     */
    public final boolean autoConnectEnabled;

    /**
     * WiFi 网络名称（SSID）
     * <p>例如："GeoDsp_Device_001"</p>
     */
    public final String ssid;

    /**
     * WiFi 密码（WPA2  passphrase）
     * <p>如果为空字符串，则表示开放网络（无密码）</p>
     */
    public final String password;

    /**
     * WiFi 接入点的 MAC 地址（BSSID）
     * <p>可选字段，用于精确匹配特定的 AP（当有多个同名 SSID 时）</p>
     */
    public final String bssid;

    /**
     * 是否为隐藏 SSID 网络
     * <p>隐藏网络不会广播 SSID，需要手动指定才能连接</p>
     */
    public final boolean hiddenSsid;

    /**
     * 设备的 TCP 服务器 IP 地址
     * <p>连接 WiFi 后，通过此 IP 和端口与设备进行 TCP 通信</p>
     */
    public final String ip;

    /**
     * 设备的 TCP 服务器端口号
     * <p>通常为 8080、9000 等自定义端口</p>
     */
    public final int port;

    /**
     * 构造 WiFi 配置对象
     *
     * @param autoConnectEnabled 是否启用自动连接
     * @param ssid               WiFi 网络名称（可为 null，会自动转为空字符串）
     * @param password           WiFi 密码（可为 null，会自动转为空字符串）
     * @param bssid              AP 的 MAC 地址（可为 null，会自动转为空字符串）
     * @param hiddenSsid         是否为隐藏网络
     * @param ip                 设备 IP 地址（可为 null，会自动转为空字符串）
     * @param port               设备端口号
     */
    public DeviceWifiConfig(boolean autoConnectEnabled,
                            @Nullable String ssid,
                            @Nullable String password,
                            @Nullable String bssid,
                            boolean hiddenSsid,
                            @Nullable String ip,
                            int port) {
        this.autoConnectEnabled = autoConnectEnabled;
        this.ssid = safeTrim(ssid);
        this.password = safeTrim(password);
        this.bssid = safeTrim(bssid);
        this.hiddenSsid = hiddenSsid;
        this.ip = safeTrim(ip);
        this.port = port;
    }

    /**
     * 判断是否可以使用自动连接功能
     * <p>需要同时满足：启用了自动连接 && SSID 不为空</p>
     *
     * @return true 表示可以尝试自动连接 WiFi
     */
    public boolean canUseAutoConnect() {
        return autoConnectEnabled && !ssid.isEmpty();
    }

    /**
     * 安全地修剪字符串，处理 null 值
     *
     * @param value 原始字符串（可能为 null）
     * @return 修剪后的字符串，如果输入为 null 则返回空字符串
     */
    private String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
