# 野外物探测点数据采集与同步系统

基于 Android 的野外物探测点数据采集与同步系统，作为主力外业 App，用于控制天线采集、收集采集数据，并与数据中心平台同步。

## 功能特性

### 1. 工程管理
- ✅ 支持新建工程，命名并保存为.sqlite 格式数据库文件
- ✅ 支持打开已有工程数据库文件（含历史测点数据）
- ✅ 每个工程包含多个测线，每条测线由若干测点组成
- ✅ 测点以编号唯一标识，每个测点对应一组采集数据

### 2. 设备连接
- ✅ TCP 连接设置界面，输入天线 IP 地址
- ✅ 建立与天线的 TCP Socket 连接
- ✅ 显示连接状态："未连接"、"正在连接"、"已连接"
- ✅ 支持断开连接与重连

### 3. 采集参数配置
- ✅ 手动或批量输入当前测点编号
- ✅ 设置采集参数：
  - 发送电流（A）
  - 采样频率（Hz）
  - 采集次数
  - 采样时间（μs）
  - 极距、发射线圈方向等
- ✅ 参数支持快速复制上一个点的设置

### 4. 实时采集与显示
- ✅ 通过 TCP 接收天线发送的原始数据流
- ✅ 实时绘制三类波形图（使用 MPAndroidChart）：
  - Recv 波形：接收电压随时间变化曲线
  - Send 波形：发射电流脉冲
  - Off 波形：关断阶段响应
- ✅ 图表坐标轴单位自动适配
- ✅ 同步显示设备监控信息（实时更新）：
  - 电池电压（V）
  - 电流（A）
  - 温度（C）
  - 信号强度（dBm）

### 5. 数据保存与判定
- ✅ 采集完成后进入"数据判定"环节
- ✅ 人工判定：用户点击"保存"按钮确认数据有效
- ✅ 点击"保存"后，将所有数据写入当前工程数据库中对应测点
- ✅ 若判定不合格，可选择"重新采集"或跳过该点

### 6. 多点连续采集
- ✅ 保存当前点后，自动进入下一个测点的配置界面
- ✅ 支持一键切换到下一个编号
- ✅ 所有测点数据均按顺序存储于同一工程数据库中

### 7. 数据同步（联网上传）
- ✅ 系统后台持续检测网络状态（WorkManager）
- ✅ 当检测到可用网络时，自动将未同步的测点数据打包上传
- ✅ 通过 HTTPS 协议上传至数据中心系统
- ✅ 上传成功后，在本地数据库中标记该测点为"已同步"
- ✅ 支持手动触发"立即同步"按钮
- ✅ 上传失败时，自动记录错误日志，并在恢复网络后重试

### 8. 数据导出
- ✅ 支持将当前工程数据库导出为标准文件格式（.sqlite）
- ✅ 导出内容包括全部测点数据、参数、监控信息

### 9. 数据回放功能
- ✅ 支持加载已完成或正在进行的工程数据库文件
- ✅ 采集的途中也支持回放
- ✅ 所有波形与监控信息均从本地数据库读取，不连接天线
- ✅ 用户可逐点查看、对比不同测点的波形特征
- ✅ 支持播放动画效果（时间轴滑动）模拟采集过程

## 技术架构

### 核心技术栈
- **语言**: Java
- **数据库**: Room (SQLite)
- **网络通信**: 
  - TCP Socket (设备通信)
  - Retrofit + HTTPS (数据同步)
- **图表绘制**: MPAndroidChart
- **后台任务**: WorkManager
- **架构模式**: Repository 模式

### 项目结构

```
app/src/main/java/cn/zjl/datacollector/
├── data/                      # 数据层
│   ├── entity/               # 数据库实体类
│   │   ├── ProjectEntity.java           # 工程实体
│   │   ├── SurveyLineEntity.java        # 测线实体
│   │   ├── MeasurementPointEntity.java  # 测点实体
│   │   ├── CollectionParameterEntity.java  # 采集参数实体
│   │   ├── WaveformDataEntity.java      # 波形数据实体
│   │   └── DeviceMonitorEntity.java     # 设备监控实体
│   ├── dao/                  # 数据访问对象
│   │   ├── ProjectDao.java
│   │   ├── SurveyLineDao.java
│   │   ├── MeasurementPointDao.java
│   │   ├── CollectionParameterDao.java
│   │   ├── WaveformDao.java
│   │   └── DeviceMonitorDao.java
│   ├── AppDatabase.java      # Room 数据库
│   └── repository/           # 数据仓库
│       ├── ProjectRepository.java
│       └── DataRepository.java
├── net/                      # 网络层
│   └── tcp/
│       └── TcpClientManager.java  # TCP 连接管理
├── collection/               # 采集业务层
│   └── CollectionManager.java     # 采集管理器
├── sync/                     # 同步模块
│   └── DataSyncWorker.java   # WorkManager 同步任务
├── ui/                       # UI 界面层
│   ├── ProjectListActivity.java      # 工程管理界面
│   ├── collection/
│   │   └── CollectionActivity.java   # 数据采集界面
│   └── playback/
│       └── PlaybackActivity.java     # 数据回放界面
└── util/                     # 工具类
    └── ExportUtils.java      # 数据导出工具
```

### 数据库设计

#### 主要表结构

1. **projects** - 工程表
   - id, name, databasePath, createdAt, updatedAt, description, isSynced

2. **survey_lines** - 测线表
   - id, projectId, name, description, createdAt, updatedAt

3. **measurement_points** - 测点表
   - id, surveyLineId, pointNumber, status, isQualified, 
     collectionTime, latitude, longitude, altitude, remark

4. **collection_parameters** - 采集参数表
   - id, pointId, transmitCurrent, sampleFrequency, collectionCount,
     sampleTime, electrodeDistance, transmitterDirection, 
     customParameters, createdAt

5. **waveform_data** - 波形数据表
   - id, pointId, waveformType, timePoints, values, 
     sampleCount, createdAt

6. **device_monitor** - 设备监控表
   - id, pointId, batteryVoltage, current, temperature, 
     signalStrength, timestamp

## 权限说明

应用需要以下权限：

```xml
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- 存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

<!-- 位置权限（用于 WiFi 连接） -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

## 编译与运行

### 环境要求
- Android Studio Hedgehog | 2023.1.1 或更高版本
- JDK 11+
- Android SDK 24+ (最低版本)
- Android SDK 36 (目标版本)

### 依赖库
- Room 2.6.1 - 数据库
- WorkManager 2.9.0 - 后台任务
- MPAndroidChart 3.1.0 - 图表绘制
- Retrofit 2.11.0 - HTTP 客户端
- Gson 2.11.0 - JSON 解析

### 编译步骤

1. 克隆项目到本地
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成
4. 连接 Android 设备或使用模拟器
5. 点击 Run 运行应用

## 使用说明

### 新建工程
1. 启动应用，进入工程管理界面
2. 点击右下角"+"按钮
3. 输入工程名称和描述
4. 点击"创建"按钮

### 打开工程
1. 在工程列表中点击要打开的工程
2. 进入数据采集界面

### 连接设备
1. 点击"连接"按钮
2. 输入设备 IP 地址和端口号（默认：192.168.1.100:8080）
3. 等待连接成功

### 开始采集
1. 输入测点编号
2. 设置采集参数
3. 点击"开始采集"按钮
4. 实时查看波形和设备状态
5. 采集完成后点击"保存"

### 数据同步
- 自动同步：连接到互联网时自动同步未同步的数据
- 手动同步：在工程列表界面下拉刷新触发同步

### 数据回放
1. 在工程列表中选择已完成的工程
2. 选择"回放"选项
3. 选择测点查看波形
4. 使用播放控件控制回放进度

### 数据导出
1. 在工程列表长按工程
2. 选择"导出"选项
3. 导出的文件保存在 /DataCollector/Export/ 目录

## 注意事项

1. **TCP 通信协议**
   - 魔数：0x12345678
   - 字节序：小端（Little Endian）
   - 协议包格式：魔数 (4) + 命令类型 (4) + 数据长度 (4) + 数据 (N) + 校验和 (4)

2. **数据存储**
   - 所有数据存储在应用的私有目录
   - 导出功能可将数据库复制到外部存储

3. **网络同步**
   - 需要配置实际的 HTTPS 接口地址
   - 建议使用 OkHttp 的拦截器进行日志记录

4. **性能优化**
   - 大数据量采集时使用异步处理
   - 波形数据分批加载避免内存溢出

## 后续开发建议

1. **AI 辅助判定**
   - 集成机器学习模型分析波形质量
   - 训练数据集标注和模型训练

2. **GPS 定位**
   - 集成高精度 GPS 模块
   - 自动记录测点坐标

3. **离线地图**
   - 集成离线地图显示测点位置
   - 支持轨迹记录

4. **多设备管理**
   - 支持同时连接多个天线设备
   - 设备分组和管理

5. **云端备份**
   - 实现完整的云端同步接口
   - 支持多设备数据同步

## 开发者

本项目为野外物探作业设计，如需定制开发或技术支持，请联系开发者。

## 许可证

MIT License
