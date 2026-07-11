package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "device_monitor")
public class DeviceMonitorEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long pointId;

    public float batteryVoltage;

    public float current;

    public float temperature;

    public float signalStrength;

    public long timestamp;
}
