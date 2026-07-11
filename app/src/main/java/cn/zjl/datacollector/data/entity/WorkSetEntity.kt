package cn.zjl.datacollector.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_workset")
data class WorkSetEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var workConfig: Int = 0,
    var sendCoil_Len: Float = 0f,
    var sendCoil_Width: Float = 0f,
    var sendCoil_Turns: Float = 0f,
    var recvCoil_Size: Float = 0f,
    var recvCoil_Gain: Float = 0f,
    var createdAt: Long = 0,
    var updatedAt: Long = 0
)
