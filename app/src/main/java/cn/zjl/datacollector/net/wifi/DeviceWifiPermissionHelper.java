package cn.zjl.datacollector.net.wifi;

/**
 * 阅读提示：设备 Wi-Fi 模块代码：处理热点配置、权限检查、连接状态和系统网络能力差异。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备 WiFi 连接权限辅助类，检查和获取自动连接 WiFi 所需的系统权限。
 * <p>
 * Android 10+ 引入了新的 WiFi 连接 API（WifiNetworkSpecifier），需要特定的权限才能使用：
 * <ul>
 *   <li>Android 12+ (API 31+): 需要 NEARBY_WIFI_DEVICES 权限</li>
 *   <li>Android 10-11 (API 29-30): 需要 ACCESS_FINE_LOCATION 权限</li>
 * </ul>
 * 该类封装了版本判断和权限检查逻辑，简化调用方的代码。
 */
public final class DeviceWifiPermissionHelper {

    /**
     * 私有构造函数，防止实例化（工具类）
     */
    private DeviceWifiPermissionHelper() {
    }

    /**
     * 检查当前 Android 版本是否支持自动 WiFi 连接功能
     * <p>
     * WifiNetworkSpecifier API 从 Android 10 (API 29) 开始引入，
     * 低版本系统无法使用此功能。
     *
     * @return true 表示支持自动连接，false 表示不支持
     */
    public static boolean isAutoConnectSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;  // Q = API 29 = Android 10
    }

    /**
     * 检查是否具有连接 WiFi 所需的所有权限
     *
     * @param context Android 上下文
     * @return true 表示权限齐全，false 表示缺少某些权限
     */
    public static boolean hasConnectPermissions(@NonNull Context context) {
        return getMissingConnectPermissions(context).length == 0;
    }

    /**
     * 获取缺少的 WiFi 连接权限列表
     * <p>
     * 根据 Android 版本返回不同的权限要求：
     * <ul>
     *   <li>Android 12+: 检查 NEARBY_WIFI_DEVICES</li>
     *   <li>Android 10-11: 检查 ACCESS_FINE_LOCATION</li>
     *   <li>Android 9及以下: 不需要特殊权限（但也不支持自动连接）</li>
     * </ul>
     *
     * @param context Android 上下文
     * @return 缺少的权限数组，如果为空表示权限齐全
     */
    @NonNull
    public static String[] getMissingConnectPermissions(@NonNull Context context) {
        // 不支持的版本不需要检查权限
        if (!isAutoConnectSupported()) {
            return new String[0];
        }

        List<String> missingPermissions = new ArrayList<>();

        // Android 12+ (TIRAMISU = API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)) {
            missingPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        // Android 10-11 (低于 TIRAMISU)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && !hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        return missingPermissions.toArray(new String[0]);
    }

    /**
     * 检查是否具有指定的权限
     *
     * @param context    Android 上下文
     * @param permission 权限名称（如 Manifest.permission.ACCESS_FINE_LOCATION）
     * @return true 表示已授权，false 表示未授权
     */
    private static boolean hasPermission(@NonNull Context context, @NonNull String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
