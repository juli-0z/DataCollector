package cn.zjl.datacollector.ui.playback;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.R;

public class PointListAdapter extends RecyclerView.Adapter<PointListAdapter.ViewHolder> {

    private final List<PointListItem> rootItems = new ArrayList<>();
    private final List<PointListItem> visibleItems = new ArrayList<>();
    private OnItemClickListener listener;
    private int selectedType = -1;
    private long selectedNodeId = -1L;
    private long selectedPointId = -1L;
    private int selectedSessionIndex = -1;

    public interface OnItemClickListener {
        void onItemClick(PointListItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<PointListItem> items) {
        rootItems.clear();
        if (items != null) {
            rootItems.addAll(items);
        }
        rebuildVisibleItems();
    }

    public void refresh() {
        rebuildVisibleItems();
    }

    public void setSelectedPoint(long pointId) {
        selectedType = PointListItem.TYPE_POINT;
        selectedNodeId = pointId;
        selectedPointId = pointId;
        selectedSessionIndex = -1;
        notifyDataSetChanged();
    }

    public void setSelectedSession(long pointId, int sessionIndex) {
        selectedType = PointListItem.TYPE_SESSION;
        selectedNodeId = pointId;
        selectedPointId = pointId;
        selectedSessionIndex = sessionIndex;
        notifyDataSetChanged();
    }

    public void setSelectedProject(long projectId) {
        selectedType = PointListItem.TYPE_PROJECT;
        selectedNodeId = projectId;
        selectedPointId = -1L;
        selectedSessionIndex = -1;
        notifyDataSetChanged();
    }

    public void setSelectedLine(long lineId) {
        selectedType = PointListItem.TYPE_LINE;
        selectedNodeId = lineId;
        selectedPointId = -1L;
        selectedSessionIndex = -1;
        notifyDataSetChanged();
    }

    private void rebuildVisibleItems() {
        visibleItems.clear();
        for (PointListItem item : rootItems) {
            appendVisible(item);
        }
        notifyDataSetChanged();
    }

    private void appendVisible(PointListItem item) {
        visibleItems.add(item);
        if (!item.expanded) {
            return;
        }
        for (PointListItem child : item.children) {
            appendVisible(child);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playback_point, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PointListItem item = visibleItems.get(position);
        Context context = holder.itemView.getContext();

        ViewGroup.MarginLayoutParams indentParams = (ViewGroup.MarginLayoutParams) holder.rowContainer.getLayoutParams();
        indentParams.setMarginStart(item.depth * 18);
        holder.rowContainer.setLayoutParams(indentParams);

        holder.textIndicator.setText(item.hasChildren
                ? context.getString(item.expanded ? R.string.tree_indicator_expanded : R.string.tree_indicator_collapsed)
                : context.getString(R.string.tree_indicator_leaf));
        holder.textTitle.setText(item.title);
        holder.textSubtitle.setText(item.subtitle == null ? "" : item.subtitle);
        holder.textMeta.setText(item.meta == null ? "" : item.meta);
        holder.textMeta.setVisibility(item.meta == null || item.meta.isEmpty() ? View.GONE : View.VISIBLE);

        int accentColor;
        switch (item.type) {
            case PointListItem.TYPE_PROJECT:
                accentColor = R.color.blue_primary;
                break;
            case PointListItem.TYPE_LINE:
                accentColor = R.color.status_active_fg;
                break;
            case PointListItem.TYPE_SESSION:
                accentColor = R.color.chart_off;
                break;
            case PointListItem.TYPE_POINT:
            default:
                accentColor = item.completed ? R.color.success : R.color.error;
                break;
        }

        boolean selected = isSelected(item);
        holder.rowContainer.setBackgroundResource(
                selected ? R.drawable.bg_surface_panel_selected
                        : item.type == PointListItem.TYPE_PROJECT ? R.drawable.bg_surface_panel
                        : R.drawable.bg_surface_panel_strong);

        int accent = ContextCompat.getColor(context, selected ? R.color.blue_primary : accentColor);
        holder.textIndicator.setTextColor(accent);
        holder.viewAccent.setBackgroundColor(accent);
        holder.textTitle.setTextColor(ContextCompat.getColor(
                context,
                selected ? R.color.blue_primary : R.color.text_primary));
        holder.textMeta.setTextColor(ContextCompat.getColor(
                context,
                selected ? R.color.blue_primary : R.color.text_secondary));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    private boolean isSelected(PointListItem item) {
        if (item.type != selectedType) {
            return false;
        }
        if (item.type == PointListItem.TYPE_PROJECT || item.type == PointListItem.TYPE_LINE) {
            return item.nodeId == selectedNodeId;
        }
        if (item.type == PointListItem.TYPE_POINT) {
            return item.pointId == selectedPointId;
        }
        if (item.type == PointListItem.TYPE_SESSION) {
            return item.pointId == selectedPointId && item.sessionIndex == selectedSessionIndex;
        }
        return false;
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View rowContainer;
        View viewAccent;
        TextView textIndicator;
        TextView textTitle;
        TextView textSubtitle;
        TextView textMeta;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            rowContainer = itemView.findViewById(R.id.layout_point_item);
            viewAccent = itemView.findViewById(R.id.view_status_indicator);
            textIndicator = itemView.findViewById(R.id.text_indicator);
            textTitle = itemView.findViewById(R.id.text_point_number);
            textSubtitle = itemView.findViewById(R.id.text_collection_count);
            textMeta = itemView.findViewById(R.id.text_status);
        }
    }
}
