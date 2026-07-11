package cn.zjl.datacollector.sync.auth;

/**
 * 阅读提示：同步登录与 Token 管理器：负责默认账号登录、缓存 Token、401 后清理并重试认证。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.net.api.response.ApiResponse;
import cn.zjl.datacollector.net.api.response.AuthLoginPayload;
import cn.zjl.datacollector.net.api.request.AuthLoginRequest;
import cn.zjl.datacollector.net.api.RetrofitClient;
import cn.zjl.datacollector.net.api.SyncApiService;
import cn.zjl.datacollector.util.AppSettings;
import retrofit2.Response;

/**
 * 同步接口的登录与 token 管理。
 */
public final class SyncAuthManager {

    /** 异步登录回调，用于上传页刷新 Token 时把结果返回 UI 层。 */
    public interface Callback {
        void onComplete(@NonNull LoginResult result);
    }

    /** 登录或 Token 校验结果。retryable 用于区分网络类错误和配置类错误。 */
    public static final class LoginResult {
        public final boolean success;
        public final boolean retryable;
        public final String message;
        public final String token;

        private LoginResult(boolean success, boolean retryable, String message, String token) {
            this.success = success;
            this.retryable = retryable;
            this.message = message;
            this.token = token;
        }

        public static LoginResult success(String token) {
            return new LoginResult(true, false, "", token);
        }

        public static LoginResult failure(String message, boolean retryable) {
            return new LoginResult(false, retryable, message, "");
        }
    }

    /** 登录请求串行执行，避免多次点击刷新 Token 时同时写入 SharedPreferences。 */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Context context;

    public SyncAuthManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasUsableAuth() {
        // 有缓存 Token 可以直接请求；没有 Token 但有账号密码时，也可以在请求前自动登录。
        return !AppSettings.getSyncToken(context).isEmpty() || AppSettings.hasSyncCredentials(context);
    }

    public boolean canLogin() {
        return AppSettings.hasSyncCredentials(context);
    }

    public void clearToken() {
        AppSettings.clearSyncToken(context);
    }

    public void loginAsync(@NonNull Callback callback) {
        // UI 层不能阻塞主线程，因此异步封装同步登录逻辑。
        EXECUTOR.execute(() -> callback.onComplete(loginSync()));
    }

    public LoginResult ensureToken() {
        // 上传前统一调用该方法：优先复用缓存 Token，缺失时再用默认账号登录。
        String token = AppSettings.getSyncToken(context);
        if (!token.isEmpty()) {
            return LoginResult.success(token);
        }
        if (!canLogin()) {
            return LoginResult.failure(
                    context.getString(R.string.error_sync_missing_auth),
                    false);
        }
        return loginSync();
    }

    public LoginResult loginSync() {
        // Base URL 和账号密码属于本地配置错误，失败后不应盲目重试。
        if (!AppSettings.isSyncBaseUrlConfigured(context)) {
            return LoginResult.failure(
                    context.getString(R.string.error_sync_missing_base_url),
                    false);
        }
        String username = AppSettings.getSyncUsername(context);
        String password = AppSettings.getSyncPassword(context);
        if (username.isEmpty() || password.isEmpty()) {
            return LoginResult.failure(
                    context.getString(R.string.error_sync_missing_credentials),
                    false);
        }

        AuthLoginRequest request = new AuthLoginRequest();
        request.username = username;
        request.password = password;

        SyncApiService apiService = RetrofitClient.getSyncApiService(context);
        try {
            Response<ApiResponse<AuthLoginPayload>> response = apiService.login(request).execute();
            if (response.isSuccessful()) {
                // 后端业务 code=200 且 data.token 非空才认为登录真正成功。
                ApiResponse<AuthLoginPayload> body = response.body();
                String token = body != null && body.data != null ? safeTrim(body.data.token) : "";
                if (body != null && body.code == 200 && !token.isEmpty()) {
                    AppSettings.setSyncToken(context, token);
                    return LoginResult.success(token);
                }
                return LoginResult.failure(resolveBodyMessage(body), false);
            }
            return LoginResult.failure(resolveHttpMessage(response.code()), isRetryableStatus(response.code()));
        } catch (IOException e) {
            return LoginResult.failure(resolveThrowableMessage(e), true);
        } catch (Exception e) {
            return LoginResult.failure(resolveThrowableMessage(e), false);
        }
    }

    private String resolveBodyMessage(ApiResponse<AuthLoginPayload> body) {
        if (body != null && body.message != null && !body.message.trim().isEmpty()) {
            return body.message.trim();
        }
        return context.getString(R.string.error_sync_login_failed);
    }

    private String resolveHttpMessage(int code) {
        if (code > 0) {
            return context.getString(R.string.error_sync_http_status, code);
        }
        return context.getString(R.string.error_sync_login_failed);
    }

    private boolean isRetryableStatus(int code) {
        // 这些状态通常受网络或服务端临时状态影响，交给上层做退避重试。
        return code == 408 || code == 429 || code >= 500;
    }

    private String resolveThrowableMessage(Exception error) {
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message.trim();
        }
        return context.getString(R.string.error_sync_login_failed);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
