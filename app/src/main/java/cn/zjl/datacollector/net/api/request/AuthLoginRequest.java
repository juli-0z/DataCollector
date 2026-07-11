package cn.zjl.datacollector.net.api.request;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

/**
 * 用户登录请求体，用于向服务器发送认证信息。
 * <p>
 * 该类对应 POST /auth/login 接口的请求参数，
 * 包含用户名和密码，通过 JSON 格式发送给服务器进行身份验证。
 */
public class AuthLoginRequest {
    /**
     * 用户名（登录账号）
     * <p>通常为邮箱、手机号或自定义用户名</p>
     */
    public String username;

    /**
     * 用户密码
     * <p>建议在前端进行哈希处理后传输，或使用 HTTPS 保证安全</p>
     */
    public String password;
}
