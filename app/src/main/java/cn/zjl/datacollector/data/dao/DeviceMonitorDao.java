package cn.zjl.datacollector.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;

/**
 * 设备监控数据访问对象
 */
@Dao
public interface DeviceMonitorDao {
    @Insert
    long insert(DeviceMonitorEntity monitor);
    
    @Insert
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
