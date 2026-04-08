package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "data_sample")
public class WaveformDataEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long startTime;

    public int deviceType;

    public int type;

    public int period;

    public byte[] dataRecv;

    public byte[] dataRecvPos;

    public byte[] dataRecvLen;

    public byte[] dataSend;

    public byte[] dataSoff;

    public float sendFs;

    public float simpleSendFs;

    public float simpleOffFs;

    public float recvFs;

    public int use;

    public String note;

    public long dataPointId;

    public long createdAt;
}
