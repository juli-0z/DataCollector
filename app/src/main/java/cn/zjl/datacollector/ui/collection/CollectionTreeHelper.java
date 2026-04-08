package cn.zjl.datacollector.ui.collection;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.zjl.datacollector.R;
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
        if (tree == null || tree.lines == null || tree.lines.isEmpty()) {
            return roots;
        }

        String projectName = resolveProjectName(tree);
        for (DataRepository.LineTreeSummary lineTree : tree.lines) {
            PointListItem lineItem = PointListItem.createLine(
                    lineTree.line.id,
                    lineTree.line.name,
                    lineTree.points.size());
            lineItem.depth = 0;
            lineItem.title = buildLineTitle(lineTree.line.name);
            lineItem.subtitle = context.getString(R.string.tree_line_subtitle_format, lineTree.points.size());
            lineItem.breadcrumb = buildBreadcrumbPath(
                    projectName,
                    lineItem.title,
                    context.getString(R.string.tree_all_points),
                    context.getString(R.string.tree_all_collections));

            for (DataRepository.PointTreeSummary pointTree : lineTree.points) {
                PointListItem pointItem = PointListItem.createPoint(
                        pointTree.point.id,
                        pointTree.point.name,
                        pointTree.sessions.size(),
                        pointTree.point.status >= DataRepository.STATUS_SAVED);
                pointItem.depth = 1;
                pointItem.title = buildPointTitle(pointTree.point.name);
                pointItem.subtitle = context.getString(R.string.tree_point_subtitle);
                pointItem.meta = context.getString(R.string.tree_point_meta_format, pointTree.sessions.size());
                pointItem.breadcrumb = buildBreadcrumbPath(
                        projectName,
                        lineItem.title,
                        pointItem.title,
                        context.getString(R.string.tree_all_collections));

                for (DataRepository.CollectionSessionSummary session : pointTree.sessions) {
                    PointListItem sessionItem = PointListItem.createSession(
                            pointTree.point.id,
                            session.sessionKey,
                            session.collectionIndex,
                            buildSessionSubtitle(session.startTime, session.waveformCount));
                    sessionItem.depth = 2;
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

            roots.add(lineItem);
        }
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
        PointListItem item = PointListItem.createLine(
                lineTree.line.id,
                lineTree.line.name,
                lineTree.points.size());
        item.depth = 0;
        item.title = buildLineTitle(lineTree.line.name);
        item.subtitle = context.getString(R.string.tree_line_subtitle_format, lineTree.points.size());
        item.breadcrumb = buildBreadcrumbPath(
                resolveProjectName(tree),
                item.title,
                context.getString(R.string.tree_all_points),
                context.getString(R.string.tree_all_collections));
        return item;
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
        if (tree != null && tree.project != null && tree.project.name != null && !tree.project.name.trim().isEmpty()) {
            return tree.project.name;
        }
        return context.getString(R.string.app_name);
    }
}
