package cn.zjl.datacollector.net.api.response;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import java.util.ArrayList;
import java.util.List;

/**
 * 项目分页列表响应中的 data 结构，对应 GET /project/page。
 */
public class ProjectPagePayload {
    /** 当前页项目记录。 */
    public List<ProjectPayload> records = new ArrayList<>();

    /** 总记录数。 */
    public Long total;

    /** 当前页码。 */
    public Long current;

    /** 每页数量。 */
    public Long size;
}
