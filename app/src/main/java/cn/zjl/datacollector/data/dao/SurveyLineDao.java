package cn.zjl.datacollector.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.SurveyLineEntity;

/**
 * 测线数据访问对象
 */
@Dao
public interface SurveyLineDao {
    @Insert
    long insert(SurveyLineEntity surveyLine);
    
    @Insert
    List<Long> insertAll(List<SurveyLineEntity> surveyLines);
    
    @Update
    void update(SurveyLineEntity surveyLine);
    
    @Delete
    void delete(SurveyLineEntity surveyLine);
    
    @Query("SELECT * FROM survey_lines WHERE projectId = :projectId ORDER BY createdAt ASC")
    List<SurveyLineEntity> getSurveyLinesByProjectId(long projectId);
    
    @Query("SELECT * FROM survey_lines WHERE id = :id")
    SurveyLineEntity getSurveyLineById(long id);
    
    @Query("DELETE FROM survey_lines WHERE projectId = :projectId")
    void deleteByProjectId(long projectId);
}
