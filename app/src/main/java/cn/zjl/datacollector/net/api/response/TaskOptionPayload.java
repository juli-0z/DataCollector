package cn.zjl.datacollector.net.api.response;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

/**
 * 服务端任务/测线选项，对应 GET /device/task-options。
 */
public class TaskOptionPayload {
    /** 服务端任务/测线 ID，设备状态上报时可传 lineId。 */
    public Long lineId;

    /** 测线编码，上传测量数据时必须传 lineCode。 */
    public String lineCode;

    /** 所属服务端项目 ID。 */
    public Long projectId;

    /** 后端展示用任务名称。 */
    public String taskName;
}
