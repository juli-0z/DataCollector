package cn.zjl.datacollector.data.dao;

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
