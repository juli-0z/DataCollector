package cn.zjl.datacollector.collection;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.net.tcp.TcpClientManager;

/**
 * 数据采集管理器 - 核心业务逻辑类
 * 
 * 主要职责：
 * 1. 控制采集流程（开始、停止、暂停）
 * 2. 解析设备返回的波形数据和状态信息
 * 3. 管理数据回调，将实时数据分发给 UI 层
 * 4. 维护采集状态和设备监控信息
 * 
 * 工作流程：
 * 设置参数 → 开始采集 → 接收数据 → 解析数据 → 回调通知 → 停止采集
 */
public class CollectionManager {
    private static final String TAG = "CollectionManager";  // 日志标签
    
    // ==================== 采集状态定义 ====================
    /**
     * 采集状态枚举
     * IDLE: 空闲状态，未进行任何操作
     * CONFIGURING: 配置中，正在发送参数到设备
     * COLLECTING: 采集中，正在接收实时数据
     * PAUSED: 暂停状态（预留）
     * COMPLETED: 采集完成
     */
    public enum CollectionStatus {
        IDLE,           // 空闲
        CONFIGURING,    // 配置中
        COLLECTING,     // 采集中
        PAUSED,         // 暂停
        COMPLETED       // 完成
    }
    
    // ==================== TCP 命令类型定义 ====================
    // 与设备进行通信的命令字，对应协议中的命令类型字段
    private static final int CMD_START_COLLECTION = 0x01;   // 开始采集命令
    private static final int CMD_STOP_COLLECTION = 0x02;    // 停止采集命令
    private static final int CMD_SET_PARAMETERS = 0x03;     // 设置参数命令
    private static final int CMD_GET_STATUS = 0x04;         // 获取状态命令
    
    // ==================== 成员变量 ====================
    private TcpClientManager tcpClient;                           // TCP 客户端管理器，负责网络通信
    private volatile CollectionStatus status = CollectionStatus.IDLE;  // 当前采集状态（volatile 保证线程可见性）
    
    private CollectionParameterEntity currentParameters;          // 当前使用的采集参数
    private List<DataCallback> dataCallbacks = new ArrayList<>(); // 数据回调列表，用于通知 UI 更新
    
    private final Handler mainHandler;                            // 主线程 Handler，用于将回调切换到 UI 线程
    
    // 实时波形数据缓冲区（固定大小，循环使用）
    private float[] recvWaveform;    // Recv 波形（接收波形）
    private float[] sendWaveform;    // Send 波形（发送波形）
    private float[] offWaveform;     // Off 波形（关闭波形）
    private double[] timePoints;     // 时间点数组（横坐标）
    
    // 设备监控信息
    private DeviceMonitorEntity currentMonitorInfo;               // 当前设备状态信息（电压、电流、温度等）
    
    /**
     * 数据回调接口 - 用于向 UI 层推送数据
     * 
     * 回调方法说明：
     * - onWaveformData: 波形数据到达时回调（包含时间点和三个通道的幅值）
     * - onMonitorInfo: 设备状态信息到达时回调（电压、电流、温度等）
     * - onCollectionComplete: 采集完成时回调
     * - onError: 发生错误时回调
     */
    public interface DataCallback {
        void onWaveformData(float[] timePoints, float[] recvValues, float[] sendValues, float[] offValues);
        void onMonitorInfo(DeviceMonitorEntity monitor);
        void onCollectionComplete();
        void onError(String error);
    }
    
    /**
     * 构造函数
     * 
     * @param tcpClient TCP 客户端管理器实例
     */
    public CollectionManager(TcpClientManager tcpClient) {
        this.tcpClient = tcpClient;
        mainHandler = new Handler(Looper.getMainLooper());  // 创建与主线程关联的 Handler
        
        // 初始化波形数据缓冲区（默认大小为 10000 个采样点）
        // 注意：如果设备返回的采样点数超过此值，会导致数据截断
        int bufferSize = 10000;
        recvWaveform = new float[bufferSize];
        sendWaveform = new float[bufferSize];
        offWaveform = new float[bufferSize];
        timePoints = new double[bufferSize];
    }
    
    /**
     * 添加数据回调监听器
     * 
     * @param callback 回调接口实例
     * 注意：使用 contains 检查避免重复添加
     */
    public void addDataCallback(DataCallback callback) {
        if (!dataCallbacks.contains(callback)) {
            dataCallbacks.add(callback);
        }
    }
    
    /**
     * 移除数据回调监听器
     * 
     * @param callback 要移除的回调接口实例
     */
    public void removeDataCallback(DataCallback callback) {
        dataCallbacks.remove(callback);
    }
    
    /**
     * 设置采集参数
     * 
     * 工作流程：
     * 1. 检查当前状态，如果在采集中则拒绝修改
     * 2. 保存参数到本地
     * 3. 通过 TCP 发送参数到设备
     * 
     * @param parameters 采集参数实体（包含发射电流、采样频率、采集次数等）
     */
    public void setParameters(CollectionParameterEntity parameters) {
        // 状态检查：采集中不允许修改参数
        if (status == CollectionStatus.COLLECTING) {
            notifyError("正在采集中，无法修改参数");
            return;
        }
        
        this.currentParameters = parameters;
        
        // 发送参数到设备
        sendParametersToDevice(parameters);
    }
    
    /**
     * 发送参数到设备 - 私有方法
     * 
     * 参数数据包格式（32 字节）：
     * - 发送电流 (4 字节 float)
     * - 采样频率 (4 字节 int)
     * - 采集次数 (4 字节 int)
     * - 采样时间 (4 字节 float)
     * - 极距 (4 字节 float)
     * - 保留字节 (12 字节)
     * 
     * @param params 采集参数实体
     */
    private void sendParametersToDevice(CollectionParameterEntity params) {
        // 连接状态检查
        if (!tcpClient.isConnected()) {
            notifyError("TCP 未连接");
            return;
        }
        
        // 使用 ByteBuffer 构建参数数据包（小端序）
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 按顺序写入参数（注意：字段顺序必须与设备端解析顺序一致）
        buffer.putFloat(params.transmitCurrent);      // 发送电流（单位：mA）
        buffer.putInt(params.sampleFrequency);        // 采样频率（单位：Hz）
        buffer.putInt(params.collectionCount);        // 采集次数（叠加次数）
        buffer.putFloat(params.sampleTime);           // 采样时间（单位：ms）
        buffer.putFloat(params.electrodeDistance);    // 极距（单位：m）
        
        // 其他参数可以扩展（目前未使用，填充 0）
        
        byte[] payload = buffer.array();
        tcpClient.sendCommand(CMD_SET_PARAMETERS, payload);  // 发送设置参数命令
        
        Log.i(TAG, "Parameters sent to device");
    }
    
    /**
     * 开始采集
     * 
     * 前置条件：
     * 1. TCP 已连接
     * 2. 已设置采集参数
     * 
     * 流程：
     * 1. 检查连接状态
     * 2. 检查参数是否已设置
     * 3. 更新状态为 COLLECTING
     * 4. 发送开始采集命令（包含采集次数）
     */
    public void startCollection() {
        // 连接状态检查
        if (!tcpClient.isConnected()) {
            notifyError("TCP 未连接，无法开始采集");
            return;
        }
        
        // 参数检查
        if (currentParameters == null) {
            notifyError("请先设置采集参数");
            return;
        }
        
        status = CollectionStatus.COLLECTING;  // 更新状态
        
        // 使用 ByteBuffer 发送开始采集命令（只包含采集次数）
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(currentParameters.collectionCount);
        
        tcpClient.sendCommand(CMD_START_COLLECTION, buffer.array());
        
        Log.i(TAG, "Collection started");
    }
    
    /**
     * 停止采集
     * 
     * 流程：
     * 1. 检查当前状态（只有在采集中才执行停止）
     * 2. 发送停止命令到设备
     * 3. 更新状态为 COMPLETED
     * 4. 通知回调采集完成
     */
    public void stopCollection() {
        // 状态检查：只有在采集中才执行停止
        if (status != CollectionStatus.COLLECTING) {
            return;
        }
        
        tcpClient.sendCommand(CMD_STOP_COLLECTION, null);  // 发送停止命令（无负载数据）
        status = CollectionStatus.COMPLETED;  // 更新状态
        
        notifyCollectionComplete();  // 通知采集完成
        Log.i(TAG, "Collection stopped");
    }
    
    /**
     * 处理接收到的数据 - 核心数据解析入口
     * 
     * 协议格式（TCP 数据包）：
     * - 魔数 (4 字节)：固定为 0x12345678，用于标识有效数据包
     * - 命令类型 (4 字节)：区分数据类型（波形数据/设备状态）
     * - 数据长度 (4 字节)：后续数据的字节数
     * - 数据内容 (N 字节)：实际的波形或状态数据
     * 
     * @param data TCP 接收到的原始字节数据
     */
    public void processReceivedData(byte[] data) {
        // 数据有效性检查（至少包含魔数 + 命令类型 + 数据长度 = 12 字节）
        if (data == null || data.length < 12) {
            return;
        }
        
        // 使用 ByteBuffer 包装数据并设置小端序
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 解析协议头
        int magicNumber = buffer.getInt();  // 读取魔数
        if (magicNumber != 0x12345678) {
            Log.w(TAG, "Invalid magic number: " + magicNumber);  // 魔数不匹配，丢弃数据包
            return;
        }
        
        int commandType = buffer.getInt();   // 读取命令类型
        int dataLength = buffer.getInt();    // 读取数据长度
        
        // 根据命令类型分发到不同的解析方法
        switch (commandType) {
            case 0x10:  // 波形数据（0x10 = 16）
                parseWaveformData(buffer, dataLength);
                break;
            case 0x11:  // 设备状态（0x11 = 17）
                parseDeviceStatus(buffer, dataLength);
                break;
            default:
                Log.d(TAG, "Unknown command type: " + commandType);
                break;
        }
    }
    
    /**
     * 解析波形数据 - 私有方法
     * 
     * 波形数据格式：
     * - 波形类型 (4 字节 int)：0=Recv, 1=Send, 2=Off
     * - 采样点数 (4 字节 int)
     * - 时间点和幅值交替排列：
     *   - 时间点 (8 字节 double)
     *   - 幅值 (4 字节 float)
     *   - ...循环 sampleCount 次
     * 
     * @param buffer ByteBuffer，已定位到数据部分
     * @param length 数据长度
     */
    private void parseWaveformData(ByteBuffer buffer, int length) {
        try {
            int waveformType = buffer.getInt();  // 波形类型：0=Recv, 1=Send, 2=Off
            int sampleCount = buffer.getInt();   // 采样点数
            
            // 创建临时数组存储本次解析的数据
            float[] values = new float[sampleCount];
            double[] times = new double[sampleCount];
            
            // 逐个读取采样点（时间点和幅值）
            for (int i = 0; i < sampleCount; i++) {
                times[i] = buffer.getDouble();   // 时间点（横坐标，单位：秒或毫秒）
                values[i] = buffer.getFloat();   // 幅值（纵坐标，单位：mV 或 A）
            }
            
            // 根据波形类型复制到对应的缓冲区
            switch (waveformType) {
                case 0:  // Recv 波形（接收波形）
                    // 使用 System.arraycopy 高效复制，取较小值避免越界
                    System.arraycopy(values, 0, recvWaveform, 0, Math.min(values.length, recvWaveform.length));
                    System.arraycopy(times, 0, timePoints, 0, Math.min(times.length, timePoints.length));
                    break;
                case 1:  // Send 波形（发送波形）
                    System.arraycopy(values, 0, sendWaveform, 0, Math.min(values.length, sendWaveform.length));
                    break;
                case 2:  // Off 波形（关闭波形/背景噪声）
                    System.arraycopy(values, 0, offWaveform, 0, Math.min(values.length, offWaveform.length));
                    break;
            }
            
            // 通知所有回调：波形数据已更新
            notifyWaveformData(timePoints, recvWaveform, sendWaveform, offWaveform);
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing waveform data", e);  // 记录异常日志
        }
    }
    
    /**
     * 解析设备状态 - 私有方法
     * 
     * 设备状态数据格式（16 字节）：
     * - 电池电压 (4 字节 float)
     * - 工作电流 (4 字节 float)
     * - 设备温度 (4 字节 float)
     * - 信号强度 (4 字节 float)
     * 
     * @param buffer ByteBuffer，已定位到数据部分
     * @param length 数据长度
     */
    private void parseDeviceStatus(ByteBuffer buffer, int length) {
        try {
            // 创建设备监控实体
            DeviceMonitorEntity monitor = new DeviceMonitorEntity();
            
            // 按顺序读取设备状态字段
            monitor.batteryVoltage = buffer.getFloat();    // 电池电压（单位：V）
            monitor.current = buffer.getFloat();           // 工作电流（单位：mA）
            monitor.temperature = buffer.getFloat();       // 设备温度（单位：℃）
            monitor.signalStrength = buffer.getFloat();    // 信号强度（单位：dBm 或百分比）
            monitor.timestamp = System.currentTimeMillis(); // 添加本地时间戳
            
            currentMonitorInfo = monitor;  // 保存当前监控信息
            notifyMonitorInfo(monitor);    // 通知回调
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing device status", e);  // 记录异常日志
        }
    }
    
    /**
     * 通知波形数据 - 私有方法
     * 
     * 作用：在主线程中回调所有监听器，推送最新的波形数据
     * 
     * @param timePoints 时间点数组（横坐标）
     * @param recv Recv 波形（接收波形）
     * @param send Send 波形（发送波形）
     * @param off Off 波形（关闭波形）
     */
    private void notifyWaveformData(double[] timePoints, float[] recv, float[] send, float[] off) {
        // 使用 Handler 切换到主线程执行回调
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onWaveformData(
                    convertDoubleToFloat(timePoints),   // 将 double 转为 float（节省内存）
                    recv,
                    send,
                    off
                );
            }
        });
    }
    
    /**
     * 数据类型转换工具方法：double[] 转 float[]
     * 
     * @param doubles double 类型数组
     * @return float 类型数组
     */
    private float[] convertDoubleToFloat(double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            floats[i] = (float) doubles[i];  // 强制类型转换（可能损失精度）
        }
        return floats;
    }
    
    /**
     * 通知监控信息 - 私有方法
     * 
     * 作用：在主线程中回调所有监听器，推送设备状态信息
     * 
     * @param monitor 设备监控信息实体
     */
    private void notifyMonitorInfo(DeviceMonitorEntity monitor) {
        // 使用 Handler 切换到主线程执行回调
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onMonitorInfo(monitor);
            }
        });
    }
    
    /**
     * 通知采集完成 - 私有方法
     * 
     * 作用：在主线程中回调所有监听器，通知采集流程结束
     */
    private void notifyCollectionComplete() {
        // 使用 Handler 切换到主线程执行回调
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onCollectionComplete();
            }
        });
    }
    
    /**
     * 通知错误 - 私有方法
     * 
     * 作用：在主线程中回调所有监听器，报告错误信息
     * 
     * @param error 错误描述信息
     */
    private void notifyError(String error) {
        // 使用 Handler 切换到主线程执行回调
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onError(error);
            }
        });
    }
    
    // ==================== Getter 方法 ====================
    
    /**
     * 获取当前采集状态
     * 
     * @return CollectionStatus 枚举值
     */
    public CollectionStatus getStatus() {
        return status;
    }
    
    /**
     * 获取当前使用的采集参数
     * 
     * @return 采集参数实体
     */
    public CollectionParameterEntity getCurrentParameters() {
        return currentParameters;
    }
    
    /**
     * 获取当前设备监控信息
     * 
     * @return 设备监控信息实体 (如果未收到过状态更新则为 null)
     */
    public DeviceMonitorEntity getCurrentMonitorInfo() {
        return currentMonitorInfo;
    }
    
    /**
     * 获取 Recv 波形数据
     * 
     * @return Recv 波形数组
     */
    public float[] getRecvWaveform() {
        return recvWaveform.clone();
    }
    
    /**
     * 获取 Send 波形数据
     * 
     * @return Send 波形数组
     */
    public float[] getSendWaveform() {
        return sendWaveform.clone();
    }
    
    /**
     * 获取 Off 波形数据
     * 
     * @return Off 波形数组
     */
    public float[] getOffWaveform() {
        return offWaveform.clone();
    }
    
    /**
     * 获取时间点数据
     * 
     * @return 时间点数组 (double 类型)
     */
    public double[] getTimePoints() {
        return timePoints.clone();
    }
}
