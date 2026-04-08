package cn.zjl.datacollector.net.tcp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

/**
 * TCP 连接管理器
 * 负责与天线设备建立 TCP 连接并收发数据
 */
public class TcpClientManager {
    private static final String TAG = "TcpClientManager";       // 日志标签
    
    // 连接状态
    public enum ConnectionState {
        DISCONNECTED,      // 未连接
        CONNECTING,        // 正在连接
        CONNECTED,         // 已连接
        RECONNECTING       // 重连中
    }

    // Socket 相关
    private Socket socket;                          // TCP Socket 对象
    private BufferedSource bufferedSource;          // Okio 输入流（接收数据）
    private BufferedSink bufferedSink;              // Okio 输出流（发送数据）
    
    private String serverIp;                        // 服务器 IP 地址
    private int serverPort;                         // 服务器端口号
    private static final int TIMEOUT_MS = 5000;     // 连接超时时间（5 秒）

    private volatile boolean isRunning = false;     // 运行标志位
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;     // 当前连接状态（volatile 保证线程可见性）

    private final Handler mainHandler;                    // 主线程 Handler，用于切换回 UI 线程
    private ConnectionListener listener;            // 连接状态监听器
    private Thread receiveThread;                   // 独立的数据接收线程
    
    // 重连任务管理
    private Runnable reconnectRunnable;             // 待处理的重连任务
    private boolean reconnectScheduled = false;     // 是否已调度重连任务

    // 重连参数
    private int reconnectAttempts = 0;              // 已尝试重连次数
    private static final int MAX_RECONNECT_ATTEMPTS = 3;    // 最大重连次数
    private static final int RECONNECT_DELAY_MS = 2000;     // 重连延迟（2 秒）
    
    /**
     * 连接状态监听器
     */
    public interface ConnectionListener {
        void onConnectionStateChanged(ConnectionState state);       // 连接状态变化
        void onDataReceived(byte[] data);                           // 收到设备数据
        void onError(String error);                                 // 发生错误
    }
    
    public TcpClientManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 设置连接参数
     */
    public void setConnectionParams(String ip, int port) {
        this.serverIp = ip;
        this.serverPort = port;
    }
    
    /**
     * 设置连接监听器
     */
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }
    
    /**
     * 连接到服务器
     */
    public void connect() {
        // 1. 检查是否已在连接或连接中
        if (connectionState == ConnectionState.CONNECTED || 
            connectionState == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connected or connecting");
            return;
        }

        // 2. 验证参数有效性
        if (serverIp == null || serverPort <= 0) {
            notifyError("IP 地址或端口号无效");
            return;
        }

        // 3. 更新状态为"正在连接"
        updateConnectionState(ConnectionState.CONNECTING);

        // 4. 启动新线程执行连接（避免阻塞主线程）
        new Thread(() -> {
            try {
                socket = new Socket();
                socket.setSoTimeout(TIMEOUT_MS);         // 设置超时
                
                InetSocketAddress address = new InetSocketAddress(serverIp, serverPort);
                socket.connect(address, TIMEOUT_MS);     // 发起连接

                // 5. 创建 Okio 输入输出流
                Source source = Okio.source(socket);
                bufferedSource = Okio.buffer(source);
                
                Sink sink = Okio.sink(socket);
                bufferedSink = Okio.buffer(sink);
                
                isRunning = true;
                reconnectAttempts = 0;      // 重置重连计数

                // 6. 更新状态为"已连接"
                updateConnectionState(ConnectionState.CONNECTED);
                Log.i(TAG, "Connected to " + serverIp + ":" + serverPort);
                
                // 7. 启动接收线程
                startReceiveThread();
                
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                isRunning = false;
                closeStreams();

                // 8. 失败时尝试重连
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    attemptReconnect();
                } else {
                    updateConnectionState(ConnectionState.DISCONNECTED);
                    notifyError("连接失败：" + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 尝试重连
     */
    private void attemptReconnect() {
        reconnectAttempts++;        // 增加重连计数
        updateConnectionState(ConnectionState.RECONNECTING);
        
        Log.d(TAG, "Scheduling reconnect attempt #" + reconnectAttempts);

        // 创建可追踪的重连任务
        reconnectRunnable = () -> {
            reconnectScheduled = false;
            
            if (isRunning && connectionState != ConnectionState.CONNECTED) {
                Log.d(TAG, "Executing reconnection");
                connect();      // 重新发起连接
            } else {
                Log.d(TAG, "Reconnect cancelled (running=" + isRunning + 
                      ", state=" + connectionState + ")");
            }
        };
        
        // 延迟 2 秒后执行并标记为已调度
        reconnectScheduled = true;
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
    }
    
    /**
     * 启动数据接收线程
     */
    private void startReceiveThread() {
        receiveThread = new Thread(this::receiveDataLoop);
        receiveThread.start();
    }
    
    /**
     * 数据接收循环 - 按协议包解析
     */
    private void receiveDataLoop() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                // 协议包最小长度：魔数 (4) + 命令类型 (4) + 数据长度 (4) = 12 字节
                if (!bufferedSource.request(12)) {
                    // 无法读取足够的字节，可能是连接断开
                    continue;
                }
                
                // 读取协议头
                int magicNumber = bufferedSource.readIntLe();
                if (magicNumber != 0x12345678) {
                    Log.w(TAG, "Invalid magic number: 0x" + Integer.toHexString(magicNumber));
                    // 跳过错误的字节，尝试重新同步
                    continue;
                }
                
                int commandType = bufferedSource.readIntLe();
                int dataLength = bufferedSource.readIntLe();
                
                // 验证数据长度合理性（防止恶意数据）
                if (dataLength < 0 || dataLength > 1024 * 1024) { // 最大 1MB
                    Log.e(TAG, "Invalid data length: " + dataLength);
                    continue;
                }
                
                // 读取数据内容
                byte[] data = new byte[dataLength];
                if (dataLength > 0) {
                    int bytesRead = bufferedSource.read(data);
                    if (bytesRead != dataLength) {
                        Log.e(TAG, "Incomplete data read: expected " + dataLength + ", got " + bytesRead);
                        continue;
                    }
                }
                
                // 读取校验和（4 字节）
                if (!bufferedSource.request(4)) {
                    Log.e(TAG, "Failed to read checksum");
                    continue;
                }
                long receivedChecksum = bufferedSource.readIntLe() & 0xFFFFFFFFL;
                
                // 验证校验和
                byte[] packetForChecksum = new byte[12 + dataLength];
                System.arraycopy(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(magicNumber).putInt(commandType).putInt(dataLength).array(), 
                    0, packetForChecksum, 0, 12);
                if (dataLength > 0) {
                    System.arraycopy(data, 0, packetForChecksum, 12, dataLength);
                }
                long calculatedChecksum = calculateCRC32(packetForChecksum);
                
                if (receivedChecksum != calculatedChecksum) {
                    Log.w(TAG, "Checksum mismatch: received=0x" + Long.toHexString(receivedChecksum) 
                        + ", calculated=0x" + Long.toHexString(calculatedChecksum));
                    continue;
                }
                
                // 校验通过，通知数据到达
                notifyDataReceived(data);
                
            } catch (SocketTimeoutException e) {
                // 超时，继续等待
            } catch (IOException e) {
                Log.e(TAG, "Receive error", e);
                if (isRunning) {
                    handleDisconnection();
                }
                break;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in receive loop", e);
            }
        }
    }
    
    /**
     * 处理断开连接
     */
    private void handleDisconnection() {
        isRunning = false;
        closeStreams();
        
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            attemptReconnect();
        } else {
            updateConnectionState(ConnectionState.DISCONNECTED);
            notifyError("连接断开");
        }
    }
    
    /**
     * 发送数据
     */
    public void sendData(byte[] data) {
        if (connectionState != ConnectionState.CONNECTED || bufferedSink == null) {
            notifyError("未连接到服务器");
            return;
        }
        
        new Thread(() -> {
            try {
                // Okio 方式：直接写入并刷新
                bufferedSink.write(data);
                bufferedSink.flush();
                Log.d(TAG, "Sent " + data.length + " bytes");
            } catch (IOException e) {
                Log.e(TAG, "Send error", e);
                notifyError("发送失败：" + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 发送命令（带协议头）
     * 协议格式：魔数 (4) + 命令类型 (4) + 数据长度 (4) + 数据 (N) + 校验和 (4)
     */
    public void sendCommand(int commandType, byte[] payload) {
        if (connectionState != ConnectionState.CONNECTED || bufferedSink == null) {
            notifyError("未连接到服务器，无法发送命令");
            return;
        }
        
        // 在后台线程发送，避免阻塞主线程
        new Thread(() -> {
            try {
                // 计算数据长度
                int dataLength = payload != null ? payload.length : 0;
                
                // 构建不含校验和的包头部分（用于计算校验和）
                byte[] headerWithoutChecksum = new byte[12 + dataLength];
                ByteBuffer buffer = ByteBuffer.wrap(headerWithoutChecksum).order(ByteOrder.LITTLE_ENDIAN);
                buffer.putInt(0x12345678);      // 魔数
                buffer.putInt(commandType);      // 命令类型
                buffer.putInt(dataLength);       // 数据长度
                if (payload != null && payload.length > 0) {
                    buffer.put(payload);         // 数据内容
                }
                
                // 计算 CRC32 校验和
                long checksum = calculateCRC32(headerWithoutChecksum);
                
                // 构建完整的协议包（包含校验和）
                byte[] completePacket = new byte[headerWithoutChecksum.length + 4];
                System.arraycopy(headerWithoutChecksum, 0, completePacket, 0, headerWithoutChecksum.length);
                ByteBuffer.wrap(completePacket, headerWithoutChecksum.length, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt((int) (checksum & 0xFFFFFFFFL));
                
                // 发送完整数据包
                bufferedSink.write(completePacket);
                bufferedSink.flush();
                
                Log.d(TAG, "Command sent: type=0x" + Integer.toHexString(commandType) 
                    + ", length=" + dataLength + ", checksum=0x" + Long.toHexString(checksum));
                    
            } catch (IOException e) {
                Log.e(TAG, "Failed to send command", e);
                notifyError("发送命令失败：" + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 计算 CRC32 校验和
     * @param data 要校验的数据
     * @return CRC32 校验值（32 位无符号整数）
     */
    private long calculateCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        return crc32.getValue();
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        Log.i(TAG, "Disconnecting...");
        isRunning = false;                  // 停止接收循环
        
        // 取消待处理的重连任务
        if (reconnectScheduled && reconnectRunnable != null) {
            mainHandler.removeCallbacks(reconnectRunnable);
            reconnectScheduled = false;
            reconnectRunnable = null;
            Log.d(TAG, "Cancelled pending reconnect task");
        }

        if (receiveThread != null) {
            receiveThread.interrupt();      // 中断接收线程
            try {
                receiveThread.join(1000);   // 等待最多 1 秒
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for receive thread to stop", e);
                // 重新中断当前线程，保留中断状态
                Thread.currentThread().interrupt();
            }
            receiveThread = null;
        }

        closeStreams();                     // 关闭所有流
        updateConnectionState(ConnectionState.DISCONNECTED);
        Log.i(TAG, "Disconnected successfully");
    }
    
    /**
     * 关闭流
     */
    private void closeStreams() {
        // 关闭 Okio Source
        if (bufferedSource != null) {
            try {
                bufferedSource.close();
                bufferedSource = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing buffered source", e);
            }
        }
        
        // 关闭 Okio Sink
        if (bufferedSink != null) {
            try {
                bufferedSink.close();
                bufferedSink = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing buffered sink", e);
            }
        }
        
        // 关闭 Socket
        if (socket != null) {
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
        }
    }
    
    /**
     * 更新连接状态
     */
    private void updateConnectionState(ConnectionState state) {
        connectionState = state;
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onConnectionStateChanged(state);   // 在主线程回调
            }
        });
    }
    
    /**
     * 通知数据到达
     */
    private void notifyDataReceived(byte[] data) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDataReceived(data);              // 在主线程回调
            }
        });
    }
    
    /**
     * 通知错误
     */
    private void notifyError(String error) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(error);                    // 在主线程回调
            }
        });
    }
    
    /**
     * 获取当前连接状态
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }
    
    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }
}
