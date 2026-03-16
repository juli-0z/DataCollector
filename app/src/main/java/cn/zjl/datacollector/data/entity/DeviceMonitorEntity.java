package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 设备监控信息实体类
 */
@Entity(tableName = "device_monitor")
public class DeviceMonitorEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // 所属测点 ID
    public long pointId;
    
    // 电池电压（V）
    public float batteryVoltage;
    
    // 电流（A）
    public float current;
    
    // 温度（C）
    public float temperature;
    
    // 信号强度（dBm）
    public float signalStrength;
    
    // 记录时间
    public long timestamp;
}
