package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "data_point")
public class MeasurementPointEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public float name;

    public int use;

    public String note;

    public int type;

    public long dataLineId;

    public int status;

    public boolean isQualified;

    public boolean isSynced;

    public long collectionTime;

    public double latitude;

    public double longitude;

    public double altitude;

    public String syncError;

    public long createdAt;

    public long updatedAt;
}
