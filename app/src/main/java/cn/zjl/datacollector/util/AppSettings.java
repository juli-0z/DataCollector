package cn.zjl.datacollector.util;

/**
 * 阅读提示：通用工具类：封装设置、导出、波形编码等跨模块复用能力。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.net.wifi.DeviceWifiConfig;

public final class AppSettings {

    private static final String PREFS = "data_collector_settings";
    private static final String KEY_TCP_IP = "tcp_ip";
    private static final String KEY_TCP_PORT = "tcp_port";
    private static final String KEY_TCP_SIMULATION_ENABLED = "tcp_simulation_enabled";
    private static final String KEY_DEVICE_WIFI_AUTO_CONNECT = "device_wifi_auto_connect";
    private static final String KEY_DEVICE_WIFI_SSID = "device_wifi_ssid";
    private static final String KEY_DEVICE_WIFI_PASSWORD = "device_wifi_password";
    private static final String KEY_DEVICE_WIFI_BSSID = "device_wifi_bssid";
    private static final String KEY_DEVICE_WIFI_HIDDEN_SSID = "device_wifi_hidden_ssid";
    private static final String KEY_SYNC_BASE_URL = "sync_base_url";
    private static final String KEY_SYNC_TOKEN = "sync_token";
    private static final String KEY_SYNC_DEVICE_ID = "sync_device_id";
    private static final String KEY_SYNC_USERNAME = "sync_username";
    private static final String KEY_SYNC_PASSWORD = "sync_password";
    private static final String KEY_PROJECT_SYNC_REMOTE_PROJECT_ID_PREFIX = "project_sync_remote_project_id_";
    private static final String KEY_PROJECT_SYNC_ENGINEERING_CODE_PREFIX = "project_sync_engineering_code_";
    private static final String KEY_COLLECTION_TEMPLATE_PREFIX = "collection_template_";
    private static final String KEY_COLLECTION_QUALITY_PROFILE = "collection_quality_profile";
    private static final String KEY_COLLECTION_QUALITY_MIN_RECV_POINTS = "collection_quality_min_recv_points";
    private static final String KEY_COLLECTION_QUALITY_MIN_RECV_AMPLITUDE = "collection_quality_min_recv_amplitude";
    private static final String KEY_COLLECTION_QUALITY_MIN_BATTERY_VOLTAGE = "collection_quality_min_battery_voltage";
    private static final String KEY_COLLECTION_QUALITY_MAX_TEMPERATURE = "collection_quality_max_temperature";
    private static final String KEY_COLLECTION_QUALITY_BLOCK_SAVE_ON_FAILURE = "collection_quality_block_save_on_failure";
    private static final String LEGACY_PLACEHOLDER_SYNC_BASE_URL = "https://your-server.com/api/";
    private static final String LEGACY_HTTPS_SYNC_BASE_URL = "https://geo.natapp1.cc/api/";
    private static final String LEGACY_HTTP_SYNC_BASE_URL = "http://geo.natapp1.cc/api/";
    private static final String DEFAULT_SYNC_BASE_URL = "http://geo.natapp1.cc/hh/";
    private static final String DEFAULT_SYNC_USERNAME = "zzjjll";
    private static final String DEFAULT_SYNC_PASSWORD = "123456";
    private static final String DEFAULT_SYNC_DEVICE_ID = "ANDROID-DEV-001";
    private static final long DEFAULT_SYNC_REMOTE_PROJECT_ID = 11L;
    private static final int COLLECTION_TEMPLATE_SLOT_COUNT = 3;
    private static final String COLLECTION_QUALITY_PROFILE_STANDARD = "standard";
    private static final String COLLECTION_QUALITY_PROFILE_RELAXED = "relaxed";
    private static final int DEFAULT_COLLECTION_QUALITY_MIN_RECV_POINTS = 36;
    private static final float DEFAULT_COLLECTION_QUALITY_MIN_RECV_AMPLITUDE = 1.0e-6f;
    private static final float DEFAULT_COLLECTION_QUALITY_MIN_BATTERY_VOLTAGE = 10f;
    private static final float DEFAULT_COLLECTION_QUALITY_MIN_GPS_ACCURACY = 95f;
    private static final float DEFAULT_COLLECTION_QUALITY_MAX_TEMPERATURE = 85f;
    private static final int RELAXED_COLLECTION_QUALITY_MIN_RECV_POINTS = 24;
    private static final float RELAXED_COLLECTION_QUALITY_MIN_RECV_AMPLITUDE = 5.0e-7f;
    private static final float RELAXED_COLLECTION_QUALITY_MIN_BATTERY_VOLTAGE = 10f;
    private static final float RELAXED_COLLECTION_QUALITY_MIN_GPS_ACCURACY = 95f;
    private static final float RELAXED_COLLECTION_QUALITY_MAX_TEMPERATURE = 95f;
    private static final boolean DEFAULT_COLLECTION_QUALITY_BLOCK_SAVE_ON_FAILURE = false;

    public enum CollectionQualityProfile {
        STANDARD(COLLECTION_QUALITY_PROFILE_STANDARD),
        RELAXED(COLLECTION_QUALITY_PROFILE_RELAXED);

        private final String code;

        CollectionQualityProfile(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static CollectionQualityProfile fromCode(@Nullable String code) {
            String safeCode = safeTrim(code);
            if (COLLECTION_QUALITY_PROFILE_RELAXED.equalsIgnoreCase(safeCode)) {
                return RELAXED;
            }
            return STANDARD;
        }
    }

    public static final class CollectionQualityConfig {
        public CollectionQualityProfile profile = CollectionQualityProfile.STANDARD;
        public int minRecvPoints;
        public float minRecvAmplitude;
        public float minBatteryVoltage;
        public float minGpsAccuracy;
        public float maxTemperature;
        public boolean blockSaveOnFailure;
    }

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

    public static boolean isTcpSimulationEnabled(Context context) {
        return prefs(context).getBoolean(KEY_TCP_SIMULATION_ENABLED, false);
    }

    public static void setTcpSimulationEnabled(Context context, boolean enabled) {
        prefs(context).edit()
                .putBoolean(KEY_TCP_SIMULATION_ENABLED, enabled)
                .apply();
    }

    public static boolean isDeviceWifiAutoConnectEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DEVICE_WIFI_AUTO_CONNECT, false);
    }

    public static void setDeviceWifiAutoConnectEnabled(Context context, boolean enabled) {
        prefs(context).edit()
                .putBoolean(KEY_DEVICE_WIFI_AUTO_CONNECT, enabled)
                .apply();
    }

    public static String getDeviceWifiSsid(Context context) {
        return safeTrim(prefs(context).getString(KEY_DEVICE_WIFI_SSID, ""));
    }

    public static void setDeviceWifiSsid(Context context, String ssid) {
        prefs(context).edit()
                .putString(KEY_DEVICE_WIFI_SSID, safeTrim(ssid))
                .apply();
    }

    public static String getDeviceWifiPassword(Context context) {
        return safeTrim(prefs(context).getString(KEY_DEVICE_WIFI_PASSWORD, ""));
    }

    public static void setDeviceWifiPassword(Context context, String password) {
        prefs(context).edit()
                .putString(KEY_DEVICE_WIFI_PASSWORD, safeTrim(password))
                .apply();
    }

    public static String getDeviceWifiBssid(Context context) {
        return safeTrim(prefs(context).getString(KEY_DEVICE_WIFI_BSSID, ""));
    }

    public static void setDeviceWifiBssid(Context context, String bssid) {
        prefs(context).edit()
                .putString(KEY_DEVICE_WIFI_BSSID, safeTrim(bssid))
                .apply();
    }

    public static boolean isDeviceWifiHiddenSsid(Context context) {
        return prefs(context).getBoolean(KEY_DEVICE_WIFI_HIDDEN_SSID, false);
    }

    public static void setDeviceWifiHiddenSsid(Context context, boolean hiddenSsid) {
        prefs(context).edit()
                .putBoolean(KEY_DEVICE_WIFI_HIDDEN_SSID, hiddenSsid)
                .apply();
    }

    public static void saveDeviceWifiConfig(Context context,
                                            boolean autoConnectEnabled,
                                            String ssid,
                                            String password,
                                            boolean hiddenSsid) {
        prefs(context).edit()
                .putBoolean(KEY_DEVICE_WIFI_AUTO_CONNECT, autoConnectEnabled)
                .putString(KEY_DEVICE_WIFI_SSID, safeTrim(ssid))
                .putString(KEY_DEVICE_WIFI_PASSWORD, safeTrim(password))
                .putBoolean(KEY_DEVICE_WIFI_HIDDEN_SSID, hiddenSsid)
                .apply();
    }

    public static DeviceWifiConfig getDeviceWifiConfig(Context context) {
        return new DeviceWifiConfig(
                isDeviceWifiAutoConnectEnabled(context),
                getDeviceWifiSsid(context),
                getDeviceWifiPassword(context),
                getDeviceWifiBssid(context),
                isDeviceWifiHiddenSsid(context),
                getTcpIp(context),
                getTcpPort(context));
    }

    public static String getSyncBaseUrl(Context context) {
        return normalizeBaseUrl(prefs(context).getString(KEY_SYNC_BASE_URL, DEFAULT_SYNC_BASE_URL));
    }

    public static void setSyncBaseUrl(Context context, String baseUrl) {
        prefs(context).edit().putString(KEY_SYNC_BASE_URL, normalizeBaseUrl(baseUrl)).apply();
    }

    public static boolean isSyncBaseUrlConfigured(Context context) {
        String baseUrl = getSyncBaseUrl(context);
        return baseUrl.startsWith("https://") || baseUrl.startsWith("http://");
    }

    public static String getSyncToken(Context context) {
        return safeTrim(prefs(context).getString(KEY_SYNC_TOKEN, ""));
    }

    public static void setSyncToken(Context context, String token) {
        prefs(context).edit().putString(KEY_SYNC_TOKEN, safeTrim(token)).apply();
    }

    public static void clearSyncToken(Context context) {
        prefs(context).edit().remove(KEY_SYNC_TOKEN).apply();
    }

    public static String getSyncDeviceId(Context context) {
        String stored = safeTrim(prefs(context).getString(KEY_SYNC_DEVICE_ID, ""));
        return stored.isEmpty() ? DEFAULT_SYNC_DEVICE_ID : stored;
    }

    public static void setSyncDeviceId(Context context, String deviceId) {
        prefs(context).edit().putString(KEY_SYNC_DEVICE_ID, safeTrim(deviceId)).apply();
    }

    public static String getSyncUsername(Context context) {
        String username = safeTrim(prefs(context).getString(KEY_SYNC_USERNAME, DEFAULT_SYNC_USERNAME));
        return username.isEmpty() ? DEFAULT_SYNC_USERNAME : username;
    }

    public static void setSyncUsername(Context context, String username) {
        prefs(context).edit().putString(KEY_SYNC_USERNAME, safeTrim(username)).apply();
    }

    public static String getSyncPassword(Context context) {
        String password = safeTrim(prefs(context).getString(KEY_SYNC_PASSWORD, DEFAULT_SYNC_PASSWORD));
        return password.isEmpty() ? DEFAULT_SYNC_PASSWORD : password;
    }

    public static void setSyncPassword(Context context, String password) {
        prefs(context).edit().putString(KEY_SYNC_PASSWORD, safeTrim(password)).apply();
    }

    public static boolean hasSyncCredentials(Context context) {
        return !getSyncUsername(context).isEmpty() && !getSyncPassword(context).isEmpty();
    }

    public static Long getProjectSyncRemoteProjectId(Context context, String databaseName) {
        String stored = safeTrim(prefs(context).getString(
                projectKey(KEY_PROJECT_SYNC_REMOTE_PROJECT_ID_PREFIX, databaseName),
                ""));
        if (!stored.isEmpty()) {
            try {
                return Long.parseLong(stored);
            } catch (NumberFormatException ignored) {
                return DEFAULT_SYNC_REMOTE_PROJECT_ID;
            }
        }
        return DEFAULT_SYNC_REMOTE_PROJECT_ID;
    }

    public static void setProjectSyncRemoteProjectId(Context context, String databaseName, Long projectId) {
        String key = projectKey(KEY_PROJECT_SYNC_REMOTE_PROJECT_ID_PREFIX, databaseName);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (projectId == null || projectId <= 0L) {
            editor.remove(key);
        } else {
            editor.putString(key, String.valueOf(projectId));
        }
        editor.apply();
    }

    public static String getProjectSyncEngineeringCode(Context context, String databaseName) {
        return safeTrim(prefs(context).getString(
                projectKey(KEY_PROJECT_SYNC_ENGINEERING_CODE_PREFIX, databaseName), ""));
    }

    public static void setProjectSyncEngineeringCode(Context context, String databaseName, String engineeringCode) {
        String key = projectKey(KEY_PROJECT_SYNC_ENGINEERING_CODE_PREFIX, databaseName);
        SharedPreferences.Editor editor = prefs(context).edit();
        String trimmed = safeTrim(engineeringCode);
        if (trimmed.isEmpty()) {
            editor.remove(key);
        } else {
            editor.putString(key, trimmed);
        }
        editor.apply();
    }

    public static boolean isProjectSyncConfigured(Context context, String databaseName) {
        return getProjectSyncRemoteProjectId(context, databaseName) != null;
    }

    public static int getCollectionTemplateSlotCount() {
        return COLLECTION_TEMPLATE_SLOT_COUNT;
    }

    public static CollectionQualityConfig getCollectionQualityConfig(Context context) {
        SharedPreferences sharedPreferences = prefs(context);
        CollectionQualityProfile profile = CollectionQualityProfile.fromCode(
                sharedPreferences.getString(
                        KEY_COLLECTION_QUALITY_PROFILE,
                        COLLECTION_QUALITY_PROFILE_STANDARD));
        boolean blockSaveOnFailure = sharedPreferences.getBoolean(
                KEY_COLLECTION_QUALITY_BLOCK_SAVE_ON_FAILURE,
                DEFAULT_COLLECTION_QUALITY_BLOCK_SAVE_ON_FAILURE);
        return createCollectionQualityConfig(profile, blockSaveOnFailure);
    }

    public static CollectionQualityConfig createDefaultCollectionQualityConfig() {
        return createCollectionQualityConfig(
                CollectionQualityProfile.STANDARD,
                DEFAULT_COLLECTION_QUALITY_BLOCK_SAVE_ON_FAILURE);
    }

    public static CollectionQualityConfig createCollectionQualityConfig(
            @Nullable CollectionQualityProfile profile,
            boolean blockSaveOnFailure) {
        CollectionQualityProfile safeProfile = profile == null
                ? CollectionQualityProfile.STANDARD
                : profile;
        CollectionQualityConfig config = new CollectionQualityConfig();
        config.profile = safeProfile;
        config.blockSaveOnFailure = blockSaveOnFailure;
        if (safeProfile == CollectionQualityProfile.RELAXED) {
            config.minRecvPoints = RELAXED_COLLECTION_QUALITY_MIN_RECV_POINTS;
            config.minRecvAmplitude = RELAXED_COLLECTION_QUALITY_MIN_RECV_AMPLITUDE;
            config.minBatteryVoltage = RELAXED_COLLECTION_QUALITY_MIN_BATTERY_VOLTAGE;
            config.minGpsAccuracy = RELAXED_COLLECTION_QUALITY_MIN_GPS_ACCURACY;
            config.maxTemperature = RELAXED_COLLECTION_QUALITY_MAX_TEMPERATURE;
            return config;
        }
        config.minRecvPoints = DEFAULT_COLLECTION_QUALITY_MIN_RECV_POINTS;
        config.minRecvAmplitude = DEFAULT_COLLECTION_QUALITY_MIN_RECV_AMPLITUDE;
        config.minBatteryVoltage = DEFAULT_COLLECTION_QUALITY_MIN_BATTERY_VOLTAGE;
        config.minGpsAccuracy = DEFAULT_COLLECTION_QUALITY_MIN_GPS_ACCURACY;
        config.maxTemperature = DEFAULT_COLLECTION_QUALITY_MAX_TEMPERATURE;
        return config;
    }

    public static void saveCollectionQualityConfig(Context context, @Nullable CollectionQualityConfig config) {
        CollectionQualityConfig safeConfig = config == null
                ? createDefaultCollectionQualityConfig()
                : config;
        prefs(context).edit()
                .putString(
                        KEY_COLLECTION_QUALITY_PROFILE,
                        (safeConfig.profile == null
                                ? CollectionQualityProfile.STANDARD
                                : safeConfig.profile).getCode())
                .putBoolean(
                        KEY_COLLECTION_QUALITY_BLOCK_SAVE_ON_FAILURE,
                        safeConfig.blockSaveOnFailure)
                .apply();
    }

    public static void saveCollectionTemplate(Context context,
                                              String databaseName,
                                              int slot,
                                              @Nullable CollectionParameterEntity parameters) {
        if (parameters == null || slot < 1 || slot > COLLECTION_TEMPLATE_SLOT_COUNT) {
            return;
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putFloat(collectionTemplateKey(databaseName, slot, "transmitCurrent"), parameters.getTransmitCurrent());
        editor.putInt(collectionTemplateKey(databaseName, slot, "sampleFrequency"), parameters.getSampleFrequency());
        editor.putInt(collectionTemplateKey(databaseName, slot, "collectionCount"), parameters.getCollectionCount());
        editor.putFloat(collectionTemplateKey(databaseName, slot, "sampleTime"), parameters.getSampleTime());
        editor.putFloat(collectionTemplateKey(databaseName, slot, "electrodeDistance"), parameters.getElectrodeDistance());
        editor.putString(collectionTemplateKey(databaseName, slot, "transmitterDirection"), safeTrim(parameters.getTransmitterDirection()));
        editor.putString(collectionTemplateKey(databaseName, slot, "customParameters"), safeTrim(parameters.getCustomParameters()));
        editor.apply();
    }

    @Nullable
    public static CollectionParameterEntity getCollectionTemplate(Context context,
                                                                  String databaseName,
                                                                  int slot) {
        if (slot < 1 || slot > COLLECTION_TEMPLATE_SLOT_COUNT) {
            return null;
        }
        SharedPreferences sharedPreferences = prefs(context);
        String transmitCurrentKey = collectionTemplateKey(databaseName, slot, "transmitCurrent");
        if (!sharedPreferences.contains(transmitCurrentKey)) {
            return null;
        }
        CollectionParameterEntity parameters = new CollectionParameterEntity();
        parameters.setTransmitCurrent(sharedPreferences.getFloat(transmitCurrentKey, 25f));
        parameters.setSampleFrequency(sharedPreferences.getInt(
                collectionTemplateKey(databaseName, slot, "sampleFrequency"),
                300));
        parameters.setCollectionCount(sharedPreferences.getInt(
                collectionTemplateKey(databaseName, slot, "collectionCount"),
                2));
        parameters.setSampleTime(sharedPreferences.getFloat(
                collectionTemplateKey(databaseName, slot, "sampleTime"),
                10f));
        parameters.setElectrodeDistance(sharedPreferences.getFloat(
                collectionTemplateKey(databaseName, slot, "electrodeDistance"),
                0f));
        parameters.setTransmitterDirection(safeTrim(sharedPreferences.getString(
                collectionTemplateKey(databaseName, slot, "transmitterDirection"),
                "")));
        parameters.setCustomParameters(safeTrim(sharedPreferences.getString(
                collectionTemplateKey(databaseName, slot, "customParameters"),
                "")));
        return parameters;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = safeTrim(baseUrl);
        if (trimmed.isEmpty()
                || LEGACY_PLACEHOLDER_SYNC_BASE_URL.equals(trimmed)
                || LEGACY_HTTPS_SYNC_BASE_URL.equals(trimmed)
                || LEGACY_HTTP_SYNC_BASE_URL.equals(trimmed)) {
            return DEFAULT_SYNC_BASE_URL;
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static int sanitizePositiveInt(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static float sanitizePositiveFloat(float value, float fallback) {
        return Float.isFinite(value) && value > 0f ? value : fallback;
    }

    private static String safeIdentifierPart(String value) {
        String trimmed = safeTrim(value);
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String projectKey(String prefix, String databaseName) {
        return prefix + safeIdentifierPart(databaseName);
    }

    private static String collectionTemplateKey(String databaseName, int slot, String field) {
        return projectKey(KEY_COLLECTION_TEMPLATE_PREFIX + slot + "_" + field + "_", databaseName);
    }
}
