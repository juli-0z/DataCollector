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
 * 数据采集管理器
 * 负责控制采集流程、解析数据
 */
public class CollectionManager {
    private static final String TAG = "CollectionManager";
    
    // 采集状态
    public enum CollectionStatus {
        IDLE,           // 空闲
        CONFIGURING,    // 配置中
        COLLECTING,     // 采集中
        PAUSED,         // 暂停
        COMPLETED       // 完成
    }
    
    // TCP 命令类型定义
    private static final int CMD_START_COLLECTION = 0x01;
    private static final int CMD_STOP_COLLECTION = 0x02;
    private static final int CMD_SET_PARAMETERS = 0x03;
    private static final int CMD_GET_STATUS = 0x04;
    
    private TcpClientManager tcpClient;
    private CollectionStatus status = CollectionStatus.IDLE;
    
    private CollectionParameterEntity currentParameters;
    private List<DataCallback> dataCallbacks = new ArrayList<>();
    
    private Handler mainHandler;
    
    // 实时波形数据缓冲区
    private float[] recvWaveform;
    private float[] sendWaveform;
    private float[] offWaveform;
    private double[] timePoints;
    
    // 设备监控信息
    private DeviceMonitorEntity currentMonitorInfo;
    
    /**
     * 数据回调接口
     */
    public interface DataCallback {
        void onWaveformData(float[] timePoints, float[] recvValues, float[] sendValues, float[] offValues);
        void onMonitorInfo(DeviceMonitorEntity monitor);
        void onCollectionComplete();
        void onError(String error);
    }
    
    public CollectionManager(TcpClientManager tcpClient) {
        this.tcpClient = tcpClient;
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化波形缓冲区（默认大小，可根据需要调整）
        int bufferSize = 10000;
        recvWaveform = new float[bufferSize];
        sendWaveform = new float[bufferSize];
        offWaveform = new float[bufferSize];
        timePoints = new double[bufferSize];
    }
    
    /**
     * 添加数据回调
     */
    public void addDataCallback(DataCallback callback) {
        if (!dataCallbacks.contains(callback)) {
            dataCallbacks.add(callback);
        }
    }
    
    /**
     * 移除数据回调
     */
    public void removeDataCallback(DataCallback callback) {
        dataCallbacks.remove(callback);
    }
    
    /**
     * 设置采集参数
     */
    public void setParameters(CollectionParameterEntity parameters) {
        if (status == CollectionStatus.COLLECTING) {
            notifyError("正在采集中，无法修改参数");
            return;
        }
        
        this.currentParameters = parameters;
        
        // 发送参数到设备
        sendParametersToDevice(parameters);
    }
    
    /**
     * 发送参数到设备
     */
    private void sendParametersToDevice(CollectionParameterEntity params) {
        if (!tcpClient.isConnected()) {
            notifyError("TCP 未连接");
            return;
        }
        
        // 构建参数数据包
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        buffer.putFloat(params.transmitCurrent);      // 发送电流
        buffer.putInt(params.sampleFrequency);        // 采样频率
        buffer.putInt(params.collectionCount);        // 采集次数
        buffer.putFloat(params.sampleTime);           // 采样时间
        buffer.putFloat(params.electrodeDistance);    // 极距
        
        // 其他参数可以扩展
        
        byte[] payload = buffer.array();
        tcpClient.sendCommand(CMD_SET_PARAMETERS, payload);
        
        Log.i(TAG, "Parameters sent to device");
    }
    
    /**
     * 开始采集
     */
    public void startCollection() {
        if (!tcpClient.isConnected()) {
            notifyError("TCP 未连接，无法开始采集");
            return;
        }
        
        if (currentParameters == null) {
            notifyError("请先设置采集参数");
            return;
        }
        
        status = CollectionStatus.COLLECTING;
        
        // 发送开始采集命令
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(currentParameters.collectionCount);
        
        tcpClient.sendCommand(CMD_START_COLLECTION, buffer.array());
        
        Log.i(TAG, "Collection started");
    }
    
    /**
     * 停止采集
     */
    public void stopCollection() {
        if (status != CollectionStatus.COLLECTING) {
            return;
        }
        
        tcpClient.sendCommand(CMD_STOP_COLLECTION, null);
        status = CollectionStatus.COMPLETED;
        
        notifyCollectionComplete();
        Log.i(TAG, "Collection stopped");
    }
    
    /**
     * 处理接收到的数据
     */
    public void processReceivedData(byte[] data) {
        if (data == null || data.length < 8) {
            return;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 解析协议包
        int magicNumber = buffer.getInt();
        if (magicNumber != 0x12345678) {
            Log.w(TAG, "Invalid magic number: " + magicNumber);
            return;
        }
        
        int commandType = buffer.getInt();
        int dataLength = buffer.getInt();
        
        // 根据命令类型解析数据
        switch (commandType) {
            case 0x10:  // 波形数据
                parseWaveformData(buffer, dataLength);
                break;
            case 0x11:  // 设备状态
                parseDeviceStatus(buffer, dataLength);
                break;
            default:
                Log.d(TAG, "Unknown command type: " + commandType);
                break;
        }
    }
    
    /**
     * 解析波形数据
     */
    private void parseWaveformData(ByteBuffer buffer, int length) {
        try {
            int waveformType = buffer.getInt();  // 波形类型
            int sampleCount = buffer.getInt();   // 采样点数
            
            float[] values = new float[sampleCount];
            double[] times = new double[sampleCount];
            
            for (int i = 0; i < sampleCount; i++) {
                times[i] = buffer.getDouble();   // 时间点
                values[i] = buffer.getFloat();   // 幅值
            }
            
            // 根据波形类型存储
            switch (waveformType) {
                case 0:  // Recv
                    System.arraycopy(values, 0, recvWaveform, 0, Math.min(values.length, recvWaveform.length));
                    System.arraycopy(times, 0, timePoints, 0, Math.min(times.length, timePoints.length));
                    break;
                case 1:  // Send
                    System.arraycopy(values, 0, sendWaveform, 0, Math.min(values.length, sendWaveform.length));
                    break;
                case 2:  // Off
                    System.arraycopy(values, 0, offWaveform, 0, Math.min(values.length, offWaveform.length));
                    break;
            }
            
            // 通知回调
            notifyWaveformData(timePoints, recvWaveform, sendWaveform, offWaveform);
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing waveform data", e);
        }
    }
    
    /**
     * 解析设备状态
     */
    private void parseDeviceStatus(ByteBuffer buffer, int length) {
        try {
            DeviceMonitorEntity monitor = new DeviceMonitorEntity();
            monitor.batteryVoltage = buffer.getFloat();
            monitor.current = buffer.getFloat();
            monitor.temperature = buffer.getFloat();
            monitor.signalStrength = buffer.getFloat();
            monitor.timestamp = System.currentTimeMillis();
            
            currentMonitorInfo = monitor;
            notifyMonitorInfo(monitor);
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing device status", e);
        }
    }
    
    /**
     * 通知波形数据
     */
    private void notifyWaveformData(double[] timePoints, float[] recv, float[] send, float[] off) {
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onWaveformData(
                    convertDoubleToFloat(timePoints), 
                    recv, 
                    send, 
                    off
                );
            }
        });
    }
    
    private float[] convertDoubleToFloat(double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            floats[i] = (float) doubles[i];
        }
        return floats;
    }
    
    /**
     * 通知监控信息
     */
    private void notifyMonitorInfo(DeviceMonitorEntity monitor) {
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onMonitorInfo(monitor);
            }
        });
    }
    
    /**
     * 通知采集完成
     */
    private void notifyCollectionComplete() {
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onCollectionComplete();
            }
        });
    }
    
    /**
     * 通知错误
     */
    private void notifyError(String error) {
        mainHandler.post(() -> {
            for (DataCallback callback : dataCallbacks) {
                callback.onError(error);
            }
        });
    }
    
    /**
     * 获取当前采集状态
     */
    public CollectionStatus getStatus() {
        return status;
    }
    
    /**
     * 获取当前参数
     */
    public CollectionParameterEntity getCurrentParameters() {
        return currentParameters;
    }
    
    /**
     * 获取当前监控信息
     */
    public DeviceMonitorEntity getCurrentMonitorInfo() {
        return currentMonitorInfo;
    }
}
