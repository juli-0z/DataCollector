package cn.zjl.datacollector.ui.collection.selection;

/**
 * 阅读提示：工程层级选择模块代码：负责工程、测线、测点树形浏览和当前采集目标切换。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.SurveyLineEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.ui.collection.chart.CollectionChartStateFactory;
import cn.zjl.datacollector.ui.collection.screen.CollectionViewModel;
import cn.zjl.datacollector.ui.playback.PointListItem;

/**
 * 负责处理测线、测点、采样记录的选择流程，以及选择后相关界面状态的数据组装。
 */
public class CollectionSelectionUseCase {

    private final Context context;
    private final CollectionChartStateFactory chartStateFactory;

    public CollectionSelectionUseCase(Context context, CollectionChartStateFactory chartStateFactory) {
        this.context = context;
        this.chartStateFactory = chartStateFactory;
    }

    public void selectProject(@NonNull DataRepository dataRepository,
                              @NonNull PointListItem item,
                              @NonNull Callback callback) {
        callback.onSelectionState(createSelectionState(item.type, item.nodeId, -1L, -1, item.breadcrumb));
        callback.onMonitorChanged(null);
        dataRepository.getAllWaveformsByProject(
                waveforms -> callback.onChartState(chartStateFactory.buildAggregateChartState(waveforms)));
    }

    public void selectLine(@NonNull DataRepository dataRepository,
                           @NonNull PointListItem item,
                           @NonNull Callback callback) {
        SurveyLineEntity line = new SurveyLineEntity();
        line.setId(item.nodeId);
        line.setName(parseLineName(item.title));
        callback.onCurrentLineChanged(line);
        callback.onSelectionState(createSelectionState(item.type, item.nodeId, -1L, -1, item.breadcrumb));
        callback.onMonitorChanged(null);

        dataRepository.getAllWaveformsByLine(
                item.nodeId,
                waveforms -> callback.onChartState(chartStateFactory.buildAggregateChartState(waveforms)));
    }

    public void selectPoint(@NonNull DataRepository dataRepository,
                            @NonNull PointListItem item,
                            boolean fromSave,
                            @NonNull Callback callback) {
        selectPointById(dataRepository, item.pointId, item.breadcrumb, fromSave, callback);
    }

    public void selectSession(@NonNull DataRepository dataRepository,
                              @NonNull PointListItem item,
                              @NonNull Callback callback) {
        callback.onSelectionState(createSelectionState(
                item.type,
                item.nodeId,
                item.pointId,
                item.sessionIndex,
                item.breadcrumb));

        dataRepository.getAllDataByPoint(item.pointId, pointData -> {
            if (pointData == null || pointData.point == null) {
                return;
            }

            callback.onCurrentLineChanged(ensureCurrentLine(pointData.point.getDataLineId()));
            callback.onFormState(createFormState(pointData.point, pointData.parameters));
            callback.onMonitorChanged(findLatestMonitor(pointData.monitors));
            callback.onChartState(
                    chartStateFactory.buildSessionChartState(pointData.waveforms, item.sessionIndex));
        });
    }

    public void selectPointById(@NonNull DataRepository dataRepository,
                                long pointId,
                                @Nullable String breadcrumb,
                                boolean fromSave,
                                @NonNull Callback callback) {
        dataRepository.getAllDataByPoint(pointId, pointData -> {
            if (pointData == null || pointData.point == null) {
                return;
            }

            callback.onCurrentLineChanged(ensureCurrentLine(pointData.point.getDataLineId()));
            callback.onSelectionState(createSelectionState(
                    PointListItem.TYPE_POINT,
                    pointData.point.getId(),
                    pointData.point.getId(),
                    -1,
                    breadcrumb != null ? breadcrumb : buildPointBreadcrumb(pointData.point.getName())));
            callback.onFormState(createFormState(pointData.point, pointData.parameters));
            callback.onMonitorChanged(findLatestMonitor(pointData.monitors));
            callback.onChartState(chartStateFactory.buildAggregateChartState(pointData.waveforms));

            if (fromSave) {
                callback.onSelectionFromSaveConsumed();
            }
        });
    }

    private CollectionViewModel.SelectionState createSelectionState(
            int type,
            long nodeId,
            long pointId,
            int sessionIndex,
            @Nullable String breadcrumb) {
        CollectionViewModel.SelectionState state = new CollectionViewModel.SelectionState();
        state.type = type;
        state.nodeId = nodeId;
        state.pointId = pointId;
        state.sessionIndex = sessionIndex;
        state.breadcrumb = breadcrumb;
        return state;
    }

    private CollectionViewModel.FormState createFormState(
            @Nullable MeasurementPointEntity point,
            @Nullable CollectionParameterEntity parameters) {
        CollectionViewModel.FormState formState = new CollectionViewModel.FormState();
        formState.point = point;
        formState.parameters = parameters;
        return formState;
    }

    @Nullable
    private DeviceMonitorEntity findLatestMonitor(@Nullable List<DeviceMonitorEntity> monitors) {
        if (monitors == null || monitors.isEmpty()) {
            return null;
        }
        DeviceMonitorEntity latest = monitors.get(0);
        for (int i = 1; i < monitors.size(); i++) {
            if (monitors.get(i).getTimestamp() >= latest.getTimestamp()) {
                latest = monitors.get(i);
            }
        }
        return latest;
    }

    @NonNull
    private SurveyLineEntity ensureCurrentLine(long lineId) {
        SurveyLineEntity line = new SurveyLineEntity();
        line.setId(lineId);
        return line;
    }

    private String buildPointBreadcrumb(float pointName) {
        return context.getString(R.string.tree_point_title, (int) pointName);
    }

    private float parseLineName(@Nullable String title) {
        if (title == null) {
            return 0f;
        }
        String digits = title.replaceAll("[^0-9.\\-]", "");
        if (digits.isEmpty()) {
            return 0f;
        }
        try {
            return Float.parseFloat(digits);
        } catch (NumberFormatException ignore) {
            return 0f;
        }
    }

    public interface Callback {
        void onSelectionState(CollectionViewModel.SelectionState state);

        void onCurrentLineChanged(SurveyLineEntity currentLine);

        void onFormState(CollectionViewModel.FormState formState);

        void onMonitorChanged(@Nullable DeviceMonitorEntity monitor);

        void onChartState(CollectionViewModel.ChartDisplayState chartState);

        void onSelectionFromSaveConsumed();
    }
}
