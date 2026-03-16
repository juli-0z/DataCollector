package cn.zjl.datacollector.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import cn.zjl.datacollector.data.dao.CollectionParameterDao;
import cn.zjl.datacollector.data.dao.DeviceMonitorDao;
import cn.zjl.datacollector.data.dao.MeasurementPointDao;
import cn.zjl.datacollector.data.dao.ProjectDao;
import cn.zjl.datacollector.data.dao.SurveyLineDao;
import cn.zjl.datacollector.data.dao.WaveformDao;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;

/**
 * 应用主数据库
 */
@Database(
    entities = {
        ProjectEntity.class,
        SurveyLineEntity.class,
        MeasurementPointEntity.class,
        CollectionParameterEntity.class,
        WaveformDataEntity.class,
        DeviceMonitorEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    
    public abstract ProjectDao projectDao();
    public abstract SurveyLineDao surveyLineDao();
    public abstract MeasurementPointDao measurementPointDao();
    public abstract CollectionParameterDao collectionParameterDao();
    public abstract WaveformDao waveformDao();
    public abstract DeviceMonitorDao deviceMonitorDao();
    
    /**
     * 获取单例数据库实例
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "data_collector.db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 销毁数据库实例（用于测试）
     */
    public static void destroyInstance() {
        INSTANCE = null;
    }
}
