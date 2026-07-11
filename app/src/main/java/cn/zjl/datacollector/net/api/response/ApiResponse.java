package cn.zjl.datacollector.net.api.response;

/**
 * 阅读提示：后端接口网络模型/服务代码：定义 Retrofit 请求、响应结构和 API 调用入口。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

/**
 * 通用 API 响应结构，用于封装服务器返回的 JSON 数据。
 * <p>
 * 该泛型类遵循 RESTful API 的标准响应格式，包含状态码、消息、数据和时间戳。
 * 所有与服务器的 HTTP 交互都使用此结构作为响应的统一包装。
 *
 * @param <T> 响应数据的类型，由具体业务决定（如登录信息、布尔值等）
 */
public class ApiResponse<T> {
    /**
     * 响应状态码
     * <ul>
     *   <li>200: 成功</li>
     *   <li>401: 未授权</li>
     *   <li>403: 禁止访问</li>
     *   <li>500: 服务器错误</li>
     * </ul>
     */
    public int code;

    /**
     * 响应消息描述
     * <p>通常用于展示给用户或记录日志，例如 "操作成功"、"令牌已过期" 等</p>
     */
    public String message;

    /**
     * 响应数据载荷
     * <p>实际的业务数据，类型由泛型 T 决定。可能为 null（取决于具体接口）</p>
     */
    public T data;

    /**
     * 服务器响应时间戳（Unix 毫秒时间戳）
     * <p>可用于计算网络延迟或检测时钟偏差</p>
     */
    public long timestamp;
}
