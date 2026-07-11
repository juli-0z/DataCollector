package cn.zjl.datacollector.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "data_project")
public class ProjectEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;

    public String note;

    public String databaseName;

    public String databasePath;

    public boolean imported;

    public long createdAt;

    public long updatedAt;

    public long lastSyncedAt;

    public int workConfig;

    public float lineNoStart;

    public float lineNoStep;

    public float pointNoStart;

    public float pointNoStep;

    @ColumnInfo(name = "SendCoil_Len")
    public float sendCoil_Len;

    @ColumnInfo(name = "SendCoil_Width")
    public float sendCoil_Width;

    @ColumnInfo(name = "SendCoil_Turns")
    public float sendCoil_Turns;

    @ColumnInfo(name = "RecvCoil_Size")
    public float recvCoil_Size;

    @ColumnInfo(name = "RecvCoil_Gain")
    public float recvCoil_Gain;

    public float offTime;

    public float pointLen_D;

    public float pointLen_R;

    public String calibrateNo;

    public String ssid;

    public long greateTime;
}
