package cn.zjl.datacollector.sync.inspect;

/**
 * 阅读提示：数据同步模块代码：负责登录认证、请求组装、上传执行和同步结果回写。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;

import cn.zjl.datacollector.net.api.response.ApiResponse;
import cn.zjl.datacollector.net.api.response.AuthLoginPayload;
import cn.zjl.datacollector.net.api.response.ProjectPagePayload;
import cn.zjl.datacollector.net.api.response.ProjectPayload;
import cn.zjl.datacollector.net.api.RetrofitClient;
import cn.zjl.datacollector.net.api.response.SampleFullPayload;
import cn.zjl.datacollector.net.api.SyncApiService;
import cn.zjl.datacollector.net.api.response.TaskOptionPayload;
import cn.zjl.datacollector.sync.auth.SyncAuthManager;
import retrofit2.Response;

/**
 * 后端联调查询器。
 *
 * <p>该类封装对接清单中推荐的查询接口：当前用户、项目分页、任务选项和样本详情。
 * 它不负责修改本地数据库，只作为上传前选择服务端项目/测线、上传后校验入库结果的辅助入口。</p>
 */
public final class BackendSyncInspector {

    private final Context context;
    private final SyncAuthManager authManager;

    public BackendSyncInspector(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.authManager = new SyncAuthManager(this.context);
    }

    @Nullable
    public AuthLoginPayload.UserPayload getCurrentUserSync() throws IOException {
        // 用于联调时确认 Token 是否有效，以及当前账号是否具备 Android 上传权限。
        SyncApiService apiService = ensureApiService();
        Response<ApiResponse<AuthLoginPayload.UserPayload>> response = apiService.getCurrentUser().execute();
        ApiResponse<AuthLoginPayload.UserPayload> body = requireSuccess(response);
        return body.data;
    }

    @Nullable
    public ProjectPagePayload getProjectPageSync(int current,
                                                 int size,
                                                 @Nullable String projectName,
                                                 @Nullable Integer status) throws IOException {
        // 上传前可通过项目分页拿到服务端 projectId，本地工程 ID 不能直接当服务端 projectId 使用。
        SyncApiService apiService = ensureApiService();
        Response<ApiResponse<ProjectPagePayload>> response = apiService
                .getProjectPage(current, size, emptyToNull(projectName), status)
                .execute();
        ApiResponse<ProjectPagePayload> body = requireSuccess(response);
        return body.data;
    }

    @Nullable
    public ProjectPayload getProjectDetailSync(long projectId) throws IOException {
        // 查询单个服务端项目的编码和名称，用于核对本地绑定关系。
        SyncApiService apiService = ensureApiService();
        Response<ApiResponse<ProjectPayload>> response = apiService.getProjectDetail(projectId).execute();
        ApiResponse<ProjectPayload> body = requireSuccess(response);
        return body.data;
    }

    @Nullable
    public List<TaskOptionPayload> getTaskOptionsSync(long projectId) throws IOException {
        // 后端按项目返回可用测线/任务选项，设备上报时可用它补齐 lineId。
        SyncApiService apiService = ensureApiService();
        Response<ApiResponse<List<TaskOptionPayload>>> response = apiService.getTaskOptions(projectId).execute();
        ApiResponse<List<TaskOptionPayload>> body = requireSuccess(response);
        return body.data;
    }

    @Nullable
    public SampleFullPayload getSampleFullSync(long sampleId) throws IOException {
        // 上传成功后按 sampleId 回查完整样本，用于验证波形、监控和质量字段是否真实入库。
        SyncApiService apiService = ensureApiService();
        Response<ApiResponse<SampleFullPayload>> response = apiService.getSampleFull(sampleId).execute();
        ApiResponse<SampleFullPayload> body = requireSuccess(response);
        return body.data;
    }

    private SyncApiService ensureApiService() throws IOException {
        // 所有查询接口都需要鉴权；这里集中处理 Token 获取，调用方只关心业务结果。
        SyncAuthManager.LoginResult authResult = authManager.ensureToken();
        if (!authResult.success) {
            throw new IOException(authResult.message);
        }
        return RetrofitClient.getSyncApiService(context);
    }

    private <T> ApiResponse<T> requireSuccess(Response<ApiResponse<T>> response) throws IOException {
        // 同时检查 HTTP 状态和后端业务 code，避免 HTTP 200 但业务失败被误判为成功。
        if (response == null) {
            throw new IOException("后端响应为空");
        }
        if (!response.isSuccessful()) {
            throw new IOException("HTTP " + response.code());
        }
        ApiResponse<T> body = response.body();
        if (body == null) {
            throw new IOException("后端响应体为空");
        }
        if (body.code != 200) {
            throw new IOException(body.message == null || body.message.trim().isEmpty()
                    ? "业务响应失败: " + body.code
                    : body.message.trim());
        }
        return body;
    }

    @Nullable
    private String emptyToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
