package cn.zjl.datacollector.net.api.response;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

/**
 * 登录成功后的数据载荷，包含认证令牌和用户信息。
 * <p>
 * 该类对应服务器返回的 auth/login 接口中的 data 字段，
 * 用于存储 JWT Token 和当前登录用户的详细信息。
 */
public class AuthLoginPayload {
    /**
     * JWT 认证令牌
     * <p>后续请求需在 Header 中携带：Authorization: Bearer {token}</p>
     */
    public String token;

    /**
     * 令牌类型（通常为 "Bearer"）
     */
    public String type;

    /**
     * 令牌过期时间（Unix 毫秒时间戳）
     * <p>客户端可据此判断是否需要重新登录</p>
     */
    public Long expiration;

    /**
     * 用户信息对象
     */
    public UserPayload user;

    /**
     * 用户信息内部类，封装登录用户的详细属性。
     */
    public static class UserPayload {
        /**
         * 用户唯一标识 ID
         */
        public Long userId;

        /**
         * 用户名（登录账号）
         */
        public String username;

        /**
         * 用户角色 ID。
         */
        public Long roleId;

        /**
         * 用户角色代码（如 "admin", "operator"）
         */
        public String role;

        /**
         * 用户角色名称（如 "管理员", "操作员"）
         */
        public String roleName;

        /**
         * 账号状态，例如 active。
         */
        public String status;

        /**
         * 是否具有数据上传权限
         */
        public Boolean canUpload;

        /**
         * 是否具有系统管理权限
         */
        public Boolean canManageSystem;

        /**
         * 是否具有 Qt 平台数据上传权限
         */
        public Boolean canUploadQt;

        /**
         * 是否具有 Android 设备数据上传权限
         */
        public Boolean canUploadAndroid;
    }
}
