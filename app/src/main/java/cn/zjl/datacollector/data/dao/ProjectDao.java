package cn.zjl.datacollector.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.ProjectEntity;

/**
 * 工程数据访问对象
 */
@Dao
public interface ProjectDao {
    @Insert
    long insert(ProjectEntity project);
    
    @Update
    void update(ProjectEntity project);
    
    @Delete
    void delete(ProjectEntity project);
    
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    List<ProjectEntity> getAllProjects();
    
    @Query("SELECT * FROM projects WHERE id = :id")
    ProjectEntity getProjectById(long id);
    
    @Query("SELECT * FROM projects WHERE name = :name LIMIT 1")
    ProjectEntity getProjectByName(String name);
    
    @Query("UPDATE projects SET isSynced = :synced WHERE id = :projectId")
    void updateSyncStatus(long projectId, boolean synced);
    
    @Query("SELECT * FROM projects WHERE isSynced = 0")
    List<ProjectEntity> getUnsyncedProjects();
}
