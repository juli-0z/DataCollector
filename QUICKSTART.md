# 快速开始指南

## 第一次运行项目

### 步骤 1: 打开项目
```
1. 启动 Android Studio
2. File -> Open
3. 选择 E:\AndroidDev\DataCollector 目录
4. 点击 OK
```

### 步骤 2: 等待 Gradle 同步
```
- 打开项目后，Android Studio 会自动同步 Gradle
- 观察底部状态栏的进度条
- 首次同步可能需要几分钟（下载依赖）
- 确保网络连接正常
```

### 步骤 3: 同步完成后检查
```
Build -> Make Project (或按 Ctrl+F9)
```

如果看到编译错误（主要是 MPAndroidChart 相关），请等待 Gradle 完全同步后再试。

### 步骤 4: 运行应用
```
1. 连接 Android 设备或启动模拟器
2. 点击工具栏的绿色运行按钮（Shift+F10）
3. 选择设备
4. 等待安装和启动
```

## 应用功能测试流程

### 测试 1: 工程管理
```
1. 应用启动后进入工程列表界面
2. 点击右下角 "+" 按钮
3. 输入：
   - 工程名称：测试工程 001
   - 描述：这是我的第一个测试工程
4. 点击"创建"
5. 验证：列表中显示新建的工程
```

### 测试 2: 数据采集（需要 TCP 服务器模拟）

#### 方案 A: 使用实际的物探设备
```
1. 点击工程进入采集界面
2. 点击"连接"按钮
3. 输入设备的实际 IP 地址
4. 点击"连接"
5. 等待连接成功
6. 设置参数并开始采集
```

#### 方案 B: 使用 TCP 服务器模拟器（推荐用于开发测试）

可以使用以下工具模拟 TCP 服务器：

**Windows 推荐**: Hercules SETUP utility
```
下载地址：https://www.hw-group.com/software/hercules-setup-utility
配置步骤：
1. 启动 Hercules
2. 切换到 TCP Server 标签
3. 设置 Port: 8080
4. 点击 Listen
5. 使用 Send 标签发送测试数据
```

**跨平台推荐**: Packet Sender
```
下载地址：https://packetsender.com/
配置步骤：
1. 启动 Packet Sender
2. 点击 TCP Server
3. 设置 Local Port: 8080
4. 点击 Open
5. 发送十六进制数据测试
```

**Python 脚本模拟**（推荐开发者使用）:
```python
import socket
import struct
import time

def start_tcp_server():
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind(('0.0.0.0', 8080))
    server_socket.listen(1)
    print("TCP Server listening on port 8080...")
    
    conn, addr = server_socket.accept()
    print(f"Connected by {addr}")
    
    try:
        while True:
            # 接收数据
            data = conn.recv(1024)
            if data:
                print(f"Received: {data.hex()}")
                
                # 发送模拟的波形数据
                # 协议格式：魔数 (4) + 命令类型 (4) + 长度 (4) + 数据 (N) + 校验和 (4)
                magic = 0x12345678
                cmd_type = 0x10  # 波形数据
                waveform_type = 0  # Recv
                sample_count = 10
                
                # 构建数据包
                header = struct.pack('<III', magic, cmd_type, 12 + sample_count * 12)
                wave_header = struct.pack('<II', waveform_type, sample_count)
                
                # 模拟数据点
                samples = b''
                for i in range(sample_count):
                    time_val = float(i * 0.1)
                    amp_val = float(10.0 * (i % 5))
                    samples += struct.pack('<df', time_val, amp_val)
                
                # 简单校验和
                checksum = len(header) + len(wave_header) + len(samples)
                checksum_bytes = struct.pack('<I', checksum)
                
                # 发送完整数据包
                packet = header + wave_header + samples + checksum_bytes
                conn.sendall(packet)
                print("Sent waveform data")
                
            time.sleep(1)
            
    except Exception as e:
        print(f"Error: {e}")
    finally:
        conn.close()
        server_socket.close()

if __name__ == '__main__':
    start_tcp_server()
```

保存为 `tcp_server.py`，运行：
```bash
python tcp_server.py
```

### 测试 3: 数据回放
```
1. 完成至少一次采集并保存数据后
2. （需要在代码中添加回放入口，目前可以通过 Intent 启动 PlaybackActivity）
3. 选择测点查看波形
4. 拖动进度条
5. 点击播放按钮
```

## 常见问题排查

### 问题 1: "Cannot resolve symbol 'mikephil'"
**原因**: MPAndroidChart 依赖未下载

**解决方案**:
```
1. File -> Invalidate Caches / Restart
2. 点击 Invalidate and Restart
3. 等待重启后重新同步 Gradle
```

### 问题 2: "Room cannot find the generated classes"
**原因**: Room 注解处理器未运行

**解决方案**:
```
1. Build -> Clean Project
2. Build -> Rebuild Project
3. 等待编译完成
```

### 问题 3: 应用闪退
**可能原因**: 权限未授予

**解决方案**:
```
1. 设置 -> 应用管理 -> DataCollector -> 权限
2. 授予所有必要权限（存储、位置、网络）
3. 重新启动应用
```

### 问题 4: TCP 连接失败
**排查步骤**:
```
1. 确认服务器已启动并监听正确端口
2. 检查防火墙是否阻止连接
3. 确认 IP 地址正确（模拟器用 10.0.2.2 代替 localhost）
4. 使用 netstat 命令检查端口占用
   Windows: netstat -ano | findstr :8080
```

## 开发调试技巧

### 1. 查看日志
```
Android Studio -> Logcat 标签
过滤标签：
- TcpClientManager - TCP 连接日志
- CollectionManager - 采集业务日志
- DataSyncWorker - 同步日志
```

### 2. 数据库检查
```
使用 Android Studio 的 App Inspection 工具：
1. View -> Tool Windows -> App Inspection
2. 选择 Database Inspector
3. 查看 data_collector.db 的内容
```

### 3. 网络抓包（可选）
```
使用 Wireshark 或 tcpdump 抓取 TCP 数据包
分析协议格式是否正确
```

## 下一步开发建议

### 必做项
1. ✅ 完成上述测试流程
2. ⬜ 配置实际的 HTTPS 同步接口地址
3. ⬜ 添加运行时权限申请代码
4. ⬜ 完善数据回放界面的数据加载逻辑

### 可选项
1. ⬜ 添加 GPS 定位功能
2. ⬜ 实现 AI 辅助判定
3. ⬜ 集成离线地图
4. ⬜ 优化 UI/UX 设计

## 联系与支持

如遇到问题，请检查：
1. README.md - 完整功能说明
2. PROJECT_SUMMARY.md - 详细技术文档
3. 代码注释 - 每个类和方法都有说明

---

**祝开发顺利！** 🚀
