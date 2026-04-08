package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "collection_parameters")
public class CollectionParameterEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long pointId;

    public float transmitCurrent;

    public int sampleFrequency;

    public int collectionCount;

    public float sampleTime;

    public float electrodeDistance;

    public String transmitterDirection;

    public String customParameters;

    public long createdAt;
}
