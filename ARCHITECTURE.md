# 系统架构设计文档

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     UI Layer (界面层)                        │
├─────────────────────────────────────────────────────────────┤
│  ProjectListActivity  │  CollectionActivity │ PlaybackActivity│
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                 Business Layer (业务层)                      │
├─────────────────────────────────────────────────────────────┤
│         CollectionManager        │      TcpClientManager    │
│   - 采集流程控制                  │    - TCP 连接管理          │
│   - 参数配置                     │    - 数据收发            │
│   - 数据解析                     │    - 协议解析            │
│   - 状态监控                     │    - 自动重连            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                Repository Layer (仓库层)                     │
├─────────────────────────────────────────────────────────────┤
│     ProjectRepository           │      DataRepository       │
│   - 工程 CRUD                    │    - 测点管理             │
│   - 文件管理                     │    - 参数管理             │
│   - 同步状态                     │    - 波形数据             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                  Data Layer (数据层)                         │
├─────────────────────────────────────────────────────────────┤
│  Room Database (SQLite)         │    Shared Preferences     │
│  - Entities (6 个)               │    - 用户设置             │
│  - DAOs (6 个)                   │    - 设备配置             │
└─────────────────────────────────────────────────────────────┘
```

## 2. 模块划分

### 2.1 数据层 (data/)

```
data/
├── entity/              # 实体类（与数据库表对应）
│   ├── ProjectEntity           # 工程表
│   ├── SurveyLineEntity        # 测线表
│   ├── MeasurementPointEntity  # 测点表
│   ├── CollectionParameterEntity # 参数表
│   ├── WaveformDataEntity      # 波形数据表
│   └── DeviceMonitorEntity     # 设备监控表
├── dao/                 # 数据访问对象
│   ├── ProjectDao
│   ├── SurveyLineDao
│   ├── MeasurementPointDao
│   ├── CollectionParameterDao
│   ├── WaveformDao
│   └── DeviceMonitorDao
├── AppDatabase.java     # Room 数据库主类
└── repository/          # 数据仓库
    ├── ProjectRepository       # 工程仓库
    └── DataRepository          # 综合数据仓库
```

**职责**:
- 数据持久化
- 数据库操作封装
- 提供统一的数据访问接口

### 2.2 网络层 (net/)

```
net/
└── tcp/
    └── TcpClientManager.java   # TCP 客户端管理器
```

**核心功能**:
- TCP Socket 连接管理
- 二进制数据收发
- 协议包解析
- 连接状态监听
- 自动重连机制

**协议格式**:
```
┌─────────┬───────────┬──────────┬──────────┬─────────┐
│ 魔数(4) │ 命令类型 (4) │ 数据长度 (4) │ 数据 (N)  │ 校验和 (4)│
└─────────┴───────────┴──────────┴──────────┴─────────┘
字节序：Little Endian
魔数：0x12345678
```

### 2.3 采集业务层 (collection/)

```
collection/
└── CollectionManager.java   # 采集管理器
```

**核心功能**:
- 采集流程控制（开始/停止/暂停）
- 参数配置与下发
- 波形数据解析
- 设备状态监控
- 实时数据回调

**工作流程**:
```
1. 设置参数 → setParameters()
2. 开始采集 → startCollection()
3. 接收数据 → processReceivedData()
   ├─ 解析波形 → parseWaveformData()
   └─ 解析状态 → parseDeviceStatus()
4. 回调通知 → DataCallback
   ├─ onWaveformData()
   ├─ onMonitorInfo()
   └─ onCollectionComplete()
```

### 2.4 同步模块 (sync/)

```
sync/
└── DataSyncWorker.java   # WorkManager 同步任务
```

**功能**:
- 后台数据同步
- 网络状态检测
- 断点续传
- 失败重试

**同步策略**:
```
1. 检测网络可用
2. 获取未同步数据
3. 逐个上传测点数据
4. 标记已同步状态
5. 记录同步结果
```

### 2.5 UI 层 (ui/)

```
ui/
├── ProjectListActivity.java      # 工程管理界面
├── collection/
│   └── CollectionActivity.java   # 数据采集界面
└── playback/
    └── PlaybackActivity.java     # 数据回放界面
```

### 2.6 工具类 (util/)

```
util/
└── ExportUtils.java   # 数据导出工具
```

## 3. 数据流图

### 3.1 数据采集流程

```
用户操作 → CollectionActivity
              ↓
        CollectionManager
              ↓
        TcpClientManager → TCP 设备
              ↓
        接收数据流
              ↓
        解析数据包
              ↓
        回调通知
              ↓
    ┌───────┴───────┐
    ↓               ↓
更新 UI 图表    保存到数据库
    │               │
    ↓               ↓
MPAndroidChart  Room Database
```

### 3.2 数据同步流程

```
WorkManager 定时检测
        ↓
网络状态检查
        ↓
有未同步数据？
    ├─ 否 → 等待下次检测
    └─ 是 → 启动 DataSyncWorker
            ↓
      获取未同步测点
            ↓
      逐个上传
            ↓
      成功？
        ├─ 是 → 标记已同步
        └─ 否 → 记录错误，稍后重试
```

### 3.3 工程管理流程

```
ProjectListActivity
        ↓
ProjectRepository
        ↓
ProjectDao
        ↓
Room Database (projects 表)
        ↓
文件系统 (.db 文件)
```

## 4. 关键设计模式

### 4.1 Repository 模式

```java
// UI 层不直接访问 DAO，通过 Repository 抽象
ProjectListActivity 
    → ProjectRepository 
        → ProjectDao 
            → Room Database
```

**优点**:
- 解耦 UI 和数据层
- 统一数据访问入口
- 便于切换数据源（本地/网络）

### 4.2 单例模式

```java
// 数据库单例
AppDatabase.getInstance(context)

// TCP 管理器单例（每个连接一个实例）
TcpClientManager instance
```

**优点**:
- 全局唯一实例
- 节省资源
- 线程安全

### 4.3 观察者模式

```java
// TCP 连接状态监听
tcpClient.setConnectionListener(listener)

// 采集数据回调
collectionManager.addDataCallback(callback)
```

**优点**:
- 异步通知机制
- 解耦发送者和接收者
- 支持多个观察者

### 4.4 工厂模式

```java
// Room 数据库构建
Room.databaseBuilder()
    .build()
```

## 5. 并发模型

### 5.1 线程划分

```
主线程 (UI Thread)
├─ UI 渲染
├─ 用户交互
└─ 回调通知（通过 Handler）

后台线程 1 (ExecutorService)
├─ 数据库操作
├─ 文件读写
└─ Repository 方法

后台线程 2 (TCP Thread)
├─ Socket 连接
├─ 数据接收
└─ 协议解析

后台线程 3 (WorkManager)
├─ 数据同步
└─ 网络请求
```

### 5.2 线程通信

```java
// Handler + Looper
Handler mainHandler = new Handler(Looper.getMainLooper());
mainHandler.post(() -> {
    // 在主线程执行
    updateUI();
});

// ExecutorService
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.execute(() -> {
    // 在后台线程执行
    saveToDatabase();
});
```

## 6. 数据库设计

### 6.1 ER 图

```
Project (1) ──── (N) SurveyLine
                        │
                        │ (1)
                        │
                        │ (N)
                        ↓
                MeasurementPoint (1) ──── (1) CollectionParameter
                        │
                        │ (1)
                        │
                        ├────── (N) WaveformData
                        │
                        └────── (N) DeviceMonitor
```

### 6.2 表结构详情

#### projects (工程表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | LONG | 主键 |
| name | TEXT | 工程名称 |
| databasePath | TEXT | 数据库文件路径 |
| createdAt | LONG | 创建时间 |
| updatedAt | LONG | 最后更新时间 |
| description | TEXT | 描述 |
| isSynced | BOOLEAN | 是否已同步 |

#### measurement_points (测点表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | LONG | 主键 |
| surveyLineId | LONG | 外键（测线 ID） |
| pointNumber | INT | 测点编号 |
| status | INT | 采集状态 |
| isQualified | BOOLEAN | 是否合格 |
| collectionTime | LONG | 采集时间 |
| latitude | DOUBLE | GPS 纬度 |
| longitude | DOUBLE | GPS 经度 |
| altitude | DOUBLE | 海拔高度 |
| remark | TEXT | 备注 |

## 7. 性能优化

### 7.1 数据库优化

- 使用 Room 的编译时 SQL 验证
- 批量插入使用 `insertAll()`
- 异步操作使用 ExecutorService
- 索引优化（pointNumber, surveyLineId）

### 7.2 内存优化

- 波形数据分批加载
- 使用软引用缓存大数据
- 及时释放不用的资源
- 避免内存泄漏（Handler 静态化）

### 7.3 网络优化

- TCP 长连接减少握手
- 二进制协议减少数据量
- 自动重连提高可靠性
- WorkManager 智能调度网络请求

## 8. 安全设计

### 8.1 数据安全

- SQLite 数据库文件存储在应用私有目录
- 导出功能需要存储权限
- HTTPS 传输加密（待实现）

### 8.2 权限管理

- 最小权限原则
- 动态申请敏感权限
- 权限拒绝时的降级处理

## 9. 可扩展性

### 9.1 插件化设计

```
CollectionManager
    ↓
IDataProcessor (接口)
    ├─ WaveformProcessor
    ├─ StatusProcessor
    └─ CustomProcessor (可扩展)
```

### 9.2 多设备支持

```
TcpClientManager (可实例化多次)
    ├─ Device1 Connection
    ├─ Device2 Connection
    └─ Device3 Connection
```

### 9.3 多协议支持

```
NetLayer
    ├─ TcpProtocol
    ├─ UdpProtocol
    └─ HttpProtocol (可扩展)
```

---

**文档版本**: v1.0  
**最后更新**: 2026-03-07  
**维护者**: 开发团队
