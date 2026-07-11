package cn.zjl.datacollector.sync.executor;

/**
 * 阅读提示：项目数据上传执行器：按项目遍历未同步测点，组装单测点 JSON，请求后端并回写同步状态。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.AppDatabase;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.net.api.RetrofitClient;
import cn.zjl.datacollector.net.api.SyncApiService;
import cn.zjl.datacollector.net.api.request.SyncRequest;
import cn.zjl.datacollector.net.api.response.SyncResponse;
import cn.zjl.datacollector.sync.auth.SyncAuthManager;
import cn.zjl.datacollector.sync.request.SinglePointSyncRequestFactory;
import cn.zjl.datacollector.util.AppSettings;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * 执行项目测点上传，可同时给 WorkManager 和手动上传页面复用。
 *
 * <p>上传链路的核心原则是“按测点逐条上传，逐条回写状态”。这样即使中途网络失败，
 * 已成功的测点不会重复标记为失败，未成功的测点也能在上传任务中心继续筛选和重试。</p>
 */
public final class ProjectSyncExecutor {

    private static final String TAG = "ProjectSyncExecutor";

    /** 单个工程的上传结果，供上传任务中心展示成功数、失败数和剩余待传数。 */
    public static final class ProjectSyncResult {
        public ProjectEntity project;
        /** 本次进入上传队列的测点数量。 */
        public int pendingPointCount;
        /** 已处理测点数量，包含成功和失败。 */
        public int processedPointCount;
        /** 本次失败测点数量。 */
        public int failedPointCount;
        /** 上传结束后，本地仍未同步的测点数量。 */
        public int remainingUnsyncedCount;
        /** 上传结束后，本地仍处于失败状态的测点数量。 */
        public int remainingFailedPointCount;
        /** 本次成功同步的测点数量。 */
        public int syncedCount;
        /** 本工程是否整体成功。 */
        public boolean success;
        /** 是否因为无数据、导入库等原因跳过。 */
        public boolean skipped;
        /** 网络异常、服务器临时错误等可重试失败。 */
        public boolean retryableFailure;
        /** 配置缺失、数据非法等不应盲目重试的失败。 */
        public boolean fatalFailure;
        public long syncedAt;
        public String message = "";
    }

    public interface ProgressListener {
        void onPointProgress(ProgressEvent event);
    }

    /** 测点级进度事件，上传页通过它刷新进度条和当前测点信息。 */
    public static final class ProgressEvent {
        public ProjectEntity project;
        public MeasurementPointEntity point;
        public int projectIndex;
        public int totalProjects;
        public int processedPointCount;
        public int totalPointCount;
        public int syncedPointCount;
        public int failedPointCount;
        public boolean pointFinished;
        public String message = "";
    }

    public static final class SyncRunResult {
        /** 每个工程一条结果记录。 */
        public final List<ProjectSyncResult> projectResults = new ArrayList<>();
        /** 本次批量任务总成功测点数。 */
        public int totalSyncedCount;
        /** 是否存在至少一个可重试失败。 */
        public boolean hasRetryableFailure;
        /** 是否存在至少一个致命失败。 */
        public boolean hasFatalFailure;
    }

    private final Context context;
    private final Gson gson = new Gson();

    public ProjectSyncExecutor(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public SyncRunResult syncProjects(@NonNull List<ProjectEntity> projects, boolean allowImported) {
        // 批量上传入口：通常由 WorkManager 或上传任务中心调用。
        SyncRunResult runResult = new SyncRunResult();
        // Retrofit、请求工厂和认证管理器在一次批量上传中复用，减少重复初始化。
        SyncApiService apiService = RetrofitClient.getSyncApiService(context);
        SinglePointSyncRequestFactory requestFactory = new SinglePointSyncRequestFactory(context);
        SyncAuthManager authManager = new SyncAuthManager(context);
        for (ProjectEntity project : projects) {
            ProjectSyncResult projectResult = syncProject(
                    project,
                    apiService,
                    requestFactory,
                    authManager,
                    allowImported,
                    false,
                    false,
                    runResult.projectResults.size() + 1,
                    projects.size(),
                    null);
            runResult.projectResults.add(projectResult);
            runResult.totalSyncedCount += projectResult.syncedCount;
            runResult.hasRetryableFailure = runResult.hasRetryableFailure || projectResult.retryableFailure;
            runResult.hasFatalFailure = runResult.hasFatalFailure || projectResult.fatalFailure;
        }
        return runResult;
    }

    public ProjectSyncResult syncProject(@NonNull ProjectEntity project, boolean allowImported) {
        return syncProject(project, allowImported, false);
    }

    public ProjectSyncResult syncProject(@NonNull ProjectEntity project,
                                         boolean allowImported,
                                         boolean failedOnly) {
        return syncProject(project, allowImported, failedOnly, 1, 1, null);
    }

    public ProjectSyncResult syncProject(@NonNull ProjectEntity project,
                                         boolean allowImported,
                                         boolean failedOnly,
                                         int projectIndex,
                                         int totalProjects,
                                         ProgressListener progressListener) {
        return syncProject(project, allowImported, failedOnly, false, projectIndex, totalProjects, progressListener);
    }

    public ProjectSyncResult syncProject(@NonNull ProjectEntity project,
                                         boolean allowImported,
                                         boolean failedOnly,
                                         boolean includeSyncedPoints,
                                         int projectIndex,
                                         int totalProjects,
                                         ProgressListener progressListener) {
        // 单工程公开入口：创建网络 API、请求工厂和认证管理器后转入核心实现。
        SyncApiService apiService = RetrofitClient.getSyncApiService(context);
        SinglePointSyncRequestFactory requestFactory = new SinglePointSyncRequestFactory(context);
        SyncAuthManager authManager = new SyncAuthManager(context);
        return syncProject(
                project,
                apiService,
                requestFactory,
                authManager,
                allowImported,
                failedOnly,
                includeSyncedPoints,
                projectIndex,
                totalProjects,
                progressListener);
    }

    private ProjectSyncResult syncProject(ProjectEntity project,
                                          SyncApiService apiService,
                                          SinglePointSyncRequestFactory requestFactory,
                                          SyncAuthManager authManager,
                                          boolean allowImported,
                                          boolean failedOnly,
                                          boolean includeSyncedPoints,
                                          int projectIndex,
                                          int totalProjects,
                                          ProgressListener progressListener) {
        ProjectSyncResult result = new ProjectSyncResult();
        result.project = project;
        if (project == null || project.getDatabaseName() == null || project.getDatabaseName().trim().isEmpty()) {
            result.skipped = true;
            result.success = true;
            result.message = context.getString(R.string.data_upload_status_missing_database);
            return result;
        }
        if (project.getImported() && !allowImported) {
            // 导入库/样例库默认偏回放，避免误把历史数据上传到后端。
            result.skipped = true;
            result.success = true;
            result.message = context.getString(R.string.data_upload_status_imported_skipped);
            return result;
        }

        DataRepository repository = new DataRepository(context, project.getDatabaseName());
        // failedOnly 用于“失败点重试”；includeSyncedPoints 只给手动测试上传使用，允许重复提交已同步测点。
        List<MeasurementPointEntity> uploadPoints = failedOnly
                ? repository.getFailedUnsyncedPointsSync()
                : (includeSyncedPoints
                ? repository.getUploadablePointsSync()
                : repository.getUnsyncedPointsSync());
        result.pendingPointCount = uploadPoints.size();
        if (uploadPoints.isEmpty()) {
            result.skipped = true;
            result.success = true;
            result.message = context.getString(failedOnly
                    ? R.string.data_upload_status_no_failed_pending
                    : R.string.data_upload_status_no_pending);
            return result;
        }

        String missingConfigError = resolveMissingConfigError(project, authManager);
        if (missingConfigError != null) {
            // 远端 projectId、账号密码、设备编号等配置缺失属于致命错误，继续请求只会批量失败。
            markPointsSyncError(repository, uploadPoints, missingConfigError);
            result.processedPointCount = uploadPoints.size();
            result.failedPointCount = uploadPoints.size();
            result.fatalFailure = true;
            result.message = missingConfigError;
            updateRemainingCounts(result, repository);
            return result;
        }

        long lastSyncedAt = 0L;
        String firstError = null;
        for (MeasurementPointEntity point : uploadPoints) {
            // 先发一次“开始处理当前点”的进度事件，便于界面显示当前测点编号。
            dispatchProgress(progressListener, project, point, projectIndex, totalProjects, result, false, "");
            try {
                // 每个测点独立读取完整数据，组装单测点 JSON，与后端接口的 points[0] 结构对应。
                DataRepository.PointData pointData = repository.getAllDataByPointSync(point.getId());
                SurveyLineEntity line = repository.getSurveyLineSync(point.getDataLineId());
                SyncRequest request = requestFactory.build(project, line, pointData);
                UploadAttemptResult uploadResult = uploadPoint(apiService, authManager, request);
                if (uploadResult.success) {
                    // 只有后端明确返回成功后，才把本地测点标记为已同步。
                    repository.markPointAsSyncedSync(point.getId());
                    result.syncedCount++;
                    lastSyncedAt = System.currentTimeMillis();
                } else {
                    // 失败原因写回测点表，上传页可以按失败点筛选和展示最近错误。
                    repository.markPointSyncErrorSync(point.getId(), uploadResult.errorMessage);
                    if (firstError == null || firstError.trim().isEmpty()) {
                        firstError = uploadResult.errorMessage;
                    }
                    if (uploadResult.retryable) {
                        result.retryableFailure = true;
                    } else {
                        result.fatalFailure = true;
                    }
                    result.failedPointCount++;
                }
                result.processedPointCount++;
                dispatchProgress(
                        progressListener,
                        project,
                        point,
                        projectIndex,
                        totalProjects,
                        result,
                        true,
                        uploadResult.errorMessage);
            } catch (IOException e) {
                String errorMessage = resolveThrowableMessage(e);
                repository.markPointSyncErrorSync(point.getId(), errorMessage);
                if (firstError == null || firstError.trim().isEmpty()) {
                    firstError = errorMessage;
                }
                result.retryableFailure = true;
                result.failedPointCount++;
                result.processedPointCount++;
                dispatchProgress(
                        progressListener,
                        project,
                        point,
                        projectIndex,
                        totalProjects,
                        result,
                        true,
                        errorMessage);
                Log.w(TAG, "上传测点失败，将在后续重试: pointId=" + point.getId(), e);
            } catch (Exception e) {
                String errorMessage = resolveThrowableMessage(e);
                repository.markPointSyncErrorSync(point.getId(), errorMessage);
                if (firstError == null || firstError.trim().isEmpty()) {
                    firstError = errorMessage;
                }
                result.fatalFailure = true;
                result.failedPointCount++;
                result.processedPointCount++;
                dispatchProgress(
                        progressListener,
                        project,
                        point,
                        projectIndex,
                        totalProjects,
                        result,
                        true,
                        errorMessage);
                Log.e(TAG, "上传测点失败: pointId=" + point.getId(), e);
            }
        }

        if (lastSyncedAt > 0L) {
            // 项目级最近同步时间只在至少一个测点成功后更新，避免失败任务刷新“已同步”观感。
            updateProjectLastSynced(project, repository, lastSyncedAt);
            result.syncedAt = lastSyncedAt;
        }

        result.success = !result.retryableFailure && !result.fatalFailure;
        if (result.success) {
            result.message = context.getString(R.string.data_upload_status_success, result.syncedCount);
        } else if (result.syncedCount > 0) {
            result.message = context.getString(
                    R.string.data_upload_status_partial,
                    result.syncedCount,
                    result.pendingPointCount,
                    firstError == null ? "" : firstError);
        } else if (firstError != null && !firstError.trim().isEmpty()) {
            result.message = firstError;
        } else {
            result.message = context.getString(R.string.error_sync_empty_response);
        }
        updateRemainingCounts(result, repository);
        return result;
    }

    private UploadAttemptResult uploadPoint(SyncApiService apiService,
                                            SyncAuthManager authManager,
                                            SyncRequest request) throws IOException {
        // 上传前先确保 Token 可用；如果未登录或 Token 过期，会在 SyncAuthManager 内完成登录。
        SyncAuthManager.LoginResult ensureResult = authManager.ensureToken();
        if (!ensureResult.success) {
            return UploadAttemptResult.failure(ensureResult.message, ensureResult.retryable);
        }

        Response<SyncResponse> response = apiService.uploadData(request).execute();
        if (isUploadSuccessful(response)) {
            return UploadAttemptResult.success();
        }

        if (response != null && response.code() == 401 && authManager.canLogin()) {
            // 401 常见原因是 Token 失效：清理缓存后重新登录，再给当前测点一次补救机会。
            authManager.clearToken();
            SyncAuthManager.LoginResult reloginResult = authManager.loginSync();
            if (!reloginResult.success) {
                return UploadAttemptResult.failure(reloginResult.message, reloginResult.retryable);
            }
            Response<SyncResponse> retryResponse = apiService.uploadData(request).execute();
            if (isUploadSuccessful(retryResponse)) {
                return UploadAttemptResult.success();
            }
            return UploadAttemptResult.failure(
                    resolveErrorMessage(retryResponse),
                    isRetryable(retryResponse));
        }

        return UploadAttemptResult.failure(
                resolveErrorMessage(response),
                isRetryable(response));
    }

    private String resolveMissingConfigError(ProjectEntity project, SyncAuthManager authManager) {
        if (!AppSettings.isSyncBaseUrlConfigured(context)) {
            return context.getString(R.string.error_sync_missing_base_url);
        }
        if (!authManager.hasUsableAuth()) {
            return context.getString(R.string.error_sync_missing_auth);
        }
        if (AppSettings.getSyncDeviceId(context).isEmpty()) {
            return context.getString(R.string.error_sync_missing_device_id);
        }
        if (project == null || project.getDatabaseName() == null
                || !AppSettings.isProjectSyncConfigured(context, project.getDatabaseName())) {
            return context.getString(R.string.error_sync_missing_remote_project_id);
        }
        return null;
    }

    private void markPointsSyncError(DataRepository repository,
                                     List<MeasurementPointEntity> points,
                                     String error) {
        for (MeasurementPointEntity point : points) {
            repository.markPointSyncErrorSync(point.getId(), error);
        }
    }

    private boolean isUploadSuccessful(Response<SyncResponse> response) {
        if (response == null || !response.isSuccessful()) {
            return false;
        }
        SyncResponse body = response.body();
        return body != null && body.code == 200;
    }

    private boolean isRetryable(Response<SyncResponse> response) {
        if (response == null) {
            return true;
        }
        int statusCode = response.code();
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private String resolveErrorMessage(Response<SyncResponse> response) {
        // 错误优先级：业务 message > errorBody 中的 message > HTTP 状态码 > 空响应。
        if (response == null) {
            return context.getString(R.string.error_sync_empty_response);
        }

        SyncResponse body = response.body();
        if (body != null && body.message != null && !body.message.trim().isEmpty()) {
            return body.message.trim();
        }

        String parsedErrorBodyMessage = parseErrorBodyMessage(response.errorBody());
        if (parsedErrorBodyMessage != null) {
            return parsedErrorBodyMessage;
        }

        if (response.code() > 0) {
            return context.getString(R.string.error_sync_http_status, response.code());
        }
        return context.getString(R.string.error_sync_empty_response);
    }

    private String parseErrorBodyMessage(ResponseBody errorBody) {
        if (errorBody == null) {
            return null;
        }
        try {
            String raw = errorBody.string();
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            SyncResponse parsed = gson.fromJson(raw, SyncResponse.class);
            if (parsed != null && parsed.message != null && !parsed.message.trim().isEmpty()) {
                return parsed.message.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "解析错误响应失败", e);
        }
        return null;
    }

    private String resolveThrowableMessage(Exception error) {
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message.trim();
        }
        return error.getClass().getSimpleName();
    }

    private void updateProjectLastSynced(ProjectEntity indexProject,
                                         DataRepository repository,
                                         long syncedAt) {
        repository.updateProjectLastSyncedSync(syncedAt);
        indexProject.setLastSyncedAt(syncedAt);
        indexProject.setUpdatedAt(syncedAt);
        AppDatabase.getInstance(context).projectDao().update(indexProject);
    }

    private void updateRemainingCounts(ProjectSyncResult result, DataRepository repository) {
        // 上传结束后重新查询数据库，得到真实剩余数量，而不是只依赖内存计数。
        if (result == null || repository == null) {
            return;
        }
        List<MeasurementPointEntity> remainingUnsyncedPoints = repository.getUnsyncedPointsSync();
        List<MeasurementPointEntity> remainingFailedPoints = repository.getFailedUnsyncedPointsSync();
        result.remainingUnsyncedCount = remainingUnsyncedPoints == null ? 0 : remainingUnsyncedPoints.size();
        result.remainingFailedPointCount = remainingFailedPoints == null ? 0 : remainingFailedPoints.size();
    }

    private void dispatchProgress(ProgressListener listener,
                                  ProjectEntity project,
                                  MeasurementPointEntity point,
                                  int projectIndex,
                                  int totalProjects,
                                  ProjectSyncResult result,
                                  boolean pointFinished,
                                  String message) {
        // 进度回调只负责传递状态，不直接触碰 UI，避免同步层依赖 Activity。
        if (listener == null || result == null) {
            return;
        }
        ProgressEvent event = new ProgressEvent();
        event.project = project;
        event.point = point;
        event.projectIndex = projectIndex;
        event.totalProjects = totalProjects;
        event.processedPointCount = result.processedPointCount;
        event.totalPointCount = result.pendingPointCount;
        event.syncedPointCount = result.syncedCount;
        event.failedPointCount = result.failedPointCount;
        event.pointFinished = pointFinished;
        event.message = message == null ? "" : message;
        listener.onPointProgress(event);
    }

    private static final class UploadAttemptResult {
        final boolean success;
        final boolean retryable;
        final String errorMessage;

        private UploadAttemptResult(boolean success, boolean retryable, String errorMessage) {
            this.success = success;
            this.retryable = retryable;
            this.errorMessage = errorMessage;
        }

        static UploadAttemptResult success() {
            return new UploadAttemptResult(true, false, "");
        }

        static UploadAttemptResult failure(String errorMessage, boolean retryable) {
            return new UploadAttemptResult(false, retryable, errorMessage);
        }
    }
}
