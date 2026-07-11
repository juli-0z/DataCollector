package cn.zjl.datacollector.data;

/**
 * 阅读提示：Room 数据库定义：声明工程、测线、测点、采集参数、监控信息和波形等表结构。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

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
        version = 6,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    /** 应用主索引库：只保存工程列表等全局信息，不保存每个工程的完整采集数据。 */
    public static final String INDEX_DATABASE_NAME = "project_index.sqlite";

    /**
     * 版本 4 -> 5：为设备监控表补充现场联调需要的扩展字段。
     *
     * <p>注意：这些字段会出现在每个工程数据库中，所以新增字段时要同时考虑历史工程库迁移。</p>
     */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN gpsAccuracy REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN sendCurrent REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN offTime REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN dataRate REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN packetLoss REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN batteryLevel REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN protocolStateCode INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN systemStatus INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN statusFrameCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN waveformFrameCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE device_monitor ADD COLUMN deviceTimestamp INTEGER NOT NULL DEFAULT 0");
        }
    };
/**
 * 版本 5 -> 6：Entity 从 Java POJO 迁移为 Kotlin data class（无 SQL schema 变更）。
 * 使用 fallbackToDestructiveMigration() 自动处理。
 */

    /**
     * 数据库实例缓存。
     *
     * <p>本项目支持一个“工程索引库”加多个“工程数据库文件”，因此不能只用单例；
     * 这里按 databaseName 缓存，避免同一个 SQLite 文件被重复打开。</p>
     */
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
                // databaseName 可以是 project_index.sqlite，也可以是某个具体工程的 .sqlite 文件名。
                existing = Room.databaseBuilder(
                                context.getApplicationContext(),
                                AppDatabase.class,
                                databaseName)
                        .addMigrations(MIGRATION_4_5)
                        // 课程设计阶段为了兼容早期样例库保留破坏性迁移；正式交付建议补齐所有版本迁移。
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
