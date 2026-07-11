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

import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;

@Dao
public interface DeviceMonitorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(DeviceMonitorEntity monitor);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<DeviceMonitorEntity> monitors);

    @Update
    void update(DeviceMonitorEntity monitor);

    @Delete
    void delete(DeviceMonitorEntity monitor);

    @Query("SELECT * FROM device_monitor WHERE pointId = :pointId ORDER BY timestamp ASC")
    List<DeviceMonitorEntity> getMonitorsByPointId(long pointId);

    @Query("SELECT * FROM device_monitor WHERE id = :id")
    DeviceMonitorEntity getMonitorById(long id);

    @Query("DELETE FROM device_monitor WHERE pointId = :pointId")
    void deleteByPointId(long pointId);
}
