package cn.zjl.datacollector.net.api;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import java.util.concurrent.TimeUnit;

import cn.zjl.datacollector.util.AppSettings;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit 网络客户端单例工厂，负责创建和配置 HTTP 通信组件。
 * <p>
 * 该类封装了 OkHttp 和 Retrofit 的初始化逻辑，提供统一的 API 服务实例。
 * 主要功能包括：
 * <ul>
 *   <li>自动添加认证令牌（JWT Bearer Token）</li>
 *   <li>配置连接超时、读取超时和写入超时</li>
 *   <li>设置 JSON 数据转换器（Gson）</li>
 *   <li>支持跳过认证的请求（如登录接口）</li>
 * </ul>
 */
public final class RetrofitClient {

    /**
     * 私有构造函数，防止外部实例化（单例模式）
     */
    private RetrofitClient() {
    }

    /**
     * 获取数据同步 API 服务实例
     * <p>
     * 该方法每次调用都会创建新的 Retrofit 实例，适用于需要动态切换服务器地址的场景。
     * 如果需要复用连接池，建议缓存返回的 SyncApiService 实例。
     *
     * @param context Android 上下文，用于读取应用配置（服务器地址、Token 等）
     * @return 配置好的 SyncApiService 实例，可用于发起网络请求
     */
    public static SyncApiService getSyncApiService(Context context) {
        // 使用 Application Context 避免内存泄漏
        Context appContext = context.getApplicationContext();

        // 构建 OkHttp 客户端，配置拦截器和超时时间
        OkHttpClient client = new OkHttpClient.Builder()
                // 添加请求拦截器，处理认证和通用 Header
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            // 默认接受 JSON 响应
                            .header("Accept", "application/json");

                    // 检查是否需要跳过认证（某些接口如登录不需要 Token）
                    boolean skipAuth = "true".equalsIgnoreCase(original.header("X-Skip-Auth"));
                    builder.removeHeader("X-Skip-Auth"); // 移除自定义 Header，不发送给服务器

                    // 从本地存储获取认证 Token
                    String token = AppSettings.getSyncToken(appContext);

                    // 如果不需要跳过认证且 Token 存在，则添加 Authorization Header
                    if (!skipAuth && !token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }

                    // 继续执行请求链
                    return chain.proceed(builder.build());
                })
                // 配置超时时间
                .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时：建立 TCP 连接的最大时间
                .readTimeout(30, TimeUnit.SECONDS)     // 读取超时：等待服务器响应的最大时间
                .writeTimeout(60, TimeUnit.SECONDS)    // 写入超时：发送请求数据的最大时间（上传数据可能较慢）
                .build();

        // 构建 Retrofit 实例
        Retrofit retrofit = new Retrofit.Builder()
                // 设置服务器基础 URL（从配置中读取）
                .baseUrl(AppSettings.getSyncBaseUrl(appContext))
                // 绑定 OkHttp 客户端
                .client(client)
                // 添加 Gson 转换器，自动将 JSON 转换为 Java 对象
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // 创建 API 接口的动态代理实现
        return retrofit.create(SyncApiService.class);
    }
}
