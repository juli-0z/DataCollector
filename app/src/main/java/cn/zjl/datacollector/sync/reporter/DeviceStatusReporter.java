package cn.zjl.datacollector.sync.reporter;

/**
 * 阅读提示：数据同步模块代码：负责登录认证、请求组装、上传执行和同步结果回写。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.net.api.response.ApiResponse;
import cn.zjl.datacollector.net.api.request.DeviceReportRequest;
import cn.zjl.datacollector.net.api.RetrofitClient;
import cn.zjl.datacollector.net.api.SyncApiService;
import cn.zjl.datacollector.net.api.response.TaskOptionPayload;
import cn.zjl.datacollector.sync.auth.SyncAuthManager;
import cn.zjl.datacollector.sync.request.DeviceReportRequestFactory;
import retrofit2.Response;

/**
 * 设备在线状态上报器。
 */
public final class DeviceStatusReporter {

    private static final String TAG = "DeviceStatusReporter";
    /** 相同设备状态最短重复上报间隔，避免采集页面频繁刷新时刷爆后端接口。 */
    private static final long MIN_REPEAT_INTERVAL_MS = 30_000L;

    private final Context context;
    private final DeviceReportRequestFactory requestFactory;
    private final SyncAuthManager authManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object reportLock = new Object();

    private long lastReportAt;
    private String lastFingerprint = "";

    public DeviceStatusReporter(Context context) {
        this.context = context.getApplicationContext();
        this.requestFactory = new DeviceReportRequestFactory(this.context);
        this.authManager = new SyncAuthManager(this.context);
    }

    public void reportOnlineIfPossible(@Nullable ProjectEntity project,
                                       @Nullable SurveyLineEntity line,
                                       @Nullable DeviceMonitorEntity monitor) {
        // 工厂返回 null 表示当前工程不适合上报，例如导入库、未绑定远端项目或缺少测线。
        DeviceReportRequest request = requestFactory.build(project, line, monitor);
        if (request == null || !authManager.hasUsableAuth()) {
            return;
        }

        String fingerprint = requestFactory.fingerprint(request);
        long now = System.currentTimeMillis();
        synchronized (reportLock) {
            // 同一状态在 30 秒内只上报一次；失败时会回滚指纹，允许后续重新尝试。
            if (fingerprint.equals(lastFingerprint) && now - lastReportAt < MIN_REPEAT_INTERVAL_MS) {
                return;
            }
            lastFingerprint = fingerprint;
            lastReportAt = now;
        }

        executor.execute(() -> reportOnce(request, fingerprint));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void reportOnce(DeviceReportRequest request, String fingerprint) {
        SyncApiService apiService = RetrofitClient.getSyncApiService(context);
        try {
            // 上报前确保 Token 可用。登录失败时回滚指纹，避免状态被误认为已上报。
            SyncAuthManager.LoginResult authResult = authManager.ensureToken();
            if (!authResult.success) {
                rollbackFingerprint(fingerprint);
                Log.w(TAG, "Skip device report because auth is unavailable: " + authResult.message);
                return;
            }

            alignTaskOptionIfPossible(apiService, request);
            Response<ApiResponse<Boolean>> response = apiService.reportAndroidDevice(request).execute();
            if (isSuccessful(response)) {
                return;
            }

            if (response != null && response.code() == 401 && authManager.canLogin()) {
                // Token 失效时清理缓存并重新登录，只补救一次，避免死循环。
                authManager.clearToken();
                SyncAuthManager.LoginResult reloginResult = authManager.loginSync();
                if (reloginResult.success) {
                    Response<ApiResponse<Boolean>> retryResponse = apiService.reportAndroidDevice(request).execute();
                    if (isSuccessful(retryResponse)) {
                        return;
                    }
                    Log.w(TAG, "Device report failed after re-login, http=" + retryResponse.code());
                } else {
                    Log.w(TAG, "Device report re-login failed: " + reloginResult.message);
                }
            } else {
                Log.w(TAG, "Device report failed, http=" + (response != null ? response.code() : -1));
            }
            rollbackFingerprint(fingerprint);
        } catch (IOException e) {
            rollbackFingerprint(fingerprint);
            Log.w(TAG, "Device report IO failure", e);
        } catch (Exception e) {
            rollbackFingerprint(fingerprint);
            Log.e(TAG, "Device report failure", e);
        }
    }

    private void alignTaskOptionIfPossible(SyncApiService apiService, DeviceReportRequest request) {
        if (request == null || request.projectId == null) {
            return;
        }
        try {
            // 后端任务选项里包含 lineId；如果 lineCode 匹配，就补齐 lineId，便于设备绑定到真实测线。
            Response<ApiResponse<List<TaskOptionPayload>>> response =
                    apiService.getTaskOptions(request.projectId).execute();
            if (response == null || !response.isSuccessful()
                    || response.body() == null || response.body().code != 200
                    || response.body().data == null || response.body().data.isEmpty()) {
                return;
            }
            TaskOptionPayload fallback = null;
            for (TaskOptionPayload option : response.body().data) {
                if (option == null) {
                    continue;
                }
                if (fallback == null) {
                    fallback = option;
                }
                if (sameLineCode(request.lineCode, option.lineCode)) {
                    request.lineId = option.lineId;
                    request.lineCode = option.lineCode;
                    return;
                }
            }
            if (fallback != null && request.lineId == null) {
                // 找不到完全匹配的测线时，使用后端返回的第一个选项作为兜底，保证设备至少能绑定到项目任务。
                request.lineId = fallback.lineId;
                request.lineCode = fallback.lineCode;
            }
        } catch (Exception e) {
            Log.w(TAG, "Skip task option alignment for device report", e);
        }
    }

    private boolean sameLineCode(String localCode, String remoteCode) {
        // 兼容 “2” 与 “2.0” 这类写法，避免前后端格式差异导致测线匹配失败。
        String local = normalizeCode(localCode);
        String remote = normalizeCode(remoteCode);
        return !local.isEmpty() && local.equals(remote);
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            long rounded = Math.round(parsed);
            if (Math.abs(parsed - rounded) < 0.0001d) {
                return String.valueOf(rounded);
            }
        } catch (NumberFormatException ignored) {
        }
        return trimmed;
    }

    private boolean isSuccessful(Response<ApiResponse<Boolean>> response) {
        if (response == null || !response.isSuccessful()) {
            return false;
        }
        ApiResponse<Boolean> body = response.body();
        return body != null && body.code == 200;
    }

    private void rollbackFingerprint(String fingerprint) {
        synchronized (reportLock) {
            if (fingerprint.equals(lastFingerprint)) {
                lastFingerprint = "";
                lastReportAt = 0L;
            }
        }
    }
}
