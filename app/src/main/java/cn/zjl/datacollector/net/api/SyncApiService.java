package cn.zjl.datacollector.net.api;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import cn.zjl.datacollector.net.api.request.AuthLoginRequest;
import cn.zjl.datacollector.net.api.request.DeviceReportRequest;
import cn.zjl.datacollector.net.api.request.SyncRequest;
import cn.zjl.datacollector.net.api.response.ApiResponse;
import cn.zjl.datacollector.net.api.response.AuthLoginPayload;
import cn.zjl.datacollector.net.api.response.ProjectPagePayload;
import cn.zjl.datacollector.net.api.response.ProjectPayload;
import cn.zjl.datacollector.net.api.response.SampleFullPayload;
import cn.zjl.datacollector.net.api.response.SyncResponse;
import cn.zjl.datacollector.net.api.response.TaskOptionPayload;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 数据同步 API 接口定义，封装与服务器的所有 HTTP 交互。
 * <p>
 * 该接口使用 Retrofit 注解声明 RESTful API 端点，包括：
 * <ul>
 *   <li>用户登录认证</li>
 *   <li>采集数据上传</li>
 *   <li>设备状态上报</li>
 * </ul>
 * 所有方法返回 {@link Call} 对象，支持同步或异步执行。
 */
public interface SyncApiService {

    /**
     * 用户登录接口
     * <p>
     * 向服务器发送用户名和密码，获取 JWT 认证令牌。
     * 该接口不需要认证（通过 X-Skip-Auth Header 标记）。
     *
     * @param request 登录请求体，包含 username 和 password
     * @return 登录响应，包含 Token 和用户信息
     */
    @Headers("X-Skip-Auth: true")  // 跳过认证检查
    @POST("auth/login")            // POST 请求到 /auth/login
    Call<ApiResponse<AuthLoginPayload>> login(@Body AuthLoginRequest request);

    /**
     * 获取当前登录用户信息。
     * <p>用于校验 token 是否有效，以及账号是否具备 Android 上传权限。</p>
     */
    @GET("auth/me")
    Call<ApiResponse<AuthLoginPayload.UserPayload>> getCurrentUser();

    /**
     * 获取服务端项目分页列表。
     * <p>Android 端可通过该接口拿到 projectId，再把本地工程绑定到服务端项目。</p>
     */
    @GET("project/page")
    Call<ApiResponse<ProjectPagePayload>> getProjectPage(@Query("current") int current,
                                                         @Query("size") int size,
                                                         @Query("projectName") String projectName,
                                                         @Query("status") Integer status);

    /**
     * 获取项目详情。
     */
    @GET("project/{projectId}")
    Call<ApiResponse<ProjectPayload>> getProjectDetail(@Path("projectId") long projectId);

    /**
     * 获取某个项目下可绑定的任务/测线选项。
     */
    @GET("device/task-options")
    Call<ApiResponse<java.util.List<TaskOptionPayload>>> getTaskOptions(@Query("projectId") long projectId);

    /**
     * 上传采集数据接口
     * <p>
     * 将野外采集的测点数据（包括波形、参数、监控信息）批量上传到服务器。
     * 该接口需要认证（自动添加 Bearer Token）。
     *
     * @param request 数据同步请求体，包含批次号、设备信息、测点列表等
     * @return 上传响应，包含保存的记录 ID、去重信息等
     */
    @POST("receive/android")  // POST 请求到 /receive/android
    Call<SyncResponse> uploadData(@Body SyncRequest request);

    /**
     * 设备状态上报接口
     * <p>
     * 定期向服务器报告设备的运行状态（电池、信号、位置等）。
     * 该接口需要认证（自动添加 Bearer Token）。
     *
     * @param request 设备状态请求体，包含设备信息和当前状态
     * @return 上报响应，data 字段为 Boolean 表示是否成功
     */
    @POST("device/report/android")  // POST 请求到 /device/report/android
    Call<ApiResponse<Boolean>> reportAndroidDevice(@Body DeviceReportRequest request);

    /**
     * 上传后按 sampleId 查询完整样本详情，用于联调校验是否真实入库。
     */
    @GET("data/playback/sample/full/{sampleId}")
    Call<ApiResponse<SampleFullPayload>> getSampleFull(@Path("sampleId") long sampleId);
}
