package cn.zjl.datacollector.ui.common;

/**
 * 阅读提示：界面通用协作代码：封装多个页面复用的导出、选择或提示流程。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.util.ExportUtils;

/**
 * 统一处理工程数据库导出、分享与导出结果弹窗，避免多个页面重复维护同一套逻辑。
 */
public class ProjectExportCoordinator {

    private final AppCompatActivity activity;

    public ProjectExportCoordinator(AppCompatActivity activity) {
        this.activity = activity;
    }

    @Nullable
    public String resolveDatabasePath(@Nullable ProjectEntity project, @Nullable String databaseName) {
        if (project != null
                && project.getDatabasePath() != null
                && !project.getDatabasePath().trim().isEmpty()) {
            return project.getDatabasePath();
        }
        if (databaseName == null || databaseName.trim().isEmpty()) {
            return null;
        }
        return activity.getDatabasePath(databaseName).getAbsolutePath();
    }

    public void exportDatabase(@Nullable String databasePath) {
        if (databasePath == null || databasePath.trim().isEmpty()) {
            Toast.makeText(activity, R.string.toast_missing_project_database_path, Toast.LENGTH_SHORT).show();
            return;
        }

        File databaseFile = new File(databasePath);
        if (!databaseFile.exists()) {
            Toast.makeText(activity, R.string.toast_missing_project_database_path, Toast.LENGTH_SHORT).show();
            return;
        }

        ExportUtils.exportDatabase(activity, databasePath, new ExportUtils.ExportCallback() {
            @Override
            public void onSuccess(File exportedFile) {
                activity.runOnUiThread(() -> showExportSuccessDialog(exportedFile));
            }

            @Override
            public void onError(String error) {
                activity.runOnUiThread(() -> Toast.makeText(activity, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * 导出完成后提供常用后续动作，减少用户继续找文件的操作成本。
     */
    private void showExportSuccessDialog(File exportedFile) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_export_success)
                .setMessage(exportedFile.getAbsolutePath())
                .setNegativeButton(R.string.action_open_export_folder, (dialog, which) -> {
                    if (!ExportUtils.openContainingDirectory(activity, exportedFile)) {
                        Toast.makeText(
                                activity,
                                activity.getString(R.string.toast_open_export_folder_failed, exportedFile.getParent()),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton(R.string.action_share_file, (dialog, which) -> {
                    if (!ExportUtils.shareFile(activity, exportedFile)) {
                        Toast.makeText(activity, R.string.toast_share_file_failed, Toast.LENGTH_LONG).show();
                    }
                })
                .setPositiveButton(R.string.action_confirm, null)
                .show();
    }
}
