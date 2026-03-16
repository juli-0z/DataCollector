package cn.zjl.datacollector.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.WaveformDataEntity;

/**
 * 波形数据访问对象
 */
@Dao
public interface WaveformDao {
    @Insert
    long insert(WaveformDataEntity waveform);
    
    @Insert
    List<Long> insertAll(List<WaveformDataEntity> waveforms);
    
    @Update
    void update(WaveformDataEntity waveform);
    
    @Delete
    void delete(WaveformDataEntity waveform);
    
    @Query("SELECT * FROM waveform_data WHERE pointId = :pointId ORDER BY waveformType ASC")
    List<WaveformDataEntity> getWaveformsByPointId(long pointId);
    
    @Query("SELECT * FROM waveform_data WHERE id = :id")
    WaveformDataEntity getWaveformById(long id);
    
    @Query("DELETE FROM waveform_data WHERE pointId = :pointId")
    void deleteByPointId(long pointId);
}
