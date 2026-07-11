package cn.zjl.datacollector.net.api.response;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

/**
 * 服务端项目记录。
 */
public class ProjectPayload {
    /** 服务端项目 ID，是 Android 上传时最重要的项目归属字段。 */
    public Long projectId;

    /** 服务端项目编码。 */
    public String projectCode;

    /** 服务端项目名称。 */
    public String projectName;

    /** 项目状态，具体含义以后端字典为准。 */
    public Integer status;
}
