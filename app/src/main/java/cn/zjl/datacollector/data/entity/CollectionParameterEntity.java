package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 采集参数实体类
 */
@Entity(tableName = "collection_parameters")
public class CollectionParameterEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // 所属测点 ID
    public long pointId;
    
    // 发送电流（A）
    public float transmitCurrent;
    
    // 采样频率（Hz）
    public int sampleFrequency;
    
    // 采集次数
    public int collectionCount;
    
    // 采样时间（μs）
    public float sampleTime;
    
    // 极距（m）
    public float electrodeDistance;
    
    // 发射线圈方向
    public String transmitterDirection;
    
    // 其他自定义参数（JSON 格式）
    public String customParameters;
    
    // 创建时间
    public long createdAt;
}
