package cn.zjl.datacollector.net.tcp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * TCP 连接管理器
 * 负责与天线设备建立 TCP 连接并收发数据
 */
public class TcpClientManager {
    private static final String TAG = "TcpClientManager";
    
    // 连接状态
    public enum ConnectionState {
        DISCONNECTED,      // 未连接
        CONNECTING,        // 正在连接
        CONNECTED,         // 已连接
        RECONNECTING       // 重连中
    }
    
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    
    private String serverIp;
    private int serverPort;
    private int timeoutMs = 5000;  // 连接超时时间
    
    private volatile boolean isRunning = false;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    
    private Handler mainHandler;
    private ConnectionListener listener;
    private Thread receiveThread;
    
    // 重连参数
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int RECONNECT_DELAY_MS = 2000;
    
    /**
     * 连接状态监听器
     */
    public interface ConnectionListener {
        void onConnectionStateChanged(ConnectionState state);
        void onDataReceived(byte[] data);
        void onError(String error);
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
        if (connectionState == ConnectionState.CONNECTED || 
            connectionState == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connected or connecting");
            return;
        }
        
        if (serverIp == null || serverPort <= 0) {
            notifyError("IP 地址或端口号无效");
            return;
        }
        
        updateConnectionState(ConnectionState.CONNECTING);
        
        new Thread(() -> {
            try {
                socket = new Socket();
                socket.setSoTimeout(timeoutMs);
                
                InetSocketAddress address = new InetSocketAddress(serverIp, serverPort);
                socket.connect(address, timeoutMs);
                
                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = new DataOutputStream(socket.getOutputStream());
                
                isRunning = true;
                reconnectAttempts = 0;
                
                updateConnectionState(ConnectionState.CONNECTED);
                Log.i(TAG, "Connected to " + serverIp + ":" + serverPort);
                
                // 启动接收线程
                startReceiveThread();
                
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                isRunning = false;
                closeStreams();
                
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
        reconnectAttempts++;
        updateConnectionState(ConnectionState.RECONNECTING);
        
        mainHandler.postDelayed(() -> {
            if (isRunning && connectionState != ConnectionState.CONNECTED) {
                connect();
            }
        }, RECONNECT_DELAY_MS);
    }
    
    /**
     * 启动数据接收线程
     */
    private void startReceiveThread() {
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead > 0) {
                        byte[] receivedData = Arrays.copyOf(buffer, bytesRead);
                        notifyDataReceived(receivedData);
                    } else if (bytesRead == -1) {
                        // 流结束，连接断开
                        Log.i(TAG, "Stream ended, connection lost");
                        handleDisconnection();
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    // 超时，继续等待
                } catch (IOException e) {
                    Log.e(TAG, "Receive error", e);
                    if (isRunning) {
                        handleDisconnection();
                    }
                    break;
                }
            }
        });
        receiveThread.start();
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
        if (connectionState != ConnectionState.CONNECTED || outputStream == null) {
            notifyError("未连接到服务器");
            return;
        }
        
        new Thread(() -> {
            try {
                outputStream.write(data);
                outputStream.flush();
                Log.d(TAG, "Sent " + data.length + " bytes");
            } catch (IOException e) {
                Log.e(TAG, "Send error", e);
                notifyError("发送失败：" + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 发送命令（带协议头）
     */
    public void sendCommand(int commandType, byte[] payload) {
        // 构建协议包：魔数 (4) + 命令类型 (4) + 数据长度 (4) + 数据 (N) + 校验和 (4)
        ByteBuffer buffer = ByteBuffer.allocate(16 + (payload != null ? payload.length : 0));
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 魔数
        buffer.putInt(0x12345678);
        // 命令类型
        buffer.putInt(commandType);
        // 数据长度
        buffer.putInt(payload != null ? payload.length : 0);
        // 数据
        if (payload != null) {
            buffer.put(payload);
        }
        // 校验和（简单示例，实际应使用 CRC 等）
        buffer.putInt(calculateChecksum(buffer.array()));
        
        sendData(buffer.array());
    }
    
    /**
     * 计算校验和
     */
    private int calculateChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum;
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        isRunning = false;
        
        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
        
        closeStreams();
        updateConnectionState(ConnectionState.DISCONNECTED);
        Log.i(TAG, "Disconnected");
    }
    
    /**
     * 关闭流
     */
    private void closeStreams() {
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing input stream", e);
        }
        
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing output stream", e);
        }
        
        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
    }
    
    /**
     * 更新连接状态
     */
    private void updateConnectionState(ConnectionState state) {
        connectionState = state;
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onConnectionStateChanged(state);
            }
        });
    }
    
    /**
     * 通知数据到达
     */
    private void notifyDataReceived(byte[] data) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDataReceived(data);
            }
        });
    }
    
    /**
     * 通知错误
     */
    private void notifyError(String error) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(error);
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
