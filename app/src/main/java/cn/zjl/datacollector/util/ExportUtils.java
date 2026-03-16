package cn.zjl.datacollector.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 数据导出工具类
 */
public class ExportUtils {
    private static final String TAG = "ExportUtils";
    private static final String EXPORT_DIR = "DataCollector/Export";
    
    /**
     * 导出工程数据库到外部存储
     */
    public interface ExportCallback {
        void onSuccess(File exportedFile);
        void onError(String error);
    }
    
    public static void exportDatabase(Context context, String databasePath, ExportCallback callback) {
        new Thread(() -> {
            try {
                // 创建导出目录
                File exportDir = new File(Environment.getExternalStorageDirectory(), EXPORT_DIR);
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }
                
                // 生成文件名
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                String timestamp = sdf.format(new Date());
                String dbName = new File(databasePath).getName();
                String exportFileName = dbName.replace(".db", "") + "_" + timestamp + ".db";
                
                File exportFile = new File(exportDir, exportFileName);
                
                // 复制数据库文件
                copyDatabaseFile(databasePath, exportFile.getAbsolutePath());
                
                Log.i(TAG, "Database exported to: " + exportFile.getAbsolutePath());
                
                if (callback != null) {
                    callback.onSuccess(exportFile);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                if (callback != null) {
                    callback.onError("导出失败：" + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 复制数据库文件
     */
    private static void copyDatabaseFile(String sourcePath, String destPath) throws IOException {
        FileInputStream fis = new FileInputStream(sourcePath);
        FileOutputStream fos = new FileOutputStream(destPath);
        
        FileChannel srcChannel = fis.getChannel();
        FileChannel destChannel = fos.getChannel();
        
        destChannel.transferFrom(srcChannel, 0, srcChannel.size());
        
        srcChannel.close();
        destChannel.close();
        fis.close();
        fos.close();
    }
    
    /**
     * 读取数据库中的数据（用于查询或验证）
     */
    public static Cursor queryDatabase(String databasePath, String tableName, String[] columns, 
                                       String selection, String[] selectionArgs, 
                                       String orderBy) {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(databasePath, null, 
                SQLiteDatabase.OPEN_READONLY);
            
            Cursor cursor = db.query(tableName, columns, selection, selectionArgs, 
                null, null, orderBy);
            
            return cursor;
        } catch (Exception e) {
            Log.e(TAG, "Query failed", e);
            return null;
        }
    }
}
