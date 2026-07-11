package cn.zjl.datacollector.net.api.response;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import java.util.List;

/**
 * 数据同步响应对象，服务器返回的数据上传结果。
 * <p>
 * 该类对应 POST /receive/android 接口的响应体，包含上传批次的基本信息、
 * 保存的记录数量、去重状态和上传者信息。客户端可根据此响应判断上传是否成功，
 * 以及哪些测点被识别为重复数据。
 */
public class SyncResponse {
    /**
     * 响应状态码
     * <ul>
     *   <li>200: 上传成功</li>
     *   <li>400: 请求参数错误</li>
     *   <li>500: 服务器内部错误</li>
     * </ul>
     */
    public int code;

    /**
     * 响应消息描述
     */
    public String message;

    /**
     * 响应数据载荷，包含上传结果的详细信息
     */
    public DataPayload data;

    /**
     * 服务器响应时间戳（Unix 毫秒时间戳）
     */
    public long timestamp;

    /**
     * 数据载荷内部类，封装上传结果的详细属性。
     */
    public static class DataPayload {
        /**
         * 项目 ID（服务器分配）
         */
        public Long projectId;

        /**
         * 项目编码（业务唯一标识）
         */
        public String projectCode;

        /**
         * 项目名称
         */
        public String projectName;

        /**
         * 批次号（与请求中的 batchNo 对应）
         */
        public String batchNo;

        /**
         * 工程编码
         */
        public String engineeringCode;

        /**
         * 测线编码
         */
        public String lineCode;

        /**
         * 测线 ID（服务器分配）
         */
        public Long lineId;

        /**
         * 项目/测线归属模式，例如 request-project 或 device-binding。
         */
        public String assignmentMode;

        /**
         * 设备绑定或后端最终分配的测线 ID。
         */
        public Long assignedLineId;

        /**
         * 设备绑定或后端最终分配的测线编码。
         */
        public String assignedLineCode;

        /**
         * 成功保存的测点数量
         */
        public Integer savedPointCount;

        /**
         * 成功保存的采样记录数量（波形数据条数）
         */
        public Integer savedSampleCount;

        /**
         * 保存的采样记录 ID 列表
         * <p>可用于后续查询或更新操作</p>
         */
        public List<Long> sampleIds;

        /**
         * 是否为重复数据
         * <p>true 表示该批次数据已存在，服务器可能跳过存储或进行合并</p>
         */
        public Boolean duplicated;

        /**
         * 上传渠道（如 "android", "qt", "web"）
         */
        public String uploadChannel;

        /**
         * 上传时间戳（Unix 毫秒时间戳）
         */
        public Long uploadedAt;

        /**
         * 上传者的用户 ID
         */
        public Long uploadedByUserId;

        /**
         * 上传者的用户名
         */
        public String uploadedByUsername;

        /**
         * 上传者的角色 ID
         */
        public Long uploadedByRoleId;

        /**
         * 上传者的角色代码
         */
        public String uploadedByRole;

        /**
         * 上传者的角色名称
         */
        public String uploadedByRoleName;
    }
}
