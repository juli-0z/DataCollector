package cn.zjl.datacollector.ui.collection.selection;

/**
 * 阅读提示：工程层级选择模块代码：负责工程、测线、测点树形浏览和当前采集目标切换。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.repository.DataRepository;
import cn.zjl.datacollector.ui.playback.PointListItem;

/**
 * 负责把工程树摘要转换成测点树列表数据。
 * 当前列表顶层直接显示测线，不再显示工程名这一层。
 */
public class CollectionTreeHelper {

    private final Context context;
    private final SimpleDateFormat sessionTimeFormat = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());

    public CollectionTreeHelper(Context context) {
        this.context = context;
    }

    public List<PointListItem> buildTreeItems(DataRepository.ProjectTreeSummary tree) {
        List<PointListItem> roots = new ArrayList<>();
        if (tree == null) {
            return roots;
        }

        String projectName = resolveProjectName(tree);
        List<DataRepository.LineTreeSummary> lineTrees =
                tree.lines == null ? new ArrayList<>() : tree.lines;
        PointListItem projectItem = PointListItem.createProject(
                tree.project != null ? tree.project.getId() : 0L,
                projectName,
                lineTrees.size());
        projectItem.subtitle = context.getString(R.string.tree_project_subtitle_format, lineTrees.size());
        projectItem.meta = context.getString(
                R.string.tree_project_meta_format,
                tree.pointCount,
                countCompletedPointsInLines(lineTrees));
        projectItem.breadcrumb = buildBreadcrumbPath(
                projectName,
                context.getString(R.string.tree_all_lines),
                context.getString(R.string.tree_all_points),
                context.getString(R.string.tree_all_collections));

        for (DataRepository.LineTreeSummary lineTree : lineTrees) {
            int pointCount = lineTree.points == null ? 0 : lineTree.points.size();
            PointListItem lineItem = PointListItem.createLine(
                    lineTree.line.getId(),
                    lineTree.line.getName(),
                    pointCount);
            lineItem.depth = 1;
            lineItem.title = buildLineTitle(lineTree.line.getName());
            lineItem.subtitle = context.getString(R.string.tree_line_subtitle_format, pointCount);
            lineItem.meta = context.getString(
                    R.string.tree_line_meta_format,
                    countCompletedPointsInPoints(lineTree.points),
                    pointCount);
            lineItem.breadcrumb = buildBreadcrumbPath(
                    projectName,
                    lineItem.title,
                    context.getString(R.string.tree_all_points),
                    context.getString(R.string.tree_all_collections));

            List<DataRepository.PointTreeSummary> pointTrees =
                    lineTree.points == null ? new ArrayList<>() : lineTree.points;
            for (DataRepository.PointTreeSummary pointTree : pointTrees) {
                int sessionCount = pointTree.sessions == null ? 0 : pointTree.sessions.size();
                boolean completed = isPointCompleted(pointTree.point);
                PointListItem pointItem = PointListItem.createPoint(
                        pointTree.point.getId(),
                        pointTree.point.getName(),
                        sessionCount,
                        completed);
                pointItem.depth = 2;
                pointItem.title = buildPointTitle(pointTree.point.getName());
                pointItem.subtitle = context.getString(R.string.tree_point_subtitle);
                pointItem.meta = context.getString(
                        completed
                                ? R.string.tree_point_meta_saved_format
                                : R.string.tree_point_meta_pending_format,
                        sessionCount);
                pointItem.breadcrumb = buildBreadcrumbPath(
                        projectName,
                        lineItem.title,
                        pointItem.title,
                        context.getString(R.string.tree_all_collections));

                List<DataRepository.CollectionSessionSummary> sessions =
                        pointTree.sessions == null ? new ArrayList<>() : pointTree.sessions;
                for (DataRepository.CollectionSessionSummary session : sessions) {
                    PointListItem sessionItem = PointListItem.createSession(
                            pointTree.point.getId(),
                            session.sessionKey,
                            session.collectionIndex,
                            buildSessionSubtitle(session.startTime, session.waveformCount));
                    sessionItem.depth = 3;
                    sessionItem.title = context.getString(R.string.tree_session_title, session.collectionIndex);
                    sessionItem.meta = context.getString(R.string.tree_session_meta);
                    sessionItem.breadcrumb = buildBreadcrumbPath(
                            projectName,
                            lineItem.title,
                            pointItem.title,
                            context.getString(R.string.tree_session_suffix, session.collectionIndex));
                    pointItem.children.add(sessionItem);
                }

                lineItem.children.add(pointItem);
            }

            projectItem.children.add(lineItem);
        }
        roots.add(projectItem);
        return roots;
    }

    /**
     * 用户首次进入界面且没有历史选择时，默认选中第一条测线。
     */
    public PointListItem findFirstLineItem(DataRepository.ProjectTreeSummary tree) {
        if (tree == null || tree.lines == null || tree.lines.isEmpty()) {
            return null;
        }

        DataRepository.LineTreeSummary lineTree = tree.lines.get(0);
        int pointCount = lineTree.points == null ? 0 : lineTree.points.size();
        PointListItem item = PointListItem.createLine(
                lineTree.line.getId(),
                lineTree.line.getName(),
                pointCount);
        item.depth = 1;
        item.title = buildLineTitle(lineTree.line.getName());
        item.subtitle = context.getString(R.string.tree_line_subtitle_format, pointCount);
        item.meta = context.getString(
                R.string.tree_line_meta_format,
                countCompletedPointsInPoints(lineTree.points),
                pointCount);
        item.breadcrumb = buildBreadcrumbPath(
                resolveProjectName(tree),
                item.title,
                context.getString(R.string.tree_all_points),
                context.getString(R.string.tree_all_collections));
        return item;
    }

    private int countCompletedPointsInLines(List<DataRepository.LineTreeSummary> lineTrees) {
        int completedCount = 0;
        if (lineTrees == null) {
            return 0;
        }
        for (DataRepository.LineTreeSummary lineTree : lineTrees) {
            completedCount += countCompletedPointsInPoints(lineTree.points);
        }
        return completedCount;
    }

    private int countCompletedPointsInPoints(List<DataRepository.PointTreeSummary> pointTrees) {
        int completedCount = 0;
        if (pointTrees == null) {
            return 0;
        }
        for (DataRepository.PointTreeSummary pointTree : pointTrees) {
            if (isPointCompleted(pointTree.point)) {
                completedCount++;
            }
        }
        return completedCount;
    }

    private boolean isPointCompleted(MeasurementPointEntity point) {
        return point != null && point.getStatus() >= DataRepository.STATUS_SAVED;
    }

    public String buildLineTitle(float lineName) {
        return context.getString(R.string.tree_line_title, (int) lineName);
    }

    public String buildPointTitle(float pointName) {
        return context.getString(R.string.tree_point_title, (int) pointName);
    }

    private String buildSessionSubtitle(long startTime, int waveformCount) {
        if (startTime > 0L) {
            return context.getString(
                    R.string.tree_session_subtitle_with_time,
                    sessionTimeFormat.format(new Date(startTime)),
                    waveformCount);
        }
        return context.getString(R.string.tree_session_subtitle, waveformCount);
    }

    private String buildBreadcrumbPath(String projectName,
                                       String lineTitle,
                                       String pointTitle,
                                       String sessionTitle) {
        return context.getString(R.string.tree_breadcrumb_format, projectName, lineTitle, pointTitle, sessionTitle);
    }

    private String resolveProjectName(DataRepository.ProjectTreeSummary tree) {
        if (tree != null && tree.project != null && tree.project.getName() != null && !tree.project.getName().trim().isEmpty()) {
            return tree.project.getName();
        }
        return context.getString(R.string.app_name);
    }
}
