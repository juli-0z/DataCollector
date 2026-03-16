package cn.zjl.datacollector.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 工程实体类
 */
@Entity(tableName = "projects")
public class ProjectEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // 工程名称
    public String name;
    
    // 数据库文件路径
    public String databasePath;
    
    // 创建时间
    public long createdAt;
    
    // 最后修改时间
    public long updatedAt;
    
    // 描述信息
    public String description;
    
    // 是否已同步到云端
    public boolean isSynced;
}
