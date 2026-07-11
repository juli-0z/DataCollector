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

import cn.zjl.datacollector.data.entity.SurveyLineEntity;

@Dao
public interface SurveyLineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SurveyLineEntity surveyLine);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<SurveyLineEntity> surveyLines);

    @Update
    void update(SurveyLineEntity surveyLine);

    @Delete
    void delete(SurveyLineEntity surveyLine);

    @Query("SELECT * FROM data_line WHERE projectId = :projectId ORDER BY name ASC")
    List<SurveyLineEntity> getSurveyLinesByProjectId(long projectId);

    @Query("SELECT * FROM data_line ORDER BY name ASC, id ASC")
    List<SurveyLineEntity> getAllSurveyLines();

    @Query("SELECT * FROM data_line WHERE id = :id")
    SurveyLineEntity getSurveyLineById(long id);

    @Query("DELETE FROM data_line WHERE projectId = :projectId")
    void deleteByProjectId(long projectId);
}
