package cn.zjl.datacollector.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_project")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var name: String = "",
    var note: String = "",
    var databaseName: String = "",
    var databasePath: String = "",
    var imported: Boolean = false,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var lastSyncedAt: Long = 0,
    var workConfig: Int = 0,
    var lineNoStart: Float = 1f,
    var lineNoStep: Float = 1f,
    var pointNoStart: Float = 1f,
    var pointNoStep: Float = 1f,
    @ColumnInfo(name = "SendCoil_Len") var sendCoil_Len: Float = 0f,
    @ColumnInfo(name = "SendCoil_Width") var sendCoil_Width: Float = 0f,
    @ColumnInfo(name = "SendCoil_Turns") var sendCoil_Turns: Float = 0f,
    @ColumnInfo(name = "RecvCoil_Size") var recvCoil_Size: Float = 0f,
    @ColumnInfo(name = "RecvCoil_Gain") var recvCoil_Gain: Float = 0f,
    var offTime: Float = 0f,
    var pointLen_D: Float = 0f,
    var pointLen_R: Float = 0f,
    var calibrateNo: String = "",
    var ssid: String = "",
    var greateTime: Long = 0
)
