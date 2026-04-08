package cn.zjl.datacollector.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.WaveformDataEntity;

@Dao
public interface WaveformDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(WaveformDataEntity waveform);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<WaveformDataEntity> waveforms);

    @Update
    void update(WaveformDataEntity waveform);

    @Delete
    void delete(WaveformDataEntity waveform);

    @Query("SELECT * FROM data_sample WHERE dataPointId = :dataPointId ORDER BY startTime ASC, type ASC, id ASC")
    List<WaveformDataEntity> getWaveformsByPointId(long dataPointId);

    @Query("SELECT * FROM data_sample WHERE id = :id")
    WaveformDataEntity getWaveformById(long id);

    @Query("SELECT COUNT(*) FROM data_sample WHERE dataPointId = :dataPointId")
    int countByPointId(long dataPointId);

    @Query("DELETE FROM data_sample WHERE dataPointId = :dataPointId")
    void deleteByPointId(long dataPointId);
}
