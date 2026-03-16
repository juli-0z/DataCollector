package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 波形数据实体类（存储原始采样数据）
 */
@Entity(tableName = "waveform_data")
public class WaveformDataEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // 所属测点 ID
    public long pointId;
    
    // 波形类型：0-Recv, 1-Send, 2-Off
    public int waveformType;
    
    // 时间点数组（μs）
    public String timePoints;
    
    // 电压/电流值数组（V 或 A）
    public String values;
    
    // 采样点数
    public int sampleCount;
    
    // 创建时间
    public long createdAt;
}
