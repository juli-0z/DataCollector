package cn.zjl.datacollector.ui.collection.selection;

/**
 * 阅读提示：工程层级选择模块代码：负责工程、测线、测点树形浏览和当前采集目标切换。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import androidx.annotation.Nullable;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.ProjectEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.ui.collection.panel.CollectionFormHelper;
import cn.zjl.datacollector.ui.collection.panel.CollectionUiRenderer;
import cn.zjl.datacollector.ui.collection.screen.CollectionViewModel;
import cn.zjl.datacollector.ui.playback.PointListAdapter;
import cn.zjl.datacollector.ui.playback.PointListItem;

/**
 * 统一处理树列表选择、默认定位、当前位置同步和点号跳转，
 * 让 Activity 只负责把用户操作和 ViewModel 状态接进来。
 */
public class CollectionSelectionCoordinator {

    private final Context context;
    private final CollectionTreeHelper treeHelper;
    private final PointListAdapter pointAdapter;
    private final CollectionFormHelper formHelper;
    private final CollectionUiRenderer uiRenderer;

    @Nullable
    private ProjectEntity currentProject;
    @Nullable
    private DataRepository.ProjectTreeSummary currentTree;

    public CollectionSelectionCoordinator(
            Context context,
            CollectionTreeHelper treeHelper,
            PointListAdapter pointAdapter,
            CollectionFormHelper formHelper,
            CollectionUiRenderer uiRenderer) {
        this.context = context;
        this.treeHelper = treeHelper;
        this.pointAdapter = pointAdapter;
        this.formHelper = formHelper;
        this.uiRenderer = uiRenderer;
    }

    public void setCurrentProject(@Nullable ProjectEntity currentProject) {
        this.currentProject = currentProject;
    }

    public void setCurrentTree(@Nullable DataRepository.ProjectTreeSummary currentTree) {
        this.currentTree = currentTree;
    }

    public void handleTreeItemClick(@Nullable PointListItem item, ActionHandler actionHandler) {
        if (item == null) {
            return;
        }

        if (item.hasChildren) {
            item.expanded = !item.expanded;
            pointAdapter.refresh();
        }

        switch (item.type) {
            case PointListItem.TYPE_PROJECT:
                actionHandler.selectProject(item);
                break;
            case PointListItem.TYPE_LINE:
                actionHandler.selectLine(item);
                break;
            case PointListItem.TYPE_POINT:
                actionHandler.selectPoint(item);
                break;
            case PointListItem.TYPE_SESSION:
                actionHandler.selectSession(item);
                break;
            default:
                break;
        }
    }

    public void applySelectionState(@Nullable CollectionViewModel.SelectionState state) {
        if (state == null) {
            uiRenderer.renderBreadcrumb(null);
            return;
        }

        switch (state.type) {
            case PointListItem.TYPE_PROJECT:
                pointAdapter.setSelectedProject(state.nodeId);
                break;
            case PointListItem.TYPE_LINE:
                pointAdapter.setSelectedLine(state.nodeId);
                break;
            case PointListItem.TYPE_POINT:
                pointAdapter.setSelectedPoint(state.pointId);
                break;
            case PointListItem.TYPE_SESSION:
                pointAdapter.setSelectedSession(state.pointId, state.sessionIndex);
                break;
            default:
                break;
        }

        String lineTitle = resolveLineTitle(state);
        if (lineTitle != null) {
            formHelper.updateLineValue(lineTitle);
        }
        uiRenderer.renderBreadcrumb(state.breadcrumb);
    }

    public void selectInitialLine(ActionHandler actionHandler) {
        PointListItem firstLineItem = treeHelper.findFirstLineItem(currentTree);
        if (firstLineItem != null) {
            actionHandler.selectLine(firstLineItem);
        }
    }

    public String buildLineBreadcrumb(String lineTitle) {
        String projectName = currentProject != null ? currentProject.getName() : "";
        return context.getString(
                R.string.tree_breadcrumb_format,
                projectName,
                lineTitle,
                context.getString(R.string.tree_all_points),
                context.getString(R.string.tree_all_collections));
    }

    public void advanceToNextPoint() {
        float currentPoint = formHelper.readPointNumber(0f);
        float step = currentProject != null && currentProject.getPointNoStep() > 0f ? currentProject.getPointNoStep() : 1f;
        formHelper.updatePointNumber(currentPoint + step);
    }

    @Nullable
    private String resolveLineTitle(@Nullable CollectionViewModel.SelectionState state) {
        if (state == null || currentTree == null || currentTree.lines == null) {
            return null;
        }

        if (state.type == PointListItem.TYPE_LINE) {
            for (DataRepository.LineTreeSummary lineTree : currentTree.lines) {
                if (lineTree.line.getId() == state.nodeId) {
                    return treeHelper.buildLineTitle(lineTree.line.getName());
                }
            }
            return null;
        }

        if (state.type == PointListItem.TYPE_POINT || state.type == PointListItem.TYPE_SESSION) {
            for (DataRepository.LineTreeSummary lineTree : currentTree.lines) {
                for (DataRepository.PointTreeSummary pointTree : lineTree.points) {
                    if (pointTree.point.getId() == state.pointId) {
                        return treeHelper.buildLineTitle(lineTree.line.getName());
                    }
                }
            }
        }

        return null;
    }

    public interface ActionHandler {
        void selectProject(PointListItem item);

        void selectLine(PointListItem item);

        void selectPoint(PointListItem item);

        void selectSession(PointListItem item);
    }
}
