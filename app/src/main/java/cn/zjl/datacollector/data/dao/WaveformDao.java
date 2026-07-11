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
