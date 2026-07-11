package cn.zjl.datacollector.ui.collection.chart;

/**
 * 阅读提示：采集图表模块代码：负责波形数据解码、状态整理和 RECV/SEND/OFF 图表绘制。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.github.mikephil.charting.interfaces.datasets.IScatterDataSet;
import com.github.mikephil.charting.renderer.scatter.IShapeRenderer;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * Draws a hollow diamond marker for RECV points so the chart matches the
 * reference screenshots more closely.
 */
public class DiamondShapeRenderer implements IShapeRenderer {

    private final Path diamondPathBuffer = new Path();

    @Override
    public void renderShape(
            Canvas canvas,
            IScatterDataSet dataSet,
            ViewPortHandler viewPortHandler,
            float posX,
            float posY,
            Paint renderPaint) {
        float shapeSize = dataSet.getScatterShapeSize();
        if (shapeSize <= 0f) {
            return;
        }

        float shapeHalf = shapeSize / 2f;
        float strokeWidth = Math.max(Utils.convertDpToPixel(1.2f), shapeSize * 0.22f);

        Paint.Style previousStyle = renderPaint.getStyle();
        Paint.Join previousJoin = renderPaint.getStrokeJoin();
        Paint.Cap previousCap = renderPaint.getStrokeCap();
        float previousStrokeWidth = renderPaint.getStrokeWidth();

        Path diamond = diamondPathBuffer;
        diamond.reset();
        diamond.moveTo(posX, posY - shapeHalf);
        diamond.lineTo(posX + shapeHalf, posY);
        diamond.lineTo(posX, posY + shapeHalf);
        diamond.lineTo(posX - shapeHalf, posY);
        diamond.close();

        renderPaint.setStyle(Paint.Style.STROKE);
        renderPaint.setStrokeJoin(Paint.Join.ROUND);
        renderPaint.setStrokeCap(Paint.Cap.ROUND);
        renderPaint.setStrokeWidth(strokeWidth);
        canvas.drawPath(diamond, renderPaint);

        diamond.reset();
        renderPaint.setStyle(previousStyle);
        renderPaint.setStrokeJoin(previousJoin);
        renderPaint.setStrokeCap(previousCap);
        renderPaint.setStrokeWidth(previousStrokeWidth);
    }
}
