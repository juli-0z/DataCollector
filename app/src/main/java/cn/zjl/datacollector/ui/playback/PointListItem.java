package cn.zjl.datacollector.ui.playback;

/**
 * 阅读提示：回放列表界面代码：负责展示历史测点并支持逐点查看采集结果。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import java.util.ArrayList;
import java.util.List;

public class PointListItem {

    public static final int TYPE_PROJECT = 0;
    public static final int TYPE_LINE = 1;
    public static final int TYPE_POINT = 2;
    public static final int TYPE_SESSION = 3;

    public int type;
    public long nodeId;
    public long pointId;
    public long sessionKey;
    public int sessionIndex;
    public int depth;
    public String title;
    public String subtitle;
    public String meta;
    public String breadcrumb;
    public boolean hasChildren;
    public boolean expanded;
    public boolean completed;
    public final List<PointListItem> children = new ArrayList<>();

    public static PointListItem createProject(long projectId, String projectName, int lineCount) {
        PointListItem item = new PointListItem();
        item.type = TYPE_PROJECT;
        item.nodeId = projectId;
        item.title = projectName;
        item.subtitle = "";
        item.depth = 0;
        item.hasChildren = lineCount > 0;
        item.expanded = true;
        return item;
    }

    public static PointListItem createGroup(String groupName) {
        PointListItem item = new PointListItem();
        item.type = TYPE_LINE;
        item.nodeId = groupName != null ? groupName.hashCode() : 0L;
        item.title = groupName;
        item.subtitle = "";
        item.depth = 1;
        item.hasChildren = false;
        return item;
    }

    public static PointListItem createLine(long lineId, float lineName, int pointCount) {
        PointListItem item = new PointListItem();
        item.type = TYPE_LINE;
        item.nodeId = lineId;
        item.title = String.valueOf((int) lineName);
        item.subtitle = "";
        item.depth = 1;
        item.hasChildren = pointCount > 0;
        return item;
    }

    public static PointListItem createPoint(long pointId, float pointNumber, int sessionCount, boolean completed) {
        PointListItem item = new PointListItem();
        item.type = TYPE_POINT;
        item.nodeId = pointId;
        item.pointId = pointId;
        item.title = String.valueOf((int) pointNumber);
        item.subtitle = "";
        item.meta = "";
        item.depth = 2;
        item.hasChildren = sessionCount > 0;
        item.completed = completed;
        return item;
    }

    public static PointListItem createSession(long pointId, long sessionKey, int sessionIndex, String subtitle) {
        PointListItem item = new PointListItem();
        item.type = TYPE_SESSION;
        item.nodeId = pointId * 1000L + sessionIndex;
        item.pointId = pointId;
        item.sessionKey = sessionKey;
        item.sessionIndex = sessionIndex;
        item.title = String.valueOf(sessionIndex);
        item.subtitle = subtitle;
        item.meta = "";
        item.depth = 3;
        item.hasChildren = false;
        return item;
    }
}
