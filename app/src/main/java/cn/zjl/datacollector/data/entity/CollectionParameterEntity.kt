package cn.zjl.datacollector.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collection_parameters")
data class CollectionParameterEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var pointId: Long = 0,
    var transmitCurrent: Float = 0f,
    var sampleFrequency: Int = 0,
    var collectionCount: Int = 0,
    var sampleTime: Float = 0f,
    var electrodeDistance: Float = 0f,
    var transmitterDirection: String = "",
    var customParameters: String = "",
    var createdAt: Long = 0
)
