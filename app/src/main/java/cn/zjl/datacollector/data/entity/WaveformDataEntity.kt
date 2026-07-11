package cn.zjl.datacollector.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_sample")
data class WaveformDataEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var startTime: Long = 0,
    var deviceType: Int = 0,
    var type: Int = 0,
    var period: Int = 0,
    var dataRecv: ByteArray? = null,
    var dataRecvPos: ByteArray? = null,
    var dataRecvLen: ByteArray? = null,
    var dataSend: ByteArray? = null,
    var dataSoff: ByteArray? = null,
    var sendFs: Float = 0f,
    var simpleSendFs: Float = 0f,
    var simpleOffFs: Float = 0f,
    var recvFs: Float = 0f,
    var use: Int = 0,
    var note: String = "",
    var dataPointId: Long = 0,
    var createdAt: Long = 0
)
