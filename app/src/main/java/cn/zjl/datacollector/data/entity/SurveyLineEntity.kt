package cn.zjl.datacollector.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_line")
data class SurveyLineEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var name: Float = 0f,
    var type: Int = 0,
    var use: Int = 0,
    var note: String = "",
    var projectId: Long = 0,
    var createdAt: Long = 0,
    var updatedAt: Long = 0
)
