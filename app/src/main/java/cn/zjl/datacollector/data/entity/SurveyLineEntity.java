package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 测线实体类
 */
@Entity(tableName = "survey_lines")
public class SurveyLineEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // 所属工程 ID
    public long projectId;
    
    // 测线名称/编号
    public String name;
    
    // 测线描述
    public String description;
    
    // 创建时间
    public long createdAt;
    
    // 最后修改时间
    public long updatedAt;
}
