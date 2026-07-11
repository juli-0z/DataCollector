package cn.zjl.datacollector.ui.upload;

/**
 * 阅读提示：上传任务中心界面代码：负责工程选择、失败筛选、手动上传和上传记录展示。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 上传任务中心的本地历史记录存储。
 *
 * <p>该类只负责把最近几次手动上传任务写入 SharedPreferences，便于用户返回上传页面后
 * 仍能查看上传结果摘要；真实的测点同步状态仍以工程数据库中的测点记录为准。</p>
 */
public final class UploadTaskCenterStore {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    private static final String PREFS_NAME = "upload_task_center";
    private static final String KEY_HISTORY = "upload_history_v1";
    /** 只保留最近若干次上传记录，避免 SharedPreferences 随长期联调无限增长。 */
    private static final int MAX_HISTORY_SIZE = 12;

    /**
     * 单个工程在一次上传任务中的结果摘要。
     */
    public static final class ProjectRecord {
        public String projectName = "";
        public String databaseName = "";
        public int pendingPointCount;
        public int syncedCount;
        public boolean success;
        public String message = "";
    }

    /**
     * 一次手动上传任务的总记录。
     *
     * <p>任务记录用于页面展示，不参与后端同步判定；每个工程的真实成功/失败状态由
     * ProjectSyncExecutor 回写到本地数据库。</p>
     */
    public static final class TaskRecord {
        public String taskId = "";
        public long startedAt;
        public long finishedAt;
        public int projectCount;
        public int totalPendingPoints;
        public int syncedPoints;
        public String status = STATUS_SUCCESS;
        public String summary = "";
        public List<ProjectRecord> projectRecords = new ArrayList<>();
    }

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<TaskRecord>>() {
    }.getType();

    public UploadTaskCenterStore(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public List<TaskRecord> loadHistory() {
        String raw = preferences.getString(KEY_HISTORY, "");
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<TaskRecord> records = gson.fromJson(raw, listType);
            if (records == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(records);
        } catch (Exception ignored) {
            // 历史记录损坏时直接返回空列表，避免影响上传页面正常打开。
            return new ArrayList<>();
        }
    }

    public void saveRecord(@NonNull TaskRecord record) {
        List<TaskRecord> records = loadHistory();
        records.add(0, record);
        // 新记录放在最前面，超过上限时丢弃最旧记录。
        while (records.size() > MAX_HISTORY_SIZE) {
            records.remove(records.size() - 1);
        }
        persist(records);
    }

    public void clearHistory() {
        persist(Collections.emptyList());
    }

    private void persist(@NonNull List<TaskRecord> records) {
        preferences.edit().putString(KEY_HISTORY, gson.toJson(records, listType)).apply();
    }
}
