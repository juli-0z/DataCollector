package cn.zjl.datacollector.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.MeasurementPointEntity;

/**
 * 测点数据访问对象
 */
@Dao
public interface MeasurementPointDao {
    @Insert
    long insert(MeasurementPointEntity point);
    
    @Insert
    List<Long> insertAll(List<MeasurementPointEntity> points);
    
    @Update
    void update(MeasurementPointEntity point);
    
    @Delete
    void delete(MeasurementPointEntity point);
    
    @Query("SELECT * FROM measurement_points WHERE surveyLineId = :surveyLineId ORDER BY pointNumber ASC")
    List<MeasurementPointEntity> getPointsBySurveyLineId(long surveyLineId);
    
    @Query("SELECT * FROM measurement_points WHERE id = :id")
    MeasurementPointEntity getPointById(long id);
    
    @Query("SELECT * FROM measurement_points WHERE surveyLineId = :surveyLineId AND pointNumber = :pointNumber LIMIT 1")
    MeasurementPointEntity getPointByNumber(long surveyLineId, int pointNumber);
    
    @Query("UPDATE measurement_points SET status = :status WHERE id = :pointId")
    void updateStatus(long pointId, int status);
    
    @Query("SELECT * FROM measurement_points WHERE status >= 2 AND isQualified = 1")
    List<MeasurementPointEntity> getQualifiedPoints();
    
    @Query("SELECT * FROM measurement_points WHERE status = 3")
    List<MeasurementPointEntity> getSyncedPoints();
    
    @Query("DELETE FROM measurement_points WHERE surveyLineId = :surveyLineId")
    void deleteBySurveyLineId(long surveyLineId);
}
