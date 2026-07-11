package cn.zjl.datacollector.data.dao;

/**
 * 阅读提示：Room DAO 数据访问接口：定义本地数据库对应表的增删改查和同步状态查询方法。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.WorkSetEntity;

/**
 * 工作站参数数据访问对象
 */
@Dao
public interface WorkSetDao {
    @Insert
    long insert(WorkSetEntity workSet);
    
    @Update
    void update(WorkSetEntity workSet);
    
    @Delete
    void delete(WorkSetEntity workSet);
    
    @Query("SELECT * FROM data_workset ORDER BY updatedAt DESC LIMIT 1")
    WorkSetEntity getLatestWorkSet();
    
    @Query("SELECT * FROM data_workset WHERE id = :id")
    WorkSetEntity getWorkSetById(long id);
    
    @Query("SELECT * FROM data_workset ORDER BY createdAt ASC")
    List<WorkSetEntity> getAllWorkSets();
}
