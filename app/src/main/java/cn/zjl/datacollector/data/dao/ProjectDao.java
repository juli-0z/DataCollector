package cn.zjl.datacollector.data.dao;

/**
 * 阅读提示：Room DAO 数据访问接口：定义本地数据库对应表的增删改查和同步状态查询方法。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.ProjectEntity;

@Dao
public interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ProjectEntity project);

    @Update
    void update(ProjectEntity project);

    @Delete
    void delete(ProjectEntity project);

    @Query("SELECT * FROM data_project ORDER BY updatedAt DESC")
    List<ProjectEntity> getAllProjects();

    @Query("SELECT * FROM data_project WHERE id = :id")
    ProjectEntity getProjectById(long id);

    @Query("SELECT * FROM data_project WHERE name = :name LIMIT 1")
    ProjectEntity getProjectByName(String name);

    @Query("SELECT * FROM data_project WHERE databaseName = :databaseName LIMIT 1")
    ProjectEntity getProjectByDatabaseName(String databaseName);

    @Query("SELECT * FROM data_project ORDER BY id ASC LIMIT 1")
    ProjectEntity getFirstProject();

    @Query("DELETE FROM data_project")
    void deleteAll();
}
