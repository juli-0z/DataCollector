package cn.zjl.datacollector.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.AppDatabase;
import cn.zjl.datacollector.data.dao.MeasurementPointDao;
import cn.zjl.datacollector.data.dao.ProjectDao;
import cn.zjl.datacollector.data.dao.SurveyLineDao;
import cn.zjl.datacollector.data.dao.WaveformDao;
import cn.zjl.datacollector.data.dao.WorkSetDao;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.data.entity.WorkSetEntity;

public class ProjectRepository {

    private static final String BUNDLED_PREFS = "bundled_projects";
    private static final String BUNDLED_JJSK008_KEY = "bundled_jjsk008_installed";
    private static final String BUNDLED_JJSK008_VERSION_KEY = "bundled_jjsk008_version";
    private static final String BUNDLED_JJSK008_ASSET = "bundled_projects/jjsk008.db";
    private static final String BUNDLED_JJSK008_NAME = "jjsk008";
    private static final int BUNDLED_JJSK008_VERSION = 2;

    private final Context context;
    private final ProjectDao projectDao;
    private final ExecutorService executorService;

    public ProjectRepository(Context context) {
        this.context = context.getApplicationContext();
        this.projectDao = AppDatabase.getInstance(this.context).projectDao();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public interface CreateProjectCallback {
        void onSuccess(ProjectEntity project);

        void onError(String error);
    }

    public interface DeleteProjectCallback {
        void onSuccess();

        void onError(String error);
    }

    public interface GetProjectsCallback {
        void onResult(List<ProjectEntity> projects);
    }

    public interface ActionCallback {
        void onSuccess();

        void onError(String error);
    }

    public void ensureBundledProjectsInstalled(ActionCallback callback) {
        executorService.execute(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(BUNDLED_PREFS, Context.MODE_PRIVATE);
                ProjectEntity bundledProject = projectDao.getProjectByName(BUNDLED_JJSK008_NAME);
                boolean installed = prefs.getBoolean(BUNDLED_JJSK008_KEY, false);
                int installedVersion = prefs.getInt(BUNDLED_JJSK008_VERSION_KEY, 0);
                boolean valid = isBundledProjectValid(bundledProject);
                if (installed && installedVersion >= BUNDLED_JJSK008_VERSION && valid) {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                    return;
                }

                if (bundledProject != null) {
                    deleteProjectInternal(bundledProject);
                }

                importBundledProject(BUNDLED_JJSK008_ASSET, BUNDLED_JJSK008_NAME);
                prefs.edit()
                        .putBoolean(BUNDLED_JJSK008_KEY, true)
                        .putInt(BUNDLED_JJSK008_VERSION_KEY, BUNDLED_JJSK008_VERSION)
                        .apply();
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(context.getString(R.string.error_bundled_project_init_failed, e.getMessage()));
                }
            }
        });
    }

    public void getAllProjects(GetProjectsCallback callback) {
        executorService.execute(() -> {
            if (callback != null) {
                callback.onResult(projectDao.getAllProjects());
            }
        });
    }

    public void createProject(String name, String description, CreateProjectCallback callback) {
        executorService.execute(() -> {
            try {
                if (projectDao.getProjectByName(name) != null) {
                    callback.onError(context.getString(R.string.error_project_name_exists));
                    return;
                }
                String databaseName = buildDatabaseName(name);
                resetDatabase(databaseName);

                ProjectEntity projectRecord = buildProjectRecord(name, description, databaseName, false);
                AppDatabase projectDatabase = AppDatabase.getInstance(context, databaseName);
                ProjectDao projectDbDao = projectDatabase.projectDao();
                projectDatabase.runInTransaction(() -> {
                    projectDbDao.deleteAll();
                    long projectId = projectDbDao.insert(projectRecord);
                    projectRecord.id = projectId;
                });

                ProjectEntity indexProject = copyForIndex(projectRecord);
                long indexId = projectDao.insert(indexProject);
                indexProject.id = indexId;
                callback.onSuccess(indexProject);
            } catch (Exception e) {
                callback.onError(context.getString(R.string.error_create_project_failed, e.getMessage()));
            }
        });
    }

    public void importProject(Uri uri, String preferredName, CreateProjectCallback callback) {
        executorService.execute(() -> {
            File tempFile = null;
            try {
                tempFile = copyUriToTempFile(uri);
                ProjectEntity indexProject = importProjectFromFile(
                        tempFile,
                        preferredName,
                        guessDisplayName(uri));
                callback.onSuccess(indexProject);
            } catch (Exception e) {
                callback.onError(context.getString(R.string.error_import_project_failed, e.getMessage()));
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        });
    }

    public void deleteProject(ProjectEntity project, DeleteProjectCallback callback) {
        executorService.execute(() -> {
            try {
                deleteProjectInternal(project);
                callback.onSuccess();
            } catch (Exception e) {
                callback.onError(context.getString(R.string.error_delete_project_failed, e.getMessage()));
            }
        });
    }

    public void touchProject(ProjectEntity project) {
        executorService.execute(() -> {
            project.updatedAt = System.currentTimeMillis();
            projectDao.update(project);
        });
    }

    private void importBundledProject(String assetPath, String preferredName) throws Exception {
        File tempFile = null;
        try {
            tempFile = copyAssetToTempFile(assetPath);
            importProjectFromFile(tempFile, preferredName, preferredName);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    private ProjectEntity importProjectFromFile(File sourceFile,
                                                String preferredName,
                                                String fallbackName) throws Exception {
        SQLiteDatabase legacyDb = null;
        try {
            legacyDb = SQLiteDatabase.openDatabase(sourceFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

            ProjectEntity importedProject = readLegacyProject(legacyDb);
            String importName = preferredName;
            if (importName == null || importName.trim().isEmpty()) {
                importName = importedProject.name;
            }
            if (importName == null || importName.trim().isEmpty()) {
                importName = fallbackName;
            }
            if (importName == null || importName.trim().isEmpty()) {
                importName = context.getString(R.string.default_import_project_name);
            }
            if (projectDao.getProjectByName(importName) != null) {
                importName = importName + "_" + System.currentTimeMillis();
            }

            String databaseName = buildDatabaseName(importName);
            resetDatabase(databaseName);
            importedProject.name = importName;
            importedProject.databaseName = databaseName;
            importedProject.databasePath = context.getDatabasePath(databaseName).getAbsolutePath();
            importedProject.imported = true;
            importedProject.updatedAt = System.currentTimeMillis();
            if (importedProject.createdAt == 0L) {
                importedProject.createdAt = importedProject.updatedAt;
            }

            AppDatabase projectDatabase = AppDatabase.getInstance(context, databaseName);
            importIntoProjectDatabase(projectDatabase, legacyDb, importedProject);

            ProjectEntity indexProject = copyForIndex(importedProject);
            long indexId = projectDao.insert(indexProject);
            indexProject.id = indexId;
            return indexProject;
        } finally {
            if (legacyDb != null) {
                legacyDb.close();
            }
        }
    }

    private void importIntoProjectDatabase(AppDatabase projectDatabase, SQLiteDatabase legacyDb, ProjectEntity importedProject) {
        ProjectDao projectDbDao = projectDatabase.projectDao();
        SurveyLineDao surveyLineDao = projectDatabase.surveyLineDao();
        MeasurementPointDao pointDao = projectDatabase.measurementPointDao();
        WaveformDao waveformDao = projectDatabase.waveformDao();
        WorkSetDao workSetDao = projectDatabase.workSetDao();

        projectDatabase.runInTransaction(() -> {
            projectDbDao.deleteAll();
            long internalProjectId = projectDbDao.insert(importedProject);
            importedProject.id = internalProjectId;

            List<SurveyLineEntity> lines = readLegacyLines(legacyDb, internalProjectId);
            if (!lines.isEmpty()) {
                surveyLineDao.insertAll(lines);
            }

            List<MeasurementPointEntity> points = readLegacyPoints(legacyDb);
            if (!points.isEmpty()) {
                pointDao.insertAll(points);
            }

            List<WaveformDataEntity> waveforms = readLegacyWaveforms(legacyDb);
            if (!waveforms.isEmpty()) {
                waveformDao.insertAll(waveforms);
            }

            WorkSetEntity workSet = readLegacyWorkSet(legacyDb);
            if (workSet != null) {
                workSetDao.insert(workSet);
            }
        });
    }

    private ProjectEntity buildProjectRecord(String name, String description, String databaseName, boolean imported) {
        long now = System.currentTimeMillis();
        ProjectEntity project = new ProjectEntity();
        project.name = name;
        project.note = description;
        project.databaseName = databaseName;
        project.databasePath = context.getDatabasePath(databaseName).getAbsolutePath();
        project.imported = imported;
        project.createdAt = now;
        project.updatedAt = now;
        project.lastSyncedAt = 0L;
        project.greateTime = now;
        project.lineNoStart = 1f;
        project.lineNoStep = 1f;
        project.pointNoStart = 1f;
        project.pointNoStep = 1f;
        return project;
    }

    private ProjectEntity copyForIndex(ProjectEntity source) {
        ProjectEntity copy = new ProjectEntity();
        copy.name = source.name;
        copy.note = source.note;
        copy.databaseName = source.databaseName;
        copy.databasePath = source.databasePath;
        copy.imported = source.imported;
        copy.createdAt = source.createdAt;
        copy.updatedAt = source.updatedAt;
        copy.lastSyncedAt = source.lastSyncedAt;
        copy.workConfig = source.workConfig;
        copy.lineNoStart = source.lineNoStart;
        copy.lineNoStep = source.lineNoStep;
        copy.pointNoStart = source.pointNoStart;
        copy.pointNoStep = source.pointNoStep;
        copy.sendCoil_Len = source.sendCoil_Len;
        copy.sendCoil_Width = source.sendCoil_Width;
        copy.sendCoil_Turns = source.sendCoil_Turns;
        copy.recvCoil_Size = source.recvCoil_Size;
        copy.recvCoil_Gain = source.recvCoil_Gain;
        copy.offTime = source.offTime;
        copy.pointLen_D = source.pointLen_D;
        copy.pointLen_R = source.pointLen_R;
        copy.calibrateNo = source.calibrateNo;
        copy.ssid = source.ssid;
        copy.greateTime = source.greateTime;
        return copy;
    }

    private void deleteProjectInternal(ProjectEntity project) {
        projectDao.delete(project);
        if (project.databaseName != null) {
            AppDatabase.closeDatabase(project.databaseName);
            context.deleteDatabase(project.databaseName);
        }
    }

    private void resetDatabase(String databaseName) {
        AppDatabase.closeDatabase(databaseName);
        context.deleteDatabase(databaseName);
    }

    private String buildDatabaseName(String name) {
        String slug = name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_");
        if (slug.isEmpty()) {
            slug = "project";
        }
        return slug + "_" + System.currentTimeMillis() + ".sqlite";
    }

    private File copyUriToTempFile(Uri uri) throws IOException {
        File temp = File.createTempFile("project_import", ".sqlite", context.getCacheDir());
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(temp)) {
            if (inputStream == null) {
                throw new IOException(context.getString(R.string.error_read_import_file));
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
        return temp;
    }

    private File copyAssetToTempFile(String assetPath) throws IOException {
        File temp = File.createTempFile("bundled_project", ".sqlite", context.getCacheDir());
        try (InputStream inputStream = context.getAssets().open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
        return temp;
    }

    private boolean isBundledProjectValid(ProjectEntity project) {
        if (project == null || project.databaseName == null || project.databaseName.trim().isEmpty()) {
            return false;
        }

        File databaseFile = context.getDatabasePath(project.databaseName);
        if (!databaseFile.exists() || databaseFile.length() <= 0L) {
            return false;
        }

        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            return countRows(database, "data_line") > 0
                    && countRows(database, "data_point") > 0
                    && countRows(database, "data_sample") > 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    private int countRows(SQLiteDatabase database, String tableName) {
        try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + tableName, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String guessDisplayName(Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        String displayName = cursor.getString(nameIndex);
                        if (displayName != null) {
                            int dot = displayName.lastIndexOf('.');
                            return dot > 0 ? displayName.substring(0, dot) : displayName;
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return context.getString(R.string.default_import_project_name);
    }

    private ProjectEntity readLegacyProject(SQLiteDatabase database) {
        ProjectEntity project = new ProjectEntity();
        long now = System.currentTimeMillis();
        project.createdAt = now;
        project.updatedAt = now;
        project.greateTime = now;
        try (Cursor cursor = database.rawQuery("SELECT * FROM Data_Project LIMIT 1", null)) {
            if (cursor.moveToFirst()) {
                project.id = getLong(cursor, "ID", 0L);
                project.workConfig = getInt(cursor, "WorkConfig", 0);
                project.lineNoStart = getFloat(cursor, "LineNoStart", 1f);
                project.lineNoStep = getFloat(cursor, "LineNoSetp", getFloat(cursor, "LineNoStep", 1f));
                project.pointNoStart = getFloat(cursor, "PointNoStart", 1f);
                project.pointNoStep = getFloat(cursor, "PointNoStep", 1f);
                project.sendCoil_Len = getFloat(cursor, "SendCoil_Len", 0f);
                project.sendCoil_Width = getFloat(cursor, "SendCoil_Width", 0f);
                project.sendCoil_Turns = getFloat(cursor, "SendCoil_Turns", 0f);
                project.recvCoil_Size = getFloat(cursor, "RecvCoil_Size", 0f);
                project.recvCoil_Gain = getFloat(cursor, "RecvCoil_Gain", 0f);
                project.offTime = getFloat(cursor, "OffTime", 0f);
                project.pointLen_D = getFloat(cursor, "PointLen_D", 0f);
                project.pointLen_R = getFloat(cursor, "PointLen_R", 0f);
                project.greateTime = (long) getFloat(cursor, "CreateTime", (float) now);
                project.note = getString(cursor, "NOTE", "");
                project.calibrateNo = getString(cursor, "CalibrateNo", "");
                project.ssid = getString(cursor, "SSID", "");
                project.name = getString(cursor, "NAME", null);
            }
        } catch (Exception ignored) {
        }
        return project;
    }

    private List<SurveyLineEntity> readLegacyLines(SQLiteDatabase database, long projectId) {
        List<SurveyLineEntity> lines = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT * FROM Data_Line ORDER BY NAME ASC", null)) {
            while (cursor.moveToNext()) {
                SurveyLineEntity line = new SurveyLineEntity();
                line.id = getLong(cursor, "ID", 0L);
                line.name = getFloat(cursor, "NAME", 0f);
                line.type = getInt(cursor, "TYPE", 0);
                line.use = getInt(cursor, "USE", 1);
                line.note = getString(cursor, "NOTE", "");
                line.projectId = projectId;
                line.createdAt = System.currentTimeMillis();
                line.updatedAt = line.createdAt;
                lines.add(line);
            }
        } catch (Exception ignored) {
        }
        return lines;
    }

    private List<MeasurementPointEntity> readLegacyPoints(SQLiteDatabase database) {
        List<MeasurementPointEntity> points = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT * FROM Data_Point ORDER BY Data_LineID ASC, NAME ASC", null)) {
            while (cursor.moveToNext()) {
                MeasurementPointEntity point = new MeasurementPointEntity();
                point.id = getLong(cursor, "ID", 0L);
                point.name = getFloat(cursor, "NAME", 0f);
                point.use = getInt(cursor, "USE", 1);
                point.note = getString(cursor, "NOTE", "");
                point.type = getInt(cursor, "TYPE", 0);
                point.dataLineId = getLong(cursor, "Data_LineID", 0L);
                point.status = 2;
                point.isQualified = point.use == 1;
                point.isSynced = false;
                point.collectionTime = System.currentTimeMillis();
                point.createdAt = point.collectionTime;
                point.updatedAt = point.collectionTime;
                points.add(point);
            }
        } catch (Exception ignored) {
        }
        return points;
    }

    private List<WaveformDataEntity> readLegacyWaveforms(SQLiteDatabase database) {
        List<WaveformDataEntity> waveforms = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT * FROM Data_Sample ORDER BY Data_PointID ASC, ID ASC", null)) {
            while (cursor.moveToNext()) {
                WaveformDataEntity waveform = new WaveformDataEntity();
                waveform.id = getLong(cursor, "ID", 0L);
                waveform.startTime = getLong(cursor, "StartTime", System.currentTimeMillis());
                waveform.deviceType = getInt(cursor, "DeviceType", 0);
                waveform.type = getInt(cursor, "TYPE", 0);
                waveform.period = getInt(cursor, "PERIOD", 1);
                waveform.dataRecv = getBlob(cursor, "DATA_RECV");
                waveform.dataRecvPos = getBlob(cursor, "DATA_RECV_POS");
                waveform.dataRecvLen = getBlob(cursor, "DATA_RECV_LEN");
                waveform.dataSend = getBlob(cursor, "DATA_SEND");
                waveform.dataSoff = getBlob(cursor, "DATA_SOFF");
                waveform.sendFs = getFloat(cursor, "SendFs", 0f);
                waveform.simpleSendFs = getFloat(cursor, "SampleSendFs", getFloat(cursor, "SimpleSendFs", 0f));
                waveform.simpleOffFs = getFloat(cursor, "SampleOffFs", getFloat(cursor, "SimpleOffFs", 0f));
                waveform.recvFs = getFloat(cursor, "RecvFs", 0f);
                waveform.use = getInt(cursor, "USE", 1);
                waveform.note = getString(cursor, "NOTE", "");
                waveform.dataPointId = getLong(cursor, "Data_PointID", 0L);
                waveform.createdAt = System.currentTimeMillis();
                waveforms.add(waveform);
            }
        } catch (Exception ignored) {
        }
        return waveforms;
    }

    private WorkSetEntity readLegacyWorkSet(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery("SELECT * FROM Data_WorkSet LIMIT 1", null)) {
            if (cursor.moveToFirst()) {
                WorkSetEntity entity = new WorkSetEntity();
                entity.id = getLong(cursor, "ID", 0L);
                entity.workConfig = getInt(cursor, "WorkConfig", 0);
                entity.sendCoil_Len = getFloat(cursor, "SendCoil_Len", 0f);
                entity.sendCoil_Width = getFloat(cursor, "SendCoil_Width", 0f);
                entity.sendCoil_Turns = getFloat(cursor, "SendCoil_Turns", 0f);
                entity.recvCoil_Size = getFloat(cursor, "RecvCoil_Size", 0f);
                entity.recvCoil_Gain = getFloat(cursor, "RecvCoil_Gain", 0f);
                entity.createdAt = System.currentTimeMillis();
                entity.updatedAt = entity.createdAt;
                return entity;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int getColumnIndex(Cursor cursor, String columnName) {
        return cursor.getColumnIndex(columnName);
    }

    private static String getString(Cursor cursor, String columnName, String fallback) {
        int index = getColumnIndex(cursor, columnName);
        return index >= 0 && !cursor.isNull(index) ? cursor.getString(index) : fallback;
    }

    private static int getInt(Cursor cursor, String columnName, int fallback) {
        int index = getColumnIndex(cursor, columnName);
        return index >= 0 && !cursor.isNull(index) ? cursor.getInt(index) : fallback;
    }

    private static long getLong(Cursor cursor, String columnName, long fallback) {
        int index = getColumnIndex(cursor, columnName);
        return index >= 0 && !cursor.isNull(index) ? cursor.getLong(index) : fallback;
    }

    private static float getFloat(Cursor cursor, String columnName, float fallback) {
        int index = getColumnIndex(cursor, columnName);
        return index >= 0 && !cursor.isNull(index) ? cursor.getFloat(index) : fallback;
    }

    private static byte[] getBlob(Cursor cursor, String columnName) {
        int index = getColumnIndex(cursor, columnName);
        return index >= 0 && !cursor.isNull(index) ? cursor.getBlob(index) : null;
    }
}

