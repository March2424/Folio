package hk.hkucs.dontwannaforget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * 纸质卡片背景。
 * - 邮票模式:四边为半圆锯齿孔;
 * - 票根模式(splitY > 0):圆角矩形,并在 splitY 处左右各有半圆撕口。
 */
public class StampDrawable extends Drawable {
    private final Paint paper = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float hole;
    private final float shadowDy;
    private final float splitY;

    public StampDrawable(float holeRadiusPx, float shadowDyPx, float splitYPx) {
        hole = holeRadiusPx;
        shadowDy = shadowDyPx;
        splitY = splitYPx;
        paper.setColor(0xFFFCFAF5);
        shadow.setColor(0x26000000);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        path.reset();
        float w = b.width(), h = b.height() - shadowDy;
        if (splitY > 0) {
            path.addRoundRect(new RectF(0, 0, w, h), hole * 1.5f, hole * 1.5f, Path.Direction.CW);
            Path holes = new Path();
            holes.addCircle(0, splitY, hole * 1.4f, Path.Direction.CW);
            holes.addCircle(w, splitY, hole * 1.4f, Path.Direction.CW);
            path.op(holes, Path.Op.DIFFERENCE);
        } else {
            path.addRect(0, 0, w, h, Path.Direction.CW);
            Path holes = new Path();
            float step = hole * 2.6f;
            int nx = Math.max(1, Math.round(w / step));
            int ny = Math.max(1, Math.round(h / step));
            float sx = w / nx, sy = h / ny;
            for (int i = 0; i <= nx; i++) {
                holes.addCircle(i * sx, 0, hole, Path.Direction.CW);
                holes.addCircle(i * sx, h, hole, Path.Direction.CW);
            }
            for (int j = 0; j <= ny; j++) {
                holes.addCircle(0, j * sy, hole, Path.Direction.CW);
                holes.addCircle(w, j * sy, hole, Path.Direction.CW);
            }
            path.op(holes, Path.Op.DIFFERENCE);
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.save();
        c.translate(b.left, b.top + shadowDy);
        c.drawPath(path, shadow);
        c.restore();
        c.save();
        c.translate(b.left, b.top);
        c.drawPath(path, paper);
        c.restore();
    }

    @Override public void setAlpha(int a) { paper.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paper.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
