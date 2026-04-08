package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "data_workset")
public class WorkSetEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public int workConfig;

    public float sendCoil_Len;

    public float sendCoil_Width;

    public float sendCoil_Turns;

    public float recvCoil_Size;

    public float recvCoil_Gain;

    public long createdAt;

    public long updatedAt;
}
