package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/** 支持双指缩放、拖动、双击放大的图片视图,默认完整显示(不裁切)。 */
public class ZoomImageView extends AppCompatImageView {
    private final Matrix m = new Matrix();
    private final float[] v = new float[9];
    private float minScale = 1f;
    private final ScaleGestureDetector sg;
    private final GestureDetector gd;
    private float lx, ly;
    private int pointers;

    public ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float cur = scale();
                float f = Math.max(minScale / cur, Math.min(minScale * 5 / cur, d.getScaleFactor()));
                m.postScale(f, f, d.getFocusX(), d.getFocusY());
                fix();
                return true;
            }
        });
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                float target = scale() > minScale * 1.5f ? minScale : minScale * 2.5f;
                float f = target / scale();
                m.postScale(f, f, e.getX(), e.getY());
                fix();
                return true;
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        reset();
    }

    @Override
    public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        reset();
    }

    private void reset() {
        if (getDrawable() == null || getWidth() == 0) return;
        float dw = getDrawable().getIntrinsicWidth(), dh = getDrawable().getIntrinsicHeight();
        minScale = Math.min(getWidth() / dw, getHeight() / dh);
        m.reset();
        m.postScale(minScale, minScale);
        m.postTranslate((getWidth() - dw * minScale) / 2f, (getHeight() - dh * minScale) / 2f);
        setImageMatrix(m);
    }

    private float scale() {
        m.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    /** 限制平移,使图片不被拖出可视范围;小于视图时居中 */
    private void fix() {
        if (getDrawable() == null) return;
        RectF r = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        m.mapRect(r);
        float dx = 0, dy = 0;
        if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        m.postTranslate(dx, dy);
        setImageMatrix(m);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gd.onTouchEvent(e);
        sg.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lx = e.getX(); ly = e.getY(); pointers = 1;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                pointers = e.getPointerCount();
                break;
            case MotionEvent.ACTION_MOVE:
                if (pointers == 1 && !sg.isInProgress()) {
                    m.postTranslate(e.getX() - lx, e.getY() - ly);
                    fix();
                }
                lx = e.getX(); ly = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                pointers = e.getPointerCount() - 1;
                int i = e.getActionIndex() == 0 ? 1 : 0;
                lx = e.getX(i); ly = e.getY(i);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pointers = 0;
                break;
        }
        return true;
    }
}
