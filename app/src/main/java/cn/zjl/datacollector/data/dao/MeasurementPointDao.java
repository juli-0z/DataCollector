package cn.zjl.datacollector.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.MeasurementPointEntity;

@Dao
public interface MeasurementPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MeasurementPointEntity point);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<MeasurementPointEntity> points);

    @Update
    void update(MeasurementPointEntity point);

    @Delete
    void delete(MeasurementPointEntity point);

    @Query("SELECT * FROM data_point WHERE dataLineId = :dataLineId ORDER BY name ASC")
    List<MeasurementPointEntity> getPointsBySurveyLineId(long dataLineId);

    @Query("SELECT * FROM data_point ORDER BY dataLineId ASC, name ASC")
    List<MeasurementPointEntity> getAllPoints();

    @Query("SELECT * FROM data_point WHERE id = :id")
    MeasurementPointEntity getPointById(long id);

    @Query("SELECT * FROM data_point WHERE dataLineId = :dataLineId AND name = :pointName LIMIT 1")
    MeasurementPointEntity getPointByNumber(long dataLineId, float pointName);

    @Query("UPDATE data_point SET status = :status, updatedAt = :updatedAt WHERE id = :pointId")
    void updateStatus(long pointId, int status, long updatedAt);

    @Query("UPDATE data_point SET isSynced = :synced, syncError = :syncError, updatedAt = :updatedAt WHERE id = :pointId")
    void updateSyncState(long pointId, boolean synced, String syncError, long updatedAt);

    @Query("SELECT * FROM data_point WHERE status = 2 AND isQualified = 1 AND isSynced = 0 ORDER BY updatedAt ASC")
    List<MeasurementPointEntity> getUnsyncedPoints();

    @Query("SELECT * FROM data_point WHERE dataLineId = :dataLineId ORDER BY updatedAt DESC LIMIT 1")
    MeasurementPointEntity getLatestPointByLine(long dataLineId);

    @Query("DELETE FROM data_point WHERE dataLineId = :dataLineId")
    void deleteBySurveyLineId(long dataLineId);
}
