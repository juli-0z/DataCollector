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

import cn.zjl.datacollector.data.entity.CollectionParameterEntity;

@Dao
public interface CollectionParameterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CollectionParameterEntity parameter);

    @Update
    void update(CollectionParameterEntity parameter);

    @Delete
    void delete(CollectionParameterEntity parameter);

    @Query("SELECT * FROM collection_parameters WHERE pointId = :pointId ORDER BY createdAt DESC LIMIT 1")
    CollectionParameterEntity getLatestParametersByPointId(long pointId);

    @Query("SELECT * FROM collection_parameters WHERE id = :id")
    CollectionParameterEntity getParameterById(long id);

    @Query("DELETE FROM collection_parameters WHERE pointId = :pointId")
    void deleteByPointId(long pointId);
}
