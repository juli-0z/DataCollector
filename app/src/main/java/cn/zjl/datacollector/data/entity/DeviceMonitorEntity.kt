package cn.zjl.datacollector.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_monitor")
data class DeviceMonitorEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var pointId: Long = 0,
    var batteryVoltage: Float = 0f,
    var current: Float = 0f,
    var temperature: Float = 0f,
    var signalStrength: Float = 0f,
    var gpsAccuracy: Float = 0f,
    var sendCurrent: Float = 0f,
    var offTime: Float = 0f,
    var dataRate: Float = 0f,
    var packetLoss: Float = 0f,
    var batteryLevel: Float = 0f,
    var protocolStateCode: Int = 0,
    var systemStatus: Int = 0,
    var statusFrameCount: Int = 0,
    var waveformFrameCount: Int = 0,
    var deviceTimestamp: Long = 0,
    var timestamp: Long = 0
)
