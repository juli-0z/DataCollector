package cn.zjl.datacollector.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_point")
data class MeasurementPointEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var name: Float = 0f,
    var use: Int = 0,
    var note: String = "",
    var type: Int = 0,
    var dataLineId: Long = 0,
    var status: Int = 0,
    var isQualified: Boolean = false,
    var isSynced: Boolean = false,
    var collectionTime: Long = 0,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var altitude: Double = 0.0,
    var syncError: String? = null,
    var createdAt: Long = 0,
    var updatedAt: Long = 0
)
