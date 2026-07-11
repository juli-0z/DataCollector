package cn.zjl.datacollector.ui.collection.workflow;

/**
 * 阅读提示：采集业务流程模块代码：负责连接、质检、保存和参数沿用等采集前后流程。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.zjl.datacollector.R;

/**
 * 封装一次采集结果的基础质检结论，供保存前弹窗展示和保存备注回写复用。
 */
public class CollectionQualityCheckResult {

    private final boolean suggestSave;
    private final boolean blockSaveOnFailure;
    private final int recvSampleCount;
    private final int sendSampleCount;
    private final int offSampleCount;
    private final boolean monitorAvailable;
    private final List<String> issues;

    public CollectionQualityCheckResult(boolean suggestSave,
                                        boolean blockSaveOnFailure,
                                        int recvSampleCount,
                                        int sendSampleCount,
                                        int offSampleCount,
                                        boolean monitorAvailable,
                                        @NonNull List<String> issues) {
        this.suggestSave = suggestSave;
        this.blockSaveOnFailure = blockSaveOnFailure;
        this.recvSampleCount = Math.max(0, recvSampleCount);
        this.sendSampleCount = Math.max(0, sendSampleCount);
        this.offSampleCount = Math.max(0, offSampleCount);
        this.monitorAvailable = monitorAvailable;
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public boolean shouldSuggestSave() {
        return suggestSave;
    }

    public boolean isSaveBlockedByPolicy() {
        return !suggestSave && blockSaveOnFailure;
    }

    @NonNull
    public List<String> getIssues() {
        return issues;
    }

    @NonNull
    public String buildDialogMessage(@NonNull Context context) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.judge_auto_result_prefix, getVerdictText(context)));
        builder.append('\n');
        builder.append(buildWaveformSummary(context));
        if (issues.isEmpty()) {
            builder.append("\n\n");
            builder.append(context.getString(R.string.judge_auto_issue_none));
        } else {
            builder.append("\n\n");
            builder.append(context.getString(R.string.judge_auto_issue_title));
            for (int i = 0; i < issues.size(); i++) {
                builder.append('\n')
                        .append(i + 1)
                        .append(". ")
                        .append(issues.get(i));
            }
        }
        builder.append("\n\n");
        if (!suggestSave) {
            builder.append(context.getString(isSaveBlockedByPolicy()
                    ? R.string.judge_policy_block_message
                    : R.string.judge_policy_allow_override_message));
            builder.append("\n\n");
        }
        builder.append(context.getString(isSaveBlockedByPolicy()
                ? R.string.judge_manual_message_blocked
                : R.string.judge_manual_message_with_auto));
        return builder.toString();
    }

    @NonNull
    public String buildJudgeNote(@NonNull Context context, boolean manuallySaved) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.judge_auto_result_prefix, getVerdictText(context)));
        if (manuallySaved) {
            builder.append("; ");
            builder.append(context.getString(R.string.judge_note_manual_saved));
        }
        builder.append("; ");
        builder.append(buildWaveformSummary(context));
        if (!issues.isEmpty()) {
            builder.append("; ");
            builder.append(context.getString(R.string.judge_note_issue_prefix));
            builder.append(joinIssues(" / "));
        }
        return builder.toString();
    }

    @NonNull
    private String buildWaveformSummary(@NonNull Context context) {
        return context.getString(
                R.string.judge_auto_waveform_summary,
                recvSampleCount,
                sendSampleCount,
                offSampleCount,
                context.getString(monitorAvailable
                        ? R.string.judge_monitor_status_received
                        : R.string.judge_monitor_status_missing));
    }

    @NonNull
    private String getVerdictText(@NonNull Context context) {
        return context.getString(suggestSave
                ? R.string.judge_auto_result_save
                : R.string.judge_auto_result_recollect);
    }

    @NonNull
    private String joinIssues(@NonNull String separator) {
        if (issues.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0) {
                builder.append(separator);
            }
            builder.append(issues.get(i));
        }
        return builder.toString();
    }
}
