package cn.zjl.datacollector.util;

import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ExportUtils {

    private ExportUtils() {
    }

    public interface ExportCallback {
        void onSuccess(File exportedFile);

        void onError(String error);
    }

    public static void exportDatabase(Context context, String databasePath, ExportCallback callback) {
        new Thread(() -> {
            try {
                File documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (documentsDir == null) {
                    throw new IOException("外部文档目录不可用");
                }
                File exportDir = new File(documentsDir, "Export");
                if (!exportDir.exists() && !exportDir.mkdirs()) {
                    throw new IOException("无法创建导出目录");
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String dbName = new File(databasePath).getName();
                String exportName = dbName.replace(".sqlite", "").replace(".db", "") + "_" + timestamp + ".sqlite";
                File exportFile = new File(exportDir, exportName);
                copy(databasePath, exportFile.getAbsolutePath());
                callback.onSuccess(exportFile);
            } catch (Exception e) {
                callback.onError("导出失败: " + e.getMessage());
            }
        }).start();
    }

    public static boolean shareFile(Context context, File file) {
        if (context == null || file == null || !file.exists()) {
            return false;
        }

        Uri uri = getContentUri(context, file);
        if (uri == null) {
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return startActivitySafely(context, Intent.createChooser(intent, context.getString(cn.zjl.datacollector.R.string.action_share_file)));
    }

    public static boolean openContainingDirectory(Context context, File file) {
        if (context == null || file == null) {
            return false;
        }

        File directory = file.isDirectory() ? file : file.getParentFile();
        if (directory == null || !directory.exists()) {
            return false;
        }

        Uri directoryUri = getContentUri(context, directory);
        if (directoryUri == null) {
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(directoryUri, "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_TITLE, directory.getName());
        return startActivitySafely(context, intent);
    }

    public static Cursor queryDatabase(String databasePath,
                                       String tableName,
                                       String[] columns,
                                       String selection,
                                       String[] selectionArgs,
                                       String orderBy) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READONLY);
        return db.query(tableName, columns, selection, selectionArgs, null, null, orderBy);
    }

    private static void copy(String sourcePath, String destPath) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(sourcePath);
             FileOutputStream outputStream = new FileOutputStream(destPath);
             FileChannel srcChannel = inputStream.getChannel();
             FileChannel dstChannel = outputStream.getChannel()) {
            dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
        }
    }

    private static Uri getContentUri(Context context, File file) {
        try {
            return FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    file);
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }

    private static boolean startActivitySafely(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignore) {
            return false;
        }
    }
}
