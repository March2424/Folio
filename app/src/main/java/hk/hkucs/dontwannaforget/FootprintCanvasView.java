package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 足迹纸质画布视图 (模仿 example/IMG_3846.jpg 自由拼贴排版 + example/background2.PNG 纸张背景):
 * - 采用 background2.PNG 白色纹理纸张作为画布背景，无多余画框与装饰线条
 * - 图片直接放置在纸质画布上，保持原图比例，支持单指自由拖动位置、双指捏合缩放或滑块缩放(40%~200%)、90°旋转
 * - 支持多个可自由拖动位置的文本框，选中时显示虚线框与右上角 × 删除按钮
 * - 支持自定义字体、字号调节、颜色选择
 * - 同时支持编辑模式(editable=true)与详情/卡片只读展示模式(editable=false)
 */
public class FootprintCanvasView extends View {

    public static class TextBox {
        public String text;
        public float nx;       // 归一化左上角 X (0.0 ~ 0.92)
        public float ny;       // 归一化左上角 Y (0.0 ~ 0.92)
        public float sizeSp;   // 字号 (基准 340dp 画布宽度下的 sp)
        public int color;      // 文本颜色
        public String fontId;  // 字体 ID (FontManager)

        public TextBox(String text, float nx, float ny, float sizeSp, int color, String fontId) {
            this.text = text;
            this.nx = nx;
            this.ny = ny;
            this.sizeSp = sizeSp;
            this.color = color;
            this.fontId = fontId != null ? fontId : FontManager.FONT_SKETCH;
        }

        public TextBox copy() {
            return new TextBox(text, nx, ny, sizeSp, color, fontId);
        }
    }

    public static class State {
        public int template = 0;
        public boolean landscape = false;
        public int photoRotation = 0;     // 0, 90, 180, 270 度旋转
        public float photoScale = 1.0f;   // 0.4 ~ 2.0 缩放倍率
        public float photoPanX = -0.06f;  // 相对于画布中心的归一化偏移 X (-0.45 ~ 0.45)
        public float photoPanY = -0.14f;  // 相对于画布中心的归一化偏移 Y (-0.42 ~ 0.42)
        public String time = "12:00";
        public final List<TextBox> boxes = new ArrayList<>();

        public static State createDefault(String title, String sub, String date) {
            State s = new State();
            s.photoScale = 1.0f;
            s.photoPanX = -0.08f;
            s.photoPanY = -0.15f;
            String topTitle = (title != null && !title.isEmpty() ? title : L10n.s(R.string.ui_005))
                    + (date != null && !date.isEmpty() ? "\n" + date : "");
            s.boxes.add(new TextBox(topTitle, 0.56f, 0.14f, 13f, 0xFF2B2927, FontManager.FONT_SERIF));
            s.boxes.add(new TextBox(sub != null && !sub.isEmpty() ? sub : L10n.s(R.string.ui_006),
                    0.10f, 0.62f, 13f, 0xFF2B2927, FontManager.FONT_SERIF));
            s.boxes.add(new TextBox(L10n.s(R.string.ui_041),
                    0.46f, 0.78f, 12f, 0xFF4A4640, FontManager.FONT_SKETCH));
            return s;
        }

        public String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("v", 2);
                o.put("tpl", template);
                o.put("land", landscape);
                o.put("rot", photoRotation);
                o.put("scale", photoScale);
                o.put("panX", photoPanX);
                o.put("panY", photoPanY);
                o.put("time", time != null ? time : "12:00");
                JSONArray arr = new JSONArray();
                for (TextBox b : boxes) {
                    JSONObject bo = new JSONObject();
                    bo.put("t", b.text != null ? b.text : "");
                    bo.put("x", b.nx);
                    bo.put("y", b.ny);
                    bo.put("sz", b.sizeSp);
                    bo.put("c", b.color);
                    bo.put("f", b.fontId != null ? b.fontId : FontManager.FONT_SKETCH);
                    arr.put(bo);
                }
                o.put("boxes", arr);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        public static State fromField(String raw, Record r, int pageIdx) {
            if (raw != null && raw.trim().startsWith("{")) {
                try {
                    JSONObject o = new JSONObject(raw);
                    State s = new State();
                    int ver = o.optInt("v", 1);
                    s.template = o.optInt("tpl", 0);
                    s.landscape = o.optBoolean("land", false);
                    s.photoRotation = o.optInt("rot", 0);
                    s.photoScale = (float) o.optDouble("scale", 1.0);
                    if (ver >= 2) {
                        s.photoPanX = (float) o.optDouble("panX", -0.06);
                        s.photoPanY = (float) o.optDouble("panY", -0.14);
                    } else {
                        // 旧版 panX/panY 为框内偏移，映射到画布偏上居中位置
                        s.photoPanX = clamp((float) o.optDouble("panX", 0.0) * 0.25f, -0.42f, 0.42f);
                        s.photoPanY = -0.10f + clamp((float) o.optDouble("panY", 0.0) * 0.25f, -0.35f, 0.35f);
                    }
                    s.time = o.optString("time", "12:00");
                    JSONArray arr = o.optJSONArray("boxes");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject bo = arr.getJSONObject(i);
                            s.boxes.add(new TextBox(
                                    bo.optString("t", ""),
                                    (float) bo.optDouble("x", 0.1),
                                    (float) bo.optDouble("y", 0.1),
                                    (float) bo.optDouble("sz", 13.0),
                                    bo.optInt("c", 0xFF3A3733),
                                    bo.optString("f", FontManager.FONT_SKETCH)
                            ));
                        }
                    }
                    if (s.boxes.isEmpty()) {
                        s.boxes.add(new TextBox(r != null ? r.title : L10n.s(R.string.ui_042), 0.1f, 0.78f, 13f, 0xFF3A3733, FontManager.FONT_SKETCH));
                    }
                    return s;
                } catch (Exception ignored) {}
            }
            // 兼容旧版 "HH:mm|文字" 格式,自动转换为 IMG_3846.jpg 极简白纸排版
            State s = new State();
            s.photoScale = 1.0f;
            s.photoPanX = (pageIdx % 2 == 0) ? -0.08f : 0.08f;
            s.photoPanY = -0.14f;
            String timePart = "12:00";
            String bodyPart = raw != null ? raw : "";
            if (raw != null && raw.contains("|")) {
                String[] parts = raw.split("\\|", 2);
                timePart = parts[0];
                bodyPart = parts.length > 1 ? parts[1] : "";
            }
            s.time = timePart;
            String header = (r != null && !r.title.isEmpty() ? r.title : "FOOTPRINT")
                    + " · " + timePart + "\n" + (r != null ? r.date : "");
            s.boxes.add(new TextBox(header, 0.54f, 0.14f, 11.5f, 0xFF3A3733, FontManager.FONT_SKETCH));
            if (r != null && !r.sub.isEmpty()) {
                s.boxes.add(new TextBox(r.sub, 0.10f, 0.62f, 12f, 0xFF5A554E, FontManager.FONT_SERIF));
            }
            s.boxes.add(new TextBox(bodyPart.isEmpty() ? L10n.s(R.string.ui_044) : bodyPart,
                    0.10f, 0.74f, 13f, 0xFF2B2927, FontManager.FONT_SERIF));
            return s;
        }
    }

    public interface Listener {
        void onBoxSelected(int index, TextBox box);
        void onBoxesChanged();
        void onPhotoTransformChanged(float scale);
    }

    private static Bitmap sPaperTexture;

    private State state = new State();
    private boolean editable = false;
    private int selectedIdx = 0;
    private Listener listener;

    private int fallbackC1 = 0xFF6B7A6A, fallbackC2 = 0xFFC9CDB8;
    private Bitmap photoBitmap;

    private final Paint paperPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint borderPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint creaseShadowPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint creaseHighlightPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint photoShadowPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeXPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintPaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);

    private final Path clipPath = new Path();
    private final Rect paperSrcRect = new Rect();
    private final RectF cardRect = new RectF();
    private final RectF photoRect = new RectF();
    private final List<RectF> boxBounds = new ArrayList<>();
    private final RectF deleteBtnRect = new RectF();

    private final ScaleGestureDetector scaleDetector;
    private int dragMode = 0; // 0=none, 1=dragging text box, 2=dragging photo on canvas
    private float lastTouchX, lastTouchY;

    public FootprintCanvasView(Context context) {
        super(context);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(0x1A000000);
        borderPaint.setStrokeWidth(Cards.dp(context, 1));

        creaseShadowPaint.setStyle(Paint.Style.STROKE);
        creaseShadowPaint.setColor(0x12000000);
        creaseShadowPaint.setStrokeWidth(Cards.dp(context, 1.2f));

        creaseHighlightPaint.setStyle(Paint.Style.STROKE);
        creaseHighlightPaint.setColor(0x55FFFFFF);
        creaseHighlightPaint.setStrokeWidth(Cards.dp(context, 1f));

        photoShadowPaint.setStyle(Paint.Style.FILL);
        photoShadowPaint.setColor(0x10000000);

        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setColor(0xFF5C5852);
        dashPaint.setStrokeWidth(Cards.dp(context, 1.2f));
        dashPaint.setPathEffect(new DashPathEffect(new float[]{Cards.dp(context, 4), Cards.dp(context, 3)}, 0));

        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(0xFF2B2927);

        badgeXPaint.setStyle(Paint.Style.STROKE);
        badgeXPaint.setColor(Color.WHITE);
        badgeXPaint.setStrokeWidth(Cards.dp(context, 1.6f));
        badgeXPaint.setStrokeCap(Paint.Cap.ROUND);

        hintPaint.setColor(0xCCFFFFFF);
        hintPaint.setTypeface(AppFonts.regular());
        hintPaint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (!editable) return false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!editable) return false;
                float factor = detector.getScaleFactor();
                state.photoScale = clamp(state.photoScale * factor, 0.40f, 2.00f);
                if (listener != null) listener.onPhotoTransformChanged(state.photoScale);
                invalidate();
                return true;
            }
        });
    }

    private static Bitmap getPaperTexture(Context context) {
        if (sPaperTexture == null || sPaperTexture.isRecycled()) {
            try {
                sPaperTexture = BitmapFactory.decodeResource(context.getResources(), R.drawable.bg_footprint_paper);
            } catch (Exception ignored) {}
        }
        return sPaperTexture;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        invalidate();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setFallbackColors(int c1, int c2) {
        this.fallbackC1 = c1;
        this.fallbackC2 = c2;
        invalidate();
    }

    public State getState() {
        return state;
    }

    public void setState(State s) {
        this.state = s != null ? s : new State();
        if (selectedIdx >= this.state.boxes.size()) {
            selectedIdx = Math.max(0, this.state.boxes.size() - 1);
        }
        invalidate();
        notifySelection();
    }

    public int getSelectedIndex() {
        return selectedIdx;
    }

    public TextBox getSelectedBox() {
        if (selectedIdx >= 0 && selectedIdx < state.boxes.size()) {
            return state.boxes.get(selectedIdx);
        }
        return null;
    }

    public void selectBox(int idx) {
        if (idx >= -1 && idx < state.boxes.size()) {
            selectedIdx = idx;
            invalidate();
            notifySelection();
        }
    }

    public void setPhotoUri(Uri uri) {
        setPhotoUri(uri, false);
    }

    public void setPhotoUri(Uri uri, boolean autoDetectOrientation) {
        if (uri == null) {
            photoBitmap = null;
            invalidate();
            return;
        }
        new Thread(() -> {
            Bitmap bm = decodeSampledFromUri(getContext(), uri, 1280);
            post(() -> {
                photoBitmap = bm;
                invalidate();
            });
        }).start();
    }

    public void setPhotoMedia(Media m) {
        if (m == null) {
            photoBitmap = null;
            invalidate();
            return;
        }
        new Thread(() -> {
            File f = new File(getContext().getFilesDir(), m.path);
            Bitmap bm = decodeSampledFromFile(f, 1280);
            post(() -> {
                photoBitmap = bm;
                invalidate();
            });
        }).start();
    }

    public void setPhotoBitmapDirect(Bitmap bm) {
        this.photoBitmap = bm;
        invalidate();
    }

    /** 导出高清无虚线框的合成纸质卡片 Bitmap */
    public Bitmap renderToBitmap(int targetW) {
        int targetH = Math.round(targetW * 1.38f);
        Bitmap out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        drawScene(c, targetW, targetH, false);
        return out;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (hMode != MeasureSpec.EXACTLY) {
            h = Math.round(w * 1.38f);
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawScene(canvas, getWidth(), getHeight(), editable);
    }

    private void drawScene(Canvas canvas, int w, int h, boolean showEditOverlay) {
        if (w <= 0 || h <= 0) return;
        float scaleFactor = w / (float) Cards.dp(getContext(), 340);

        // 1. 绘制 background2.PNG 白色纹理纸张背景 + 中央轻微折痕 (还原 IMG_3846.jpg)
        float radius = 12f * scaleFactor * getResources().getDisplayMetrics().density;
        cardRect.set(1, 1, w - 1, h - 1);

        canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        Bitmap paperBm = getPaperTexture(getContext());
        if (paperBm != null && !paperBm.isRecycled()) {
            paperSrcRect.set(0, 0, paperBm.getWidth(), paperBm.getHeight());
            canvas.drawBitmap(paperBm, paperSrcRect, cardRect, paperPaint);
        } else {
            paperPaint.setColor(0xFFF5F4F0);
            canvas.drawRect(cardRect, paperPaint);
        }

        // 还原 IMG_3846.jpg 纸张中线轻微折痕光影
        float foldY = h * 0.50f;
        canvas.drawLine(0, foldY, w, foldY, creaseShadowPaint);
        canvas.drawLine(0, foldY + 1.5f * scaleFactor, w, foldY + 1.5f * scaleFactor, creaseHighlightPaint);

        // 2. 绘制可自由拖动位置、无边框线条的图片 (保持原图长宽比)
        computeFreePhotoRect(w, h, photoRect);
        drawFreePhoto(canvas, photoRect, scaleFactor, showEditOverlay);

        // 3. 绘制所有可移动文本框 (保持原有虚线框与 × 删除按钮设计不变)
        boxBounds.clear();
        deleteBtnRect.setEmpty();
        float maxBoxW = w * 0.80f;

        for (int i = 0; i < state.boxes.size(); i++) {
            TextBox b = state.boxes.get(i);
            String txt = (b.text == null || b.text.isEmpty()) ? (showEditOverlay ? L10n.s(R.string.ui_045) : "") : b.text;
            if (txt.isEmpty()) {
                boxBounds.add(new RectF());
                continue;
            }

            textPaint.setTypeface(FontManager.resolve(getContext(), b.fontId));
            float pxSize = b.sizeSp * getResources().getDisplayMetrics().scaledDensity * scaleFactor;
            textPaint.setTextSize(Math.max(10f, pxSize));
            textPaint.setColor(b.color);
            textPaint.setLetterSpacing(FontManager.FONT_SKETCH.equals(b.fontId) ? 0.14f : 0.04f);

            int layoutW = Math.round(Math.min(maxBoxW, Math.max(w * 0.25f, w * (0.94f - b.nx))));
            StaticLayout sl = StaticLayout.Builder.obtain(txt, 0, txt.length(), textPaint, layoutW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.28f)
                    .setIncludePad(false)
                    .build();

            float maxLineW = 0f;
            for (int line = 0; line < sl.getLineCount(); line++) {
                maxLineW = Math.max(maxLineW, sl.getLineWidth(line));
            }
            float textW = Math.max(Cards.dp(getContext(), 48) * scaleFactor, maxLineW);
            float textH = sl.getHeight();

            float x = Math.max(w * 0.04f, Math.min(b.nx * w, w - textW - w * 0.04f));
            float y = Math.max(h * 0.03f, Math.min(b.ny * h, h - textH - h * 0.03f));

            canvas.save();
            canvas.translate(x, y);
            sl.draw(canvas);
            canvas.restore();

            float pad = Cards.dp(getContext(), 7) * scaleFactor;
            RectF bounds = new RectF(x - pad, y - pad, x + textW + pad, y + textH + pad);
            boxBounds.add(bounds);

            // 选中态: 虚线边框 + 右上角深色圆形 × 删除按钮
            if (showEditOverlay && i == selectedIdx) {
                canvas.drawRect(bounds, dashPaint);
                float r = Cards.dp(getContext(), 10) * scaleFactor;
                float cx = bounds.right;
                float cy = bounds.top;
                canvas.drawCircle(cx, cy, r, badgePaint);
                float d = r * 0.38f;
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, badgeXPaint);
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, badgeXPaint);
                deleteBtnRect.set(cx - r * 1.5f, cy - r * 1.5f, cx + r * 1.5f, cy + r * 1.5f);
            }
        }

        canvas.restore();
        canvas.drawRoundRect(cardRect, radius, radius, borderPaint);
    }

    /**
     * 计算图片在纸质画布上的自由矩形区域:
     * - 保持原图(旋转后)真实长宽比，不裁剪、不画框线
     * - 支持通过 state.photoScale 放大缩小，通过 state.photoPanX/Y 在整张白纸上自由移动
     */
    private void computeFreePhotoRect(int w, int h, RectF out) {
        float aspect = 1.28f; // 未上传图片时的默认横向明信片比例 (模仿 IMG_3846.jpg 左上照片)
        if (photoBitmap != null && !photoBitmap.isRecycled()
                && photoBitmap.getWidth() > 0 && photoBitmap.getHeight() > 0) {
            boolean rot90 = (Math.abs(state.photoRotation) % 180) == 90;
            float effW = rot90 ? photoBitmap.getHeight() : photoBitmap.getWidth();
            float effH = rot90 ? photoBitmap.getWidth() : photoBitmap.getHeight();
            aspect = effW / Math.max(1f, effH);
        }

        float scale = clamp(state.photoScale, 0.40f, 2.00f);
        float baseMaxDim = w * 0.54f * scale;
        float pw, ph;
        if (aspect >= 1f) {
            pw = baseMaxDim;
            ph = baseMaxDim / aspect;
        } else {
            ph = baseMaxDim * 1.08f;
            pw = ph * aspect;
        }

        float cx = w * (0.5f + clamp(state.photoPanX, -0.45f, 0.45f));
        float cy = h * (0.5f + clamp(state.photoPanY, -0.44f, 0.44f));
        out.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f);
    }

    private void drawFreePhoto(Canvas canvas, RectF rect, float scaleFactor, boolean showEditOverlay) {
        // 轻微纸片贴附阴影，无任何手绘边框线条
        float off = 1.5f * scaleFactor;
        canvas.drawRect(rect.left + off, rect.top + off, rect.right + off, rect.bottom + off, photoShadowPaint);

        if (photoBitmap != null && !photoBitmap.isRecycled()) {
            float bw = photoBitmap.getWidth();
            float bh = photoBitmap.getHeight();
            boolean rot90 = (Math.abs(state.photoRotation) % 180) == 90;
            float effW = rot90 ? bh : bw;
            float effH = rot90 ? bw : bh;
            float scaleX = rect.width() / Math.max(1f, effW);
            float scaleY = rect.height() / Math.max(1f, effH);

            Matrix m = new Matrix();
            m.postTranslate(-bw / 2f, -bh / 2f);
            m.postRotate(state.photoRotation);
            m.postScale(scaleX, scaleY);
            m.postTranslate(rect.centerX(), rect.centerY());
            canvas.drawBitmap(photoBitmap, m, paperPaint);
        } else {
            Paint gp = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            gp.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    fallbackC1, fallbackC2, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, gp);
            if (showEditOverlay) {
                hintPaint.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity * scaleFactor);
                canvas.drawText(L10n.s(R.string.ui_046), rect.centerX(), rect.centerY() + hintPaint.getTextSize() * 0.35f, hintPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editable) return super.onTouchEvent(event);

        scaleDetector.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                dragMode = 0;

                // 1. 检查是否点击了当前选中框右上角的 × 删除按钮
                if (selectedIdx >= 0 && selectedIdx < state.boxes.size() && deleteBtnRect.contains(x, y)) {
                    if (state.boxes.size() > 1) {
                        state.boxes.remove(selectedIdx);
                        selectedIdx = Math.max(0, selectedIdx - 1);
                    } else {
                        state.boxes.get(0).text = "";
                    }
                    invalidate();
                    notifySelection();
                    if (listener != null) listener.onBoxesChanged();
                    return true;
                }

                // 2. 检查是否命中某个文本框(从上层往下层找)
                for (int i = boxBounds.size() - 1; i >= 0; i--) {
                    if (boxBounds.get(i).contains(x, y)) {
                        selectedIdx = i;
                        dragMode = 1;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                        invalidate();
                        notifySelection();
                        return true;
                    }
                }

                // 3. 检查是否点在图片区域内 -> 在白色纸张画布上自由拖动图片位置
                if (photoRect.contains(x, y)) {
                    dragMode = 2;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                // 双指落点在画布上时优先进行图片缩放
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;

            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) {
                    lastTouchX = x;
                    lastTouchY = y;
                    return true;
                }
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                lastTouchX = x;
                lastTouchY = y;

                if (dragMode == 1 && selectedIdx >= 0 && selectedIdx < state.boxes.size()) {
                    TextBox b = state.boxes.get(selectedIdx);
                    b.nx = clamp(b.nx + dx / Math.max(1f, getWidth()), 0.03f, 0.88f);
                    b.ny = clamp(b.ny + dy / Math.max(1f, getHeight()), 0.02f, 0.92f);
                    invalidate();
                    return true;
                } else if (dragMode == 2 && getWidth() > 0 && getHeight() > 0) {
                    state.photoPanX = clamp(state.photoPanX + dx / (float) getWidth(), -0.45f, 0.45f);
                    state.photoPanY = clamp(state.photoPanY + dy / (float) getHeight(), -0.44f, 0.44f);
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragMode = 0;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.onTouchEvent(event);
    }

    private void notifySelection() {
        if (listener != null) {
            listener.onBoxSelected(selectedIdx, getSelectedBox());
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Bitmap decodeSampledFromUri(Context c, Uri uri, int maxDim) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, opts);
            }
            int sample = 1;
            int w = opts.outWidth, h = opts.outHeight;
            while (w / sample > maxDim || h / sample > maxDim) sample *= 2;
            BitmapFactory.Options opts2 = new BitmapFactory.Options();
            opts2.inSampleSize = sample;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts2);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decodeSampledFromFile(File f, int maxDim) {
        if (f == null || !f.exists()) return null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            int sample = 1;
            int w = opts.outWidth, h = opts.outHeight;
            while (w / sample > maxDim || h / sample > maxDim) sample *= 2;
            BitmapFactory.Options opts2 = new BitmapFactory.Options();
            opts2.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts2);
        } catch (Exception e) {
            return null;
        }
    }
}
