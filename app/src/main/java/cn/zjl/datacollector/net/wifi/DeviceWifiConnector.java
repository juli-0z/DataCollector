package cn.zjl.datacollector.net.wifi;

/**
 * 阅读提示：设备 Wi-Fi 热点连接器：封装 Android Wi-Fi 建议网络/网络请求接入流程和连接状态回调。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.zjl.datacollector.R;

/**
 * 设备 WiFi 连接器，负责自动连接到采集设备的 WiFi 热点。
 * <p>
 * 该类使用 Android 10+ 的 WifiNetworkSpecifier API 实现无需用户干预的自动连接功能。
 * 主要特点：
 * <ul>
 *   <li>自动请求连接到指定的 WiFi 网络（SSID + 密码）</li>
 *   <li>通过回调通知连接状态变化</li>
 *   <li>支持超时控制和异常处理</li>
 *   <li>自动管理 NetworkCallback 的生命周期</li>
 * </ul>
 * <p>
 * 使用场景：野外数据采集时，Android 设备需要连接到仪器的 WiFi 热点进行 TCP 通信。
 */
public class DeviceWifiConnector {

    /**
     * 连接超时时间（15秒）
     * <p>如果超过此时间仍未连接成功，则判定为连接失败</p>
     */
    private static final int CONNECT_TIMEOUT_MS = 15_000;

    /**
     * WiFi 连接回调接口，用于通知调用方连接状态的变化。
     * <p>
     * 所有回调方法都在主线程执行，可以直接更新 UI。
     */
    public interface Callback {
        /**
         * 状态变化通知
         *
         * @param state  新的连接状态
         * @param detail 详细信息（如错误消息、提示文本等）
         */
        void onStateChanged(DeviceWifiState state, @Nullable String detail);

        /**
         * 网络连接可用
         *
         * @param network 可用的 Network 对象，可用于绑定 Socket
         * @param config  当前使用的 WiFi 配置
         */
        void onAvailable(@NonNull Network network, @NonNull DeviceWifiConfig config);

        /**
         * 网络不可用（连接失败）
         *
         * @param message 错误消息
         * @param config  当前使用的 WiFi 配置
         */
        void onUnavailable(@NonNull String message, @NonNull DeviceWifiConfig config);

        /**
         * 网络连接丢失（之前已连接，后来断开）
         *
         * @param message 断开原因
         * @param config  当前使用的 WiFi 配置
         */
        void onLost(@NonNull String message, @NonNull DeviceWifiConfig config);

        /**
         * 发生错误
         *
         * @param message 错误消息
         * @param config  当前使用的 WiFi 配置（可能为 null）
         */
        void onError(@NonNull String message, @Nullable DeviceWifiConfig config);

        /**
         * 需要权限
         *
         * @param message 权限说明
         * @param config  当前使用的 WiFi 配置
         */
        void onPermissionRequired(@NonNull String message, @NonNull DeviceWifiConfig config);

        /**
         * 不支持该功能（Android 版本过低）
         *
         * @param message 不支持的原因
         * @param config  当前使用的 WiFi 配置
         */
        void onUnsupported(@NonNull String message, @NonNull DeviceWifiConfig config);
    }

    /**
     * Application Context，避免内存泄漏
     */
    private final Context context;

    /**
     * 系统连接管理器，用于请求和管理网络连接
     */
    private final ConnectivityManager connectivityManager;

    /**
     * 网络回调对象，接收系统发出的网络状态变化事件
     */
    @Nullable
    private ConnectivityManager.NetworkCallback networkCallback;

    /**
     * 当前激活的网络对象
     */
    @Nullable
    private Network activeNetwork;

    /**
     * 当前正在使用的 WiFi 配置
     */
    @Nullable
    private DeviceWifiConfig activeConfig;

    /**
     * 当前连接状态
     */
    private DeviceWifiState state = DeviceWifiState.IDLE;

    /**
     * 构造 WiFi 连接器
     *
     * @param context Android 上下文（会自动转换为 Application Context）
     */
    public DeviceWifiConnector(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = this.context.getSystemService(ConnectivityManager.class);
    }

    /**
     * 检查是否已连接到设备 WiFi
     *
     * @return true 表示已连接且状态为 CONNECTED
     */
    public boolean isConnected() {
        return activeNetwork != null && state == DeviceWifiState.CONNECTED;
    }

    /**
     * 获取当前激活的网络对象
     *
     * @return Network 对象，如果未连接则返回 null
     */
    @Nullable
    public Network getActiveNetwork() {
        return activeNetwork;
    }

    /**
     * 获取当前连接状态
     *
     * @return 当前的 DeviceWifiState 枚举值
     */
    public DeviceWifiState getState() {
        return state;
    }

    /**
     * 连接到指定的 WiFi 网络
     * <p>
     * 该方法会执行以下步骤：
     * <ol>
     *   <li>验证配置是否有效（SSID 不为空）</li>
     *   <li>检查 Android 版本是否支持</li>
     *   <li>检查是否具有所需权限</li>
     *   <li>构建 WifiNetworkSpecifier 请求</li>
     *   <li>异步等待系统连接结果</li>
     * </ol>
     *
     * @param config   WiFi 连接配置（SSID、密码等）
     * @param callback 回调接口，接收连接状态通知
     */
    @SuppressLint("MissingPermission")
    public void connect(@NonNull DeviceWifiConfig config, @NonNull Callback callback) {
        // 1. 验证 SSID 是否有效
        if (!config.canUseAutoConnect()) {
            updateState(DeviceWifiState.ERROR, config.ssid, context.getString(R.string.error_device_wifi_missing_ssid), callback);
            callback.onError(context.getString(R.string.error_device_wifi_missing_ssid), config);
            return;
        }

        // 2. 检查 Android 版本支持
        if (!DeviceWifiPermissionHelper.isAutoConnectSupported()) {
            String message = context.getString(R.string.error_device_wifi_unsupported);
            updateState(DeviceWifiState.UNSUPPORTED, config.ssid, message, callback);
            callback.onUnsupported(message, config);
            return;
        }

        // 3. 检查权限
        if (!DeviceWifiPermissionHelper.hasConnectPermissions(context)) {
            String message = context.getString(R.string.error_device_wifi_permission_required);
            updateState(DeviceWifiState.PERMISSION_REQUIRED, config.ssid, message, callback);
            callback.onPermissionRequired(message, config);
            return;
        }

        // 4. 检查 ConnectivityManager 是否可用
        if (connectivityManager == null) {
            String message = context.getString(R.string.error_device_wifi_connect_failed, "ConnectivityManager 不可用");
            updateState(DeviceWifiState.ERROR, config.ssid, message, callback);
            callback.onError(message, config);
            return;
        }

        // 5. 断开之前的连接（如果有）
        disconnectInternal(false);
        activeConfig = config;

        try {
            // 6. 构建 WiFi 网络描述符
            WifiNetworkSpecifier.Builder specifierBuilder = new WifiNetworkSpecifier.Builder()
                    .setSsid(config.ssid);  // 设置 SSID

            // 如果是隐藏网络，需要特殊标记
            if (config.hiddenSsid) {
                specifierBuilder.setIsHiddenSsid(true);
            }

            // 如果有密码，设置 WPA2 密码
            if (!config.password.isEmpty()) {
                specifierBuilder.setWpa2Passphrase(config.password);
            }

            // 7. 构建网络请求
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)  // 指定 WiFi 传输类型
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)  // 移除互联网能力要求（设备热点通常无外网）
                    .setNetworkSpecifier(specifierBuilder.build())  // 绑定 WiFi 描述符
                    .build();

            // 8. 创建网络回调，监听连接状态
            networkCallback = new ConnectivityManager.NetworkCallback() {
                /**
                 * 网络可用时调用
                 */
                @Override
                public void onAvailable(@NonNull Network network) {
                    activeNetwork = network;
                    updateState(
                            DeviceWifiState.CONNECTED,
                            config.ssid,
                            context.getString(R.string.text_device_wifi_hint_enabled),
                            callback);
                    callback.onAvailable(network, config);
                }

                /**
                 * 网络不可用时调用（连接失败）
                 */
                @Override
                public void onUnavailable() {
                    activeNetwork = null;
                    String message = context.getString(R.string.error_device_wifi_connect_failed, config.ssid);
                    updateState(DeviceWifiState.UNAVAILABLE, config.ssid, message, callback);
                    callback.onUnavailable(message, config);
                    networkCallback = null;
                }

                /**
                 * 网络连接丢失时调用
                 */
                @Override
                public void onLost(@NonNull Network network) {
                    if (activeNetwork == null || activeNetwork.equals(network)) {
                        activeNetwork = null;
                    }
                    String message = context.getString(R.string.wifi_status_lost);
                    updateState(DeviceWifiState.LOST, config.ssid, message, callback);
                    callback.onLost(message, config);
                }
            };

            // 9. 更新状态为“连接中”
            updateState(
                    DeviceWifiState.CONNECTING,
                    config.ssid,
                    context.getString(R.string.text_device_wifi_hint_enabled),
                    callback);

            // 10. 发起网络请求，系统会尝试连接指定的 WiFi
            connectivityManager.requestNetwork(request, networkCallback, CONNECT_TIMEOUT_MS);
        } catch (SecurityException exception) {
            // 权限不足异常
            String message = context.getString(R.string.error_device_wifi_permission_required);
            updateState(DeviceWifiState.PERMISSION_REQUIRED, config.ssid, message, callback);
            callback.onPermissionRequired(message, config);
        } catch (Exception exception) {
            // 其他异常
            String message = context.getString(
                    R.string.error_device_wifi_connect_failed,
                    safeText(exception.getMessage()).isEmpty()
                            ? exception.getClass().getSimpleName()
                            : safeText(exception.getMessage()));
            updateState(DeviceWifiState.ERROR, config.ssid, message, callback);
            callback.onError(message, config);
        }
    }

    /**
     * 断开 WiFi 连接
     * <p>
     * 该方法会取消网络请求并清理资源，状态变为 DISCONNECTED。
     */
    public void disconnect() {
        String ssid = activeConfig == null ? "" : activeConfig.ssid;
        disconnectInternal(true);
        updateState(DeviceWifiState.DISCONNECTED, ssid, context.getString(R.string.wifi_status_disconnected), null);
    }

    /**
     * 内部断开逻辑， unregister NetworkCallback 并清理状态
     *
     * @param clearStateOnly 如果为 true，则清空 activeConfig；否则保留配置信息
     */
    private void disconnectInternal(boolean clearStateOnly) {
        // 注销网络回调
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // 回调可能已经被系统注销，忽略异常
            }
        }
        networkCallback = null;
        activeNetwork = null;

        // 根据参数决定是否清空配置
        if (clearStateOnly) {
            activeConfig = null;
        }
    }

    /**
     * 更新连接状态并通知回调
     *
     * @param newState 新的状态
     * @param ssid     WiFi 名称（用于日志或提示）
     * @param detail   详细信息
     * @param callback 回调接口（可为 null）
     */
    private void updateState(@NonNull DeviceWifiState newState,
                             @Nullable String ssid,
                             @Nullable String detail,
                             @Nullable Callback callback) {
        state = newState;
        if (callback != null) {
            callback.onStateChanged(newState, detail);
        }
    }

    /**
     * 安全地处理字符串，防止 null 值
     *
     * @param value 原始字符串（可能为 null）
     * @return 修剪后的字符串，null 转为空字符串
     */
    @NonNull
    private String safeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
