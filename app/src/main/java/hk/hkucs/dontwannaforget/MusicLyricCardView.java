package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
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
import android.text.TextUtils;
import android.view.View;

import java.io.File;
import java.io.InputStream;

/**
 * 音乐歌词卡片自定义视图 (模仿 example/IMG_3870.jpg):
 * - 外围带有深灰磨砂圆角边框；
 * - 背景采用用户上传的图片并进行柔焦模糊 + 暗调压暗遮罩；
 * - 左上角展示清晰（不模糊）的圆角小方形封面（带细白边）+ 右侧白色粗体歌名与歌手名；
 * - 中下区域展示大号醒目歌词，支持切换内置字体与导入本地 .ttf/.otf 字体；
 * - 移除原参考图底部的 Spotify 标志，保持画面纯粹。
 */
public class MusicLyricCardView extends View {

    private Record record;
    private MusicCardStyle style = new MusicCardStyle();
    private boolean compactMode = false;

    private Bitmap rawBitmap;
    private Bitmap blurredBgBitmap;
    private int cachedBlurLevel = -1;

    private final Paint framePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbBorderPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint artistPaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint lyricPaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);

    private final RectF outerRect = new RectF();
    private final RectF innerRect = new RectF();
    private final RectF thumbRect = new RectF();
    private final Path innerClipPath = new Path();
    private final Path thumbClipPath = new Path();

    public MusicLyricCardView(Context context) {
        super(context);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x48000000);

        framePaint.setStyle(Paint.Style.FILL);
        framePaint.setColor(0xFF565558); // 还原 IMG_3870.jpg 外圈温润深灰包边

        thumbBorderPaint.setStyle(Paint.Style.STROKE);
        thumbBorderPaint.setColor(0xE6FFFFFF);
    }

    public void bind(Record r, MusicCardStyle customStyle, boolean compact) {
        this.record = r;
        this.style = customStyle != null ? customStyle : MusicCardStyle.fromRecord(r);
        this.compactMode = compact;

        if (r != null && r.id != null) {
            Media cov = MediaIndex.first(r.id, Media.COVER);
            if (cov == null) cov = MediaIndex.first(r.id, Media.PHOTO);
            if (cov != null) {
                loadMediaAsync(cov);
            } else {
                loadFallbackSceneAsync(r);
            }
        } else {
            loadFallbackSceneAsync(r);
        }
        requestLayout();
        invalidate();
    }

    public void setStyle(MusicCardStyle s) {
        this.style = s != null ? s : new MusicCardStyle();
        if (rawBitmap != null && cachedBlurLevel != this.style.blurLevel) {
            rebuildBlurredBitmapAsync(rawBitmap, this.style.blurLevel);
        }
        invalidate();
    }

    public MusicCardStyle getStyle() {
        return style;
    }

    public void setPreviewImageUri(Uri uri) {
        if (uri == null) return;
        new Thread(() -> {
            Bitmap bm = decodeSampledFromUri(getContext(), uri, 960);
            if (bm != null) {
                Bitmap blurred = createBlurredBitmap(bm, style.blurLevel);
                post(() -> {
                    rawBitmap = bm;
                    blurredBgBitmap = blurred;
                    cachedBlurLevel = style.blurLevel;
                    invalidate();
                });
            }
        }).start();
    }

    private void loadMediaAsync(Media m) {
        final int targetBlur = style.blurLevel;
        new Thread(() -> {
            File f = new File(getContext().getFilesDir(), m.path);
            Bitmap bm = decodeSampledFromFile(f, 960);
            if (bm != null) {
                Bitmap blurred = createBlurredBitmap(bm, targetBlur);
                post(() -> {
                    rawBitmap = bm;
                    blurredBgBitmap = blurred;
                    cachedBlurLevel = targetBlur;
                    invalidate();
                });
            } else {
                loadFallbackSceneAsync(record);
            }
        }).start();
    }

    private void loadFallbackSceneAsync(Record r) {
        final int targetBlur = style.blurLevel;
        final int h = (r != null && r.title != null) ? Math.abs(r.title.hashCode()) : 0;
        new Thread(() -> {
            int[] resIds = {
                    R.drawable.voice_scene_2,
                    R.drawable.voice_scene_1,
                    R.drawable.voice_scene_3,
                    R.drawable.default_cover_poster
            };
            int resId = resIds[h % resIds.length];
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bm = BitmapFactory.decodeResource(getResources(), resId, opts);
            if (bm != null) {
                Bitmap blurred = createBlurredBitmap(bm, targetBlur);
                post(() -> {
                    rawBitmap = bm;
                    blurredBgBitmap = blurred;
                    cachedBlurLevel = targetBlur;
                    invalidate();
                });
            }
        }).start();
    }

    private void rebuildBlurredBitmapAsync(Bitmap src, int blurLevel) {
        new Thread(() -> {
            Bitmap blurred = createBlurredBitmap(src, blurLevel);
            post(() -> {
                blurredBgBitmap = blurred;
                cachedBlurLevel = blurLevel;
                invalidate();
            });
        }).start();
    }

    public float computeHeightRatio() {
        return 1.03f; // 还原 IMG_3870.jpg 略微偏竖的圆角方卡比例
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (hMode != MeasureSpec.EXACTLY && w > 0) {
            h = Math.round(w * computeHeightRatio());
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float dp1 = Cards.dp(getContext(), 1f);
        float pad = dp1 * 5f;
        outerRect.set(pad, pad, w - pad, h - pad);

        float scale = outerRect.width() / (dp1 * 260f);
        scale = Math.max(0.60f, Math.min(1.35f, scale));

        float outerRadius = dp1 * 18f * scale;
        float frameStroke = dp1 * 5.2f * scale;
        innerRect.set(
                outerRect.left + frameStroke,
                outerRect.top + frameStroke,
                outerRect.right - frameStroke,
                outerRect.bottom - frameStroke
        );
        float innerRadius = Math.max(dp1 * 8f, outerRadius - frameStroke * 0.6f);

        // 1. 底部柔和阴影
        canvas.save();
        canvas.translate(dp1 * 2f, dp1 * 3.5f);
        canvas.drawRoundRect(outerRect, outerRadius, outerRadius, shadowPaint);
        canvas.restore();

        // 2. 绘制外层深灰磨砂包边 (还原 IMG_3870.jpg 外框)
        canvas.drawRoundRect(outerRect, outerRadius, outerRadius, framePaint);

        // 3. 裁剪内层画布区域
        innerClipPath.reset();
        innerClipPath.addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(innerClipPath);

        // 4. 绘制模糊后的背景图片 (若尚未解码完成则用唱片渐变色兜底)
        Bitmap bgToDraw = (blurredBgBitmap != null) ? blurredBgBitmap : rawBitmap;
        if (bgToDraw != null && !bgToDraw.isRecycled()) {
            drawCenterCropBitmap(canvas, bgToDraw, innerRect);
        } else {
            int c1 = (record != null && record.c1 != 0) ? record.c1 : 0xFF28343A;
            int c2 = (record != null && record.c2 != 0) ? record.c2 : 0xFF5A4E40;
            LinearGradient fallbackGrad = new LinearGradient(
                    innerRect.left, innerRect.top, innerRect.right, innerRect.bottom,
                    c1, c2, Shader.TileMode.CLAMP);
            scrimPaint.setShader(fallbackGrad);
            canvas.drawRect(innerRect, scrimPaint);
            scrimPaint.setShader(null);
        }

        // 5. 叠加半透明暗调压暗遮罩 (让前景白色歌词如 IMG_3870.jpg 一样通透醒目，同时保留背景画面通透感)
        LinearGradient dimGrad = new LinearGradient(
                innerRect.left, innerRect.top, innerRect.left, innerRect.bottom,
                new int[]{0x4E0B0D0E, 0x620B0D0E, 0x7E08090A},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        scrimPaint.setShader(dimGrad);
        canvas.drawRect(innerRect, scrimPaint);
        scrimPaint.setShader(null);

        // 6. 绘制左上角：清晰小方形专辑封面 + 歌名 + 歌手名 (严格还原 IMG_3870.jpg 左上角布局)
        float contentPadX = dp1 * 18f * scale;
        float contentPadTop = dp1 * 18f * scale;
        float thumbSize = dp1 * 34f * scale;
        thumbRect.set(
                innerRect.left + contentPadX,
                innerRect.top + contentPadTop,
                innerRect.left + contentPadX + thumbSize,
                innerRect.top + contentPadTop + thumbSize
        );
        float thumbRad = dp1 * 5.5f * scale;

        canvas.save();
        thumbClipPath.reset();
        thumbClipPath.addRoundRect(thumbRect, thumbRad, thumbRad, Path.Direction.CW);
        canvas.clipPath(thumbClipPath);
        if (rawBitmap != null && !rawBitmap.isRecycled()) {
            drawCenterCropBitmap(canvas, rawBitmap, thumbRect);
        } else {
            scrimPaint.setColor(0xFF3E4A45);
            canvas.drawRect(thumbRect, scrimPaint);
        }
        canvas.restore();

        // 小封面外圈细腻白边
        thumbBorderPaint.setStrokeWidth(dp1 * 1.15f * scale);
        canvas.drawRoundRect(thumbRect, thumbRad, thumbRad, thumbBorderPaint);

        // 小封面右侧的歌名与歌手
        String songTitle = (record != null && record.title != null && !record.title.trim().isEmpty())
                ? record.title.trim() : L10n.s(R.string.ui_170);
        String artistName = (record != null && record.sub != null && !record.sub.trim().isEmpty())
                ? record.sub.trim() : "Mayday";

        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        titlePaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(13.5f * scaledDensity * scale);

        artistPaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.NORMAL));
        artistPaint.setColor(0xE0FFFFFF);
        artistPaint.setTextSize(11.8f * scaledDensity * scale);

        float metaLeft = thumbRect.right + dp1 * 10f * scale;
        float maxMetaW = Math.max(dp1 * 40f, innerRect.right - contentPadX - metaLeft);
        String fittedTitle = TextUtils.ellipsize(songTitle, titlePaint, maxMetaW, TextUtils.TruncateAt.END).toString();
        String fittedArtist = TextUtils.ellipsize(artistName, artistPaint, maxMetaW, TextUtils.TruncateAt.END).toString();

        canvas.drawText(fittedTitle, metaLeft, thumbRect.top + thumbSize * 0.43f, titlePaint);
        canvas.drawText(fittedArtist, metaLeft, thumbRect.top + thumbSize * 0.86f, artistPaint);

        // 7. 绘制中下部大号歌词排版 (支持自定义导入字体与粗斜体风格，无底部 Spotify 标志)
        String rawLyrics = (record != null && record.note != null && !record.note.trim().isEmpty())
                ? record.note.trim()
                : L10n.s(R.string.ui_171);

        Typeface baseTf = FontManager.resolve(getContext(), style.fontId);
        if (style.textWeight == MusicCardStyle.WEIGHT_BOLD_ITALIC) {
            lyricPaint.setTypeface(Typeface.create(baseTf, Typeface.BOLD_ITALIC));
            lyricPaint.setFakeBoldText(true);
            lyricPaint.setTextSkewX(-0.14f); // 即使导入的自定义中文字体无原生斜体，也能呈现 IMG_3870.jpg 的动感斜体效果
        } else if (style.textWeight == MusicCardStyle.WEIGHT_BOLD) {
            lyricPaint.setTypeface(Typeface.create(baseTf, Typeface.BOLD));
            lyricPaint.setFakeBoldText(true);
            lyricPaint.setTextSkewX(0f);
        } else {
            lyricPaint.setTypeface(baseTf);
            lyricPaint.setFakeBoldText(false);
            lyricPaint.setTextSkewX(0f);
        }
        lyricPaint.setColor(0xFFFFFFFF);
        lyricPaint.setShadowLayer(dp1 * 3f, 0f, dp1 * 1f, 0x55000000);

        float lyricTop = thumbRect.bottom + dp1 * 18f * scale;
        float lyricBottom = innerRect.bottom - dp1 * 20f * scale;
        float availH = Math.max(dp1 * 50f, lyricBottom - lyricTop);
        int availW = Math.max(60, Math.round(innerRect.width() - contentPadX * 2f));

        float lyricPx = style.lyricSizeSp * scaledDensity * scale;
        lyricPaint.setTextSize(lyricPx);

        StaticLayout lyricLayout = StaticLayout.Builder
                .obtain(rawLyrics, 0, rawLyrics.length(), lyricPaint, availW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.24f)
                .setIncludePad(false)
                .build();

        while (lyricLayout.getHeight() > availH && lyricPx > dp1 * 10.5f) {
            lyricPx *= 0.90f;
            lyricPaint.setTextSize(lyricPx);
            lyricLayout = StaticLayout.Builder
                    .obtain(rawLyrics, 0, rawLyrics.length(), lyricPaint, availW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.20f)
                    .setIncludePad(false)
                    .build();
        }

        // 垂直居中偏上排布歌词，底部干净留白（无 Spotify 标志）
        float drawY = lyricTop + Math.max(0f, (availH - lyricLayout.getHeight()) * 0.35f);
        canvas.save();
        canvas.translate(innerRect.left + contentPadX, drawY);
        lyricLayout.draw(canvas);
        canvas.restore();

        canvas.restore();
    }

    private void drawCenterCropBitmap(Canvas canvas, Bitmap bm, RectF dst) {
        int bw = bm.getWidth();
        int bh = bm.getHeight();
        if (bw <= 0 || bh <= 0) return;
        float dstW = dst.width();
        float dstH = dst.height();
        float srcRatio = bw / (float) bh;
        float dstRatio = dstW / dstH;
        Rect srcRect = new Rect();
        if (srcRatio > dstRatio) {
            int cropW = Math.round(bh * dstRatio);
            int xOff = (bw - cropW) / 2;
            srcRect.set(xOff, 0, xOff + cropW, bh);
        } else {
            int cropH = Math.round(bw / dstRatio);
            int yOff = (bh - cropH) / 2;
            srcRect.set(0, yOff, bw, yOff + cropH);
        }
        canvas.drawBitmap(bm, srcRect, dst, bitmapPaint);
    }

    /**
     * 对背景图片进行平滑缩放 + 多趟盒式高斯近似模糊:
     * - BLUR_SUBTLE (0): 仿 IMG_3870.jpg 轻度模糊，既突出前景白字又隐约保留画面人物与景致；
     * - BLUR_MEDIUM (1): 中度柔焦模糊；
     * - BLUR_DEEP   (2): 深度氛围模糊；
     * - BLUR_CLEAR  (3): 不模糊原图。
     */
    private static Bitmap createBlurredBitmap(Bitmap src, int blurLevel) {
        if (src == null || src.isRecycled()) return null;
        if (blurLevel == MusicCardStyle.BLUR_CLEAR) return src;

        // 轻度模糊 (BLUR_SUBTLE): 高分辨率 (420px) + 仅 1 遍 1px 微柔焦，清晰保留人物与景物细节
        int targetShortSide = (blurLevel == MusicCardStyle.BLUR_SUBTLE) ? 420
                : (blurLevel == MusicCardStyle.BLUR_MEDIUM) ? 240 : 135;
        int radius = (blurLevel == MusicCardStyle.BLUR_SUBTLE) ? 1
                : (blurLevel == MusicCardStyle.BLUR_MEDIUM) ? 2 : 5;
        int passes = (blurLevel == MusicCardStyle.BLUR_SUBTLE) ? 1 : 2;

        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) return src;
        float ratio = (float) targetShortSide / Math.min(sw, sh);
        int w = Math.max(16, Math.round(sw * ratio));
        int h = Math.max(16, Math.round(sh * ratio));

        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] pixels = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);

        int[] temp = new int[w * h];
        for (int pass = 0; pass < passes; pass++) {
            boxBlurHorizontal(pixels, temp, w, h, radius);
            boxBlurVertical(temp, pixels, w, h, radius);
        }

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        return out;
    }

    private static void boxBlurHorizontal(int[] src, int[] dst, int w, int h, int radius) {
        int div = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int ix = Math.max(0, Math.min(w - 1, x + k));
                    int c = src[rowOffset + ix];
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                }
                dst[rowOffset + x] = 0xFF000000 | ((r / div) << 16) | ((g / div) << 8) | (b / div);
            }
        }
    }

    private static void boxBlurVertical(int[] src, int[] dst, int w, int h, int radius) {
        int div = radius * 2 + 1;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int iy = Math.max(0, Math.min(h - 1, y + k));
                    int c = src[iy * w + x];
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                }
                dst[y * w + x] = 0xFF000000 | ((r / div) << 16) | ((g / div) << 8) | (b / div);
            }
        }
    }

    private static Bitmap decodeSampledFromFile(File f, int maxDim) {
        if (f == null || !f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            o.inSampleSize = calcSampleSize(o.outWidth, o.outHeight, maxDim);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decodeSampledFromUri(Context c, Uri uri, int maxDim) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            o.inSampleSize = calcSampleSize(o.outWidth, o.outHeight, maxDim);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int calcSampleSize(int w, int h, int maxDim) {
        int s = 1;
        while (w / s > maxDim || h / s > maxDim) s *= 2;
        return Math.max(1, s);
    }
}
