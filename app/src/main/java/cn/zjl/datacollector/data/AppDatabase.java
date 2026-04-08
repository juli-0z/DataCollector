package cn.zjl.datacollector.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.zjl.datacollector.data.dao.CollectionParameterDao;
import cn.zjl.datacollector.data.dao.DeviceMonitorDao;
import cn.zjl.datacollector.data.dao.MeasurementPointDao;
import cn.zjl.datacollector.data.dao.ProjectDao;
import cn.zjl.datacollector.data.dao.SurveyLineDao;
import cn.zjl.datacollector.data.dao.WaveformDao;
import cn.zjl.datacollector.data.dao.WorkSetDao;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.data.entity.WorkSetEntity;

@Database(
        entities = {
                WorkSetEntity.class,
                ProjectEntity.class,
                SurveyLineEntity.class,
                MeasurementPointEntity.class,
                CollectionParameterEntity.class,
                WaveformDataEntity.class,
                DeviceMonitorEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public static final String INDEX_DATABASE_NAME = "project_index.sqlite";

    private static final Map<String, AppDatabase> INSTANCES = new ConcurrentHashMap<>();

    public abstract WorkSetDao workSetDao();

    public abstract ProjectDao projectDao();

    public abstract SurveyLineDao surveyLineDao();

    public abstract MeasurementPointDao measurementPointDao();

    public abstract CollectionParameterDao collectionParameterDao();

    public abstract WaveformDao waveformDao();

    public abstract DeviceMonitorDao deviceMonitorDao();

    public static AppDatabase getInstance(@NonNull Context context) {
        return getInstance(context, INDEX_DATABASE_NAME);
    }

    public static AppDatabase getInstance(@NonNull Context context, @NonNull String databaseName) {
        AppDatabase existing = INSTANCES.get(databaseName);
        if (existing != null) {
            return existing;
        }
        synchronized (AppDatabase.class) {
            existing = INSTANCES.get(databaseName);
            if (existing == null) {
                existing = Room.databaseBuilder(
                                context.getApplicationContext(),
                                AppDatabase.class,
                                databaseName)
                        .fallbackToDestructiveMigration()
                        .build();
                INSTANCES.put(databaseName, existing);
            }
            return existing;
        }
    }

    public static void closeDatabase(@NonNull String databaseName) {
        AppDatabase database = INSTANCES.remove(databaseName);
        if (database != null) {
            database.close();
        }
    }

    public static void destroyAll() {
        for (Map.Entry<String, AppDatabase> entry : INSTANCES.entrySet()) {
            entry.getValue().close();
        }
        INSTANCES.clear();
    }
}
