package cn.zjl.datacollector.data.dao;

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
