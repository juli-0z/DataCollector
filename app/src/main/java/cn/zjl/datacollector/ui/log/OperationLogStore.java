package cn.zjl.datacollector.ui.log;

/**
 * 阅读提示：操作日志中心代码：记录和展示关键操作、异常和同步过程，方便现场追踪。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class OperationLogStore {

    public static final String CATEGORY_COLLECTION = "COLLECTION";
    public static final String CATEGORY_DEVICE = "DEVICE";
    public static final String CATEGORY_TEMPLATE = "TEMPLATE";
    public static final String CATEGORY_UPLOAD = "UPLOAD";
    public static final String CATEGORY_PROJECT = "PROJECT";
    public static final String CATEGORY_SETTINGS = "SETTINGS";

    private static final String PREFS_NAME = "operation_log_center";
    private static final String KEY_HISTORY = "operation_log_history_v1";
    private static final int MAX_HISTORY_SIZE = 120;
    private static final long DUPLICATE_WINDOW_MS = 1500L;

    public static final class LogRecord {
        public String id = "";      // UUID 唯一标识
        public long createdAt;      // 时间戳
        public String category = CATEGORY_COLLECTION;       // 分类
        public String title = "";       // 标题
        public String detail = "";      // 详情（可选）
        public String databaseName = "";     // 关联工程库名（可选）
    }

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<LogRecord>>() {
    }.getType();

    public OperationLogStore(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public synchronized List<LogRecord> loadHistory() {
        String raw = preferences.getString(KEY_HISTORY, "");
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<LogRecord> records = gson.fromJson(raw, listType);
            if (records == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(records);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public synchronized void record(@NonNull String category,
                                    @NonNull String title,
                                    @Nullable String detail,
                                    @Nullable String databaseName) {
        String safeTitle = safeText(title);
        if (safeTitle.isEmpty()) {
            return;
        }

        List<LogRecord> records = loadHistory();
        LogRecord record = new LogRecord();
        record.id = UUID.randomUUID().toString();
        record.createdAt = System.currentTimeMillis();
        record.category = safeText(category);
        record.title = safeTitle;
        record.detail = safeText(detail);
        record.databaseName = safeText(databaseName);

        if (!records.isEmpty() && isDuplicate(records.get(0), record)) {
            return;
        }

        records.add(0, record);
        while (records.size() > MAX_HISTORY_SIZE) {
            records.remove(records.size() - 1);
        }
        persist(records);
    }
    public synchronized void deleteRecord(@NonNull LogRecord record) {
        List<LogRecord> records = loadHistory();
        records.removeIf(r -> r.id != null && r.id.equals(record.id));
        persist(records);
    }
    public synchronized void clearHistory() {
        persist(Collections.emptyList());
    }

    private boolean isDuplicate(@Nullable LogRecord latest, @NonNull LogRecord current) {
        if (latest == null) {
            return false;
        }
        if (current.createdAt - latest.createdAt > DUPLICATE_WINDOW_MS) {
            return false;
        }
        return safeText(latest.category).equals(current.category)
                && safeText(latest.title).equals(current.title)
                && safeText(latest.detail).equals(current.detail)
                && safeText(latest.databaseName).equals(current.databaseName);
    }

    private void persist(@NonNull List<LogRecord> records) {
        preferences.edit().putString(KEY_HISTORY, gson.toJson(records, listType)).apply();
    }

    @NonNull
    private String safeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
