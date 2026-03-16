package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 测点实体类
 */
@Entity(tableName = "measurement_points")
public class MeasurementPointEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // 所属测线 ID
    public long surveyLineId;
    
    // 测点编号（如 1, 5, 10）
    public int pointNumber;
    
    // 采集状态：0-未采集，1-已采集未保存，2-已保存，3-已同步
    public int status;
    
    // 是否合格
    public boolean isQualified;
    
    // 采集时间
    public long collectionTime;
    
    // GPS 纬度
    public double latitude;
    
    // GPS 经度
    public double longitude;
    
    // 海拔高度
    public double altitude;
    
    // 备注
    public String remark;
}
