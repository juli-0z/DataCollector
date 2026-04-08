package cn.zjl.datacollector.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {

    private static final String PREFS = "data_collector_settings";
    private static final String KEY_TCP_IP = "tcp_ip";
    private static final String KEY_TCP_PORT = "tcp_port";
    private static final String KEY_SYNC_BASE_URL = "sync_base_url";

    private AppSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getTcpIp(Context context) {
        return prefs(context).getString(KEY_TCP_IP, "192.168.1.100");
    }

    public static int getTcpPort(Context context) {
        return prefs(context).getInt(KEY_TCP_PORT, 8080);
    }

    public static void saveTcp(Context context, String ip, int port) {
        prefs(context).edit()
                .putString(KEY_TCP_IP, ip)
                .putInt(KEY_TCP_PORT, port)
                .apply();
    }

    public static String getSyncBaseUrl(Context context) {
        return prefs(context).getString(KEY_SYNC_BASE_URL, "https://your-server.com/");
    }

    public static void setSyncBaseUrl(Context context, String baseUrl) {
        prefs(context).edit().putString(KEY_SYNC_BASE_URL, baseUrl).apply();
    }
}
