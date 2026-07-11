# DataCollector 野外物探测点数据采集系统

DataCollector 是一款面向野外物探测点作业场景的 Android 采集端，用于完成工程/测线/测点管理、采集设备连接、波形实时显示、本地离线保存、历史回放、数据上传、失败重试和设备诊断等流程。

项目以 Android 原生开发为主，围绕“现场采集 - 本地落库 - 历史回放 - 上传同步 - 异常诊断”构建完整业务闭环，适合弱网、离线、硬件联调和大批量测点数据管理场景。

## 功能概览

- 工程管理：支持创建、导入、删除、导出工程库，按工程、测线、测点、采集记录组织数据。
- 测点采集：支持连接采集设备、配置采集参数、开始/停止采集、保存或重采当前测点。
- 波形显示：使用 MPAndroidChart 展示 Recv、Send、Off 三类波形，支持实时采集展示与历史回放。
- 本地存储：使用 Room/SQLite 保存工程数据、测线、测点、采集参数、波形数据和设备监控数据。
- 设备通信：基于 TCP Socket + Okio 实现设备通信，支持 0x8000 外层协议帧编解码、粘包拆包、校验和异常帧记录。
- 模拟设备：内置模拟设备模式，可在没有真实采集设备时生成状态帧和波形帧，便于演示和调试。
- 数据质检：采集完成后根据 Recv 有效点数、幅值、电池电压、GPS 指标、温度等规则给出保存/重采建议。
- 数据上传：通过 Retrofit/OkHttp/Gson 对接数据中心接口，支持登录认证、测点上传、设备状态上报、上传进度和失败点重试。
- 后台同步：使用 WorkManager 执行后台同步任务，提高弱网或网络恢复后的上传可靠性。
- 诊断与日志：提供设备联调诊断页和操作日志中心，方便追踪 TCP 状态、协议阶段、收发包、校验错误和关键业务操作。
- Wi-Fi 热点连接：支持配置设备 Wi-Fi 热点信息，在真实设备模式下先连接热点再建立 TCP 通信。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言 | Java、Kotlin |
| UI | XML、ViewBinding、ConstraintLayout、RecyclerView、Material Design、Jetpack Compose |
| 架构 | MVVM、Repository、UseCase/Coordinator 风格的业务拆分 |
| 本地存储 | Room、SQLite、SharedPreferences |
| 网络接口 | Retrofit、OkHttp、Gson |
| 设备通信 | TCP Socket、Okio、自定义二进制协议 |
| 后台任务 | WorkManager |
| 图表 | MPAndroidChart |
| 定位/Wi-Fi | Google Play Services Location、Android Wi-Fi API |
| 构建 | Gradle Kotlin DSL、Version Catalog |

## 业务流程

```text
创建/导入工程
    -> 选择工程、测线、测点
    -> 连接真实设备或启用模拟设备
    -> 配置采集参数
    -> 下发参数并启动采集
    -> 接收 TCP 二进制数据帧
    -> 解析设备状态与三类波形
    -> 质检判定
    -> 保存到工程 SQLite 数据库
    -> 历史回放或上传到数据中心
```

## 项目结构

```text
app/src/main/java/cn/zjl/datacollector/
├── MainActivity.java                         # 应用入口，跳转工程列表
├── collection/core/                          # 采集核心流程
│   └── CollectionManager.java                # 参数下发、采集控制、波形/监控解析回调
├── data/                                     # Room 数据层
│   ├── AppDatabase.java                      # 工程索引库与工程数据库管理
│   ├── dao/                                  # DAO：工程、测线、测点、参数、波形、监控等
│   ├── entity/                               # Room 实体：Project、SurveyLine、Point、Waveform 等
│   └── repository/                           # Repository 与工程树读取、波形存储辅助
├── net/
│   ├── api/                                  # Retrofit 接口、请求体、响应体
│   ├── tcp/                                  # TCP 连接、0x8000 协议、诊断状态
│   └── wifi/                                 # 设备 Wi-Fi 热点配置与连接
├── sync/                                     # 数据同步链路
│   ├── auth/                                 # 登录认证与 Token 管理
│   ├── executor/                             # 工程/测点上传执行器
│   ├── inspect/                              # 上传后端联调校验
│   ├── reporter/                             # 设备状态上报
│   ├── request/                              # 同步请求组装
│   └── worker/                               # WorkManager 后台同步任务
├── ui/
│   ├── project/                              # 工程列表与工程管理
│   ├── collection/                           # 采集/回放页面、图表、选择器、工作流
│   ├── upload/                               # 数据上传任务中心
│   ├── diagnostic/                           # Compose 设备诊断页面
│   ├── log/                                  # Compose 操作日志中心
│   ├── playback/                             # 回放列表适配器
│   └── common/                               # 通用导出协调器
└── util/                                     # 设置、导出、字符串、波形编解码工具
```

## 数据存储设计

项目使用“工程索引库 + 独立工程库”的方式管理数据：

- `project_index.sqlite`：保存工程列表等全局索引信息。
- 每个工程对应一个独立 `.sqlite` 文件：保存该工程下的测线、测点、采集参数、波形数据、设备监控和同步状态。

主要数据表包括：

- `work_sets`：工作集/工程集合信息。
- `projects`：工程基本信息、工程库文件名、同步绑定信息等。
- `survey_lines`：测线信息。
- `measurement_points`：测点信息、采集状态、同步状态、失败原因等。
- `collection_parameters`：采集参数，如发射电流、采样频率、采集次数、采样时间、极距等。
- `waveform_data`：Recv、Send、Off 波形数据。
- `device_monitor`：电池、电流、温度、信号、GPS、协议状态、帧计数等设备监控数据。

`AppDatabase` 当前数据库版本为 6，并保留了历史版本迁移逻辑。项目中为了兼容早期样例库，暂时启用了 `fallbackToDestructiveMigration()`；正式生产场景建议补齐所有版本的非破坏性迁移。

## 设备通信协议

设备通信模块位于 `net/tcp`，核心类包括：

- `TcpClientManager`：负责真实 Socket 连接、模拟设备、收发线程、自动重连、接收缓冲区、拆包和诊断记录。
- `GeoDspProtocol`：负责 0x8000 外层协议帧编解码、帧头查找、长度判断、校验和解析结果封装。
- `TcpDiagnosticsStore`：记录连接状态、握手阶段、最近收发包、错误帧、校验错误和事件日志。

当前外层协议结构：

```text
AA 55              # 帧头，小端表示 0x55AA
00 80              # 协议版本，小端表示 0x8000
id                 # 消息/命令编号，2 字节
type               # 请求/响应类型，2 字节
no                 # 消息序号，4 字节
size               # payload 长度，4 字节
baseParity         # 基础字段校验，4 字节
dataParity         # payload XOR32 校验，4 字节
payload            # 业务数据
```

已定义的主要消息包括：

- `DEVICE_INFO`
- `TEM_CONFIG`
- `TEM_START`
- `TEM_GET_OFF_DATA`
- `TEM_GET_SEND_DATA`
- `TEM_STOP`
- `TEM_SEND_DATA`
- `TEM_SEND_MONITOR`

TCP 是流式协议，项目在接收线程中维护缓冲区，并通过 `GeoDspProtocol.tryParse()` 处理半包、粘包、前导噪声、长度异常和校验异常。

## 采集与波形

采集核心由 `CollectionManager` 驱动：

- 设置采集参数后，将电流、采样频率、采集次数、采样时间、极距等字段编码为小端 payload 下发给设备。
- 启动采集后接收设备返回的完整协议帧。
- 对波形 payload 按“波形类型 + 采样点数 + 时间/数值序列”解析。
- 对监控 payload 兼容旧版 16 字节状态结构和新版扩展状态结构。
- 通过主线程回调更新 UI、图表和监控状态。

波形类型：

- `Recv`：接收电压随时间变化曲线。
- `Send`：发射电流波形。
- `Off`：关断阶段响应波形。

## 数据同步

同步模块通过 Retrofit 定义接口，通过 WorkManager 和同步执行器组织上传流程。

主要接口包括：

- `POST auth/login`：登录并获取 Token。
- `GET auth/me`：校验当前用户。
- `GET project/page`：获取服务端工程分页。
- `GET project/{projectId}`：获取工程详情。
- `GET device/task-options`：获取可绑定任务/测线选项。
- `POST receive/android`：上传 Android 采集数据。
- `POST device/report/android`：上报设备状态。
- `GET data/playback/sample/full/{sampleId}`：联调校验样本完整详情。

同步流程会根据本地工程库中的测点状态筛选可上传点，组装采集参数、波形、设备监控、工程绑定等信息。上传成功后更新本地同步状态；上传失败时记录失败原因，后续可按失败点重试。

## 运行环境

- Android Studio：建议使用支持 AGP 9.x 的较新版本。
- JDK：11 或更高版本。
- Android Gradle Plugin：`9.1.0`
- Kotlin：`2.3.20`
- compileSdk：`36.1`
- minSdk：`24`
- targetSdk：`36`

## 构建运行

1. 使用 Android Studio 打开项目根目录。
2. 等待 Gradle Sync 完成。
3. 连接 Android 真机或启动模拟器。
4. 运行 `app` 模块。

也可以使用命令行构建：

```bash
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

## 快速体验

如果没有真实采集设备，可以使用内置模拟设备模式体验完整流程：

1. 启动应用，进入工程管理页面。
2. 创建一个工程并进入采集页面。
3. 打开设备连接设置。
4. 启用“采集设备模拟模式”。
5. 点击连接，等待模拟设备连接成功。
6. 选择或创建测线/测点，填写采集参数。
7. 开始采集，观察 Recv、Send、Off 波形和设备监控信息。
8. 采集完成后执行质检判定，选择保存或重采。
9. 进入历史回放或数据上传页面查看后续流程。

## 权限说明

项目会根据功能使用以下权限：

- 网络权限：用于 HTTP 数据同步和 TCP 设备通信。
- 网络状态/Wi-Fi 权限：用于判断网络状态、连接设备热点。
- 定位权限：部分 Android 版本连接 Wi-Fi 热点或获取定位信息时需要。
- 存储权限：用于工程库导入、导出和外部文件共享。
- FileProvider：用于安全分享导出的工程数据库文件。

Android 10 及以上系统对外部存储和 Wi-Fi 连接有额外限制，调试时需要关注运行时权限、分区存储和 `NEARBY_WIFI_DEVICES` 等版本适配问题。

## 配置说明

常用配置由 `AppSettings` 通过 SharedPreferences 保存，包括：

- 设备 TCP IP 与端口。
- 是否启用模拟设备。
- 设备 Wi-Fi 热点 SSID、密码、隐藏 SSID 配置。
- 同步服务 Base URL。
- 登录 Token、同步账号、设备 ID。
- 工程与服务端项目/工程编码的绑定关系。
- 采集参数模板。
- 采集质检规则档位和是否拦截保存。

公开仓库中不建议提交真实账号、密码、Token、服务端地址、客户数据或设备协议敏感细节。若用于简历展示，建议使用 Mock 服务或模拟设备模式。

## 适合展示的工程亮点

- 使用 Room/SQLite 设计离线优先的数据存储，工程索引库与工程数据库分离。
- 基于 TCP Socket/Okio 实现二进制协议通信，处理半包、粘包、校验、异常帧和自动重连。
- 内置模拟设备模式，方便无硬件条件下演示采集、波形、监控和诊断流程。
- 使用 MPAndroidChart 展示三类波形，并支持实时采集和历史回放。
- 使用 Retrofit/OkHttp/Gson + WorkManager 实现认证、上传、失败记录和重试。
- 提供设备诊断与操作日志中心，增强现场联调和问题追踪能力。
- 将采集流程拆分为 Manager、Repository、UseCase、Coordinator、ViewModel 等角色，降低页面逻辑复杂度。

## 注意事项

- 当前项目包含真实设备通信和数据同步相关配置，请在公开发布前完成脱敏。
- `base.apk`、`outputs/`、本地说明文档、真实工程数据库等产物不建议提交到公开仓库。
- 若上传到 GitHub 作为简历项目，建议补充截图、演示 GIF、模拟设备说明和接口 Mock 说明。
- 如果后续用于正式交付，建议补齐数据库完整迁移、单元测试、接口环境切换、敏感配置加密和发布混淆规则。

## 许可证

当前仓库未声明开源许可证。若计划公开发布，请根据代码归属和项目用途补充合适的 LICENSE 文件。
