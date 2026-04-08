package cn.zjl.datacollector.ui.playback;

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

    public static PointListItem createProject(String projectName) {
        PointListItem item = new PointListItem();
        item.type = TYPE_PROJECT;
        item.nodeId = -1L;
        item.title = projectName;
        item.subtitle = "";
        item.depth = 0;
        item.hasChildren = true;
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
