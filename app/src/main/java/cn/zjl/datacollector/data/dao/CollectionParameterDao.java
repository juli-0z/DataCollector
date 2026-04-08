package cn.zjl.datacollector.data.dao;

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
