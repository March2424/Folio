package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;

import java.io.File;
import java.io.InputStream;
import java.util.Random;

/**
 * 声音记忆视图 (Voice / 声音模块):
 * 1. 外部散落卡片模式 (默认, 1:1 模仿 example/IMG_3860.jpg):
 *    - 上方为大圆角顶边(左上/右上圆角,底部直角)的氛围感画面照片，右下角带白色胶囊角标(显示时长或 VOICE)
 *    - 下方在黑色/暗色背景上展示浅灰日期 (如 2026-10-06) 与粗体白色标题
 * 2. 录音/试听胶囊声波条模式 (editorBarMode = true, 用于 VoiceEditActivity 录音与试听反馈):
 *    - 暗色磨砂胶囊容器 + 播放/录音状态图标 + 时间 + 动态声波/进度条
 */
public class VoiceTapeView extends View {
    private static final int[] DEFAULT_SCENE_RES = {
            R.drawable.voice_scene_1,
            R.drawable.voice_scene_2,
            R.drawable.voice_scene_3
    };
    private static final Bitmap[] sDefaultScenes = new Bitmap[3];

    private final Paint paint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    private Record record;
    private String title = L10n.s(R.string.ui_000);
    private String subtitle = "";
    private String dateText = "2026-10-06";
    private String durationText = "00:30";
    private String sourceTag = "VOICE";
    private int accentColor = 0xFF4A6670;
    private int secondaryColor = 0xFFD9A464;
    private boolean compact = false;
    private boolean editorBarMode = false;

    private Bitmap customCoverBitmap;
    private int fallbackSceneIdx = 0;

    private final float[] waveBars = new float[36];
    private float progress = 0f;       // 0..1 播放进度
    private boolean playing = false;   // 是否正在播放
    private boolean recording = false; // 是否处于录音状态
    private float animPhase = 0f;

    public VoiceTapeView(Context c) {
        super(c);
        seedWave("dontwannaforget");
    }

    public void setEditorBarMode(boolean editorBarMode) {
        this.editorBarMode = editorBarMode;
        requestLayout();
        invalidate();
    }

    public void bind(Record r, boolean compact) {
        this.record = r;
        this.compact = compact;
        this.title = (r != null && r.title != null && !r.title.isEmpty()) ? r.title : L10n.s(R.string.ui_000);
        this.subtitle = (r != null && r.sub != null && !r.sub.isEmpty()) ? r.sub : "";
        this.dateText = (r != null && r.date != null && !r.date.isEmpty())
                ? r.date.replace('.', '-') : "2026-10-06";
        this.durationText = (r != null && r.fields != null && r.fields.length > 0 && !r.fields[0].isEmpty())
                ? r.fields[0] : "00:30";
        this.sourceTag = (r != null && r.fields != null && r.fields.length > 1 && !r.fields[1].isEmpty())
                ? r.fields[1] : "VOICE";
        this.accentColor = (r != null && r.c1 != 0) ? r.c1 : 0xFF4A6670;
        this.secondaryColor = (r != null && r.c2 != 0) ? r.c2 : 0xFFD9A464;

        int h = (r != null && r.title != null) ? Math.abs(r.title.hashCode()) : 0;
        this.fallbackSceneIdx = h % DEFAULT_SCENE_RES.length;
        seedWave((r != null ? r.id : "") + "|" + this.title);

        if (r != null && r.id != null) {
            Media cov = MediaIndex.first(r.id, Media.COVER);
            if (cov != null) {
                loadCoverMediaAsync(cov);
            } else {
                customCoverBitmap = null;
            }
        }
        invalidate();
    }

    public void setPreviewCoverUri(Uri uri) {
        if (uri == null) {
            customCoverBitmap = null;
            invalidate();
            return;
        }
        new Thread(() -> {
            Bitmap bm = decodeSampledFromUri(getContext(), uri, 960);
            post(() -> {
                customCoverBitmap = bm;
                invalidate();
            });
        }).start();
    }

    private void loadCoverMediaAsync(Media m) {
        new Thread(() -> {
            File f = new File(getContext().getFilesDir(), m.path);
            Bitmap bm = decodeSampledFromFile(f, 960);
            post(() -> {
                customCoverBitmap = bm;
                invalidate();
            });
        }).start();
    }

    private Bitmap getFallbackSceneBitmap() {
        int idx = Math.abs(fallbackSceneIdx) % DEFAULT_SCENE_RES.length;
        if (sDefaultScenes[idx] == null || sDefaultScenes[idx].isRecycled()) {
            try {
                sDefaultScenes[idx] = BitmapFactory.decodeResource(getResources(), DEFAULT_SCENE_RES[idx]);
            } catch (Exception ignored) {}
        }
        return sDefaultScenes[idx];
    }

    public void setMeta(String title, String subtitle, String durationText, String sourceTag) {
        if (title != null && !title.isEmpty()) this.title = title;
        if (subtitle != null) this.subtitle = subtitle;
        if (durationText != null) this.durationText = durationText;
        if (sourceTag != null) this.sourceTag = sourceTag;
        invalidate();
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        this.recording = false;
        invalidate();
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
        this.playing = recording;
        invalidate();
    }

    public void setProgress(float p) {
        this.progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    /** 录音时推入实时音量振幅(0..1),产生滚动声波 */
    public void pushLiveAmplitude(float amp) {
        float clamped = Math.max(0.12f, Math.min(1f, amp));
        System.arraycopy(waveBars, 1, waveBars, 0, waveBars.length - 1);
        waveBars[waveBars.length - 1] = clamped;
        progress = 1f;
        invalidate();
    }

    private void seedWave(String seedStr) {
        Random rnd = new Random(seedStr.hashCode() * 31L + 7L);
        for (int i = 0; i < waveBars.length; i++) {
            float env = (float) Math.sin(Math.PI * (i + 2) / (waveBars.length + 3));
            waveBars[i] = 0.22f + rnd.nextFloat() * 0.72f * (0.45f + 0.55f * env);
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (editorBarMode) {
            drawEditorPillBar(cv, w, h);
        } else {
            drawExternalPhotoCard(cv, w, h);
        }
    }

    /**
     * 外部卡片形态 (1:1 模仿 example/IMG_3860.jpg):
     * - 上半部: 顶部大圆角、底部直角的摄影画面 + 右下角白色胶囊标签 (显示时长或 FREE/VOICE)
     * - 下半部: 暗色背景上的浅灰日期 (如 2026-10-06) + 白色粗体标题
     */
    private void drawExternalPhotoCard(Canvas cv, int w, int h) {
        float d = getResources().getDisplayMetrics().density;
        float textAreaH = (compact ? 46f : 62f) * d;
        float imgH = Math.max(40f * d, h - textAreaH);

        // 1. 绘制上圆角下直角的画面照片区域 (还原 IMG_3860.jpg)
        float topRadius = (compact ? 16f : 22f) * d;
        rect.set(0, 0, w, imgH);
        clipPath.reset();
        float[] radii = new float[]{
                topRadius, topRadius, // 左上
                topRadius, topRadius, // 右上
                0f, 0f,               // 右下直角
                0f, 0f                // 左下直角
        };
        clipPath.addRoundRect(rect, radii, Path.Direction.CW);

        cv.save();
        cv.clipPath(clipPath);

        Bitmap bm = (customCoverBitmap != null && !customCoverBitmap.isRecycled())
                ? customCoverBitmap : getFallbackSceneBitmap();
        if (bm != null && !bm.isRecycled()) {
            drawCenterCropBitmap(cv, bm, rect);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, w, imgH, accentColor, secondaryColor, Shader.TileMode.CLAMP));
            cv.drawRect(rect, paint);
            paint.setShader(null);
        }

        // 右下角白色胶囊标签 (还原 IMG_3860.jpg 右下角的白色圆角胶囊)
        String badgeText = (durationText != null && !durationText.isEmpty() && !"00:00".equals(durationText))
                ? durationText : "VOICE";
        paint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        paint.setTextSize((compact ? 9.5f : 11f) * d);
        float bw = paint.measureText(badgeText) + (compact ? 16f : 22f) * d;
        float bh = (compact ? 18f : 22f) * d;
        float bx = w - bw - (compact ? 10f : 14f) * d;
        float by = imgH - bh - (compact ? 10f : 12f) * d;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF5F7F5F0);
        RectF badgeRect = new RectF(bx, by, bx + bw, by + bh);
        cv.drawRoundRect(badgeRect, bh / 2f, bh / 2f, paint);

        paint.setColor(0xFF141515);
        paint.setTextAlign(Paint.Align.CENTER);
        cv.drawText(badgeText, badgeRect.centerX(), badgeRect.centerY() + (compact ? 3.3f : 3.8f) * d, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        cv.restore();

        // 2. 图片下方: 浅灰日期 + 白色粗体标题 (还原 IMG_3860.jpg 底部排版)
        float dateY = imgH + (compact ? 18f : 24f) * d;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF7D807F);
        paint.setTypeface(AppFonts.regular());
        paint.setTextSize((compact ? 10f : 12f) * d);
        paint.setTextAlign(Paint.Align.LEFT);
        cv.drawText(dateText, 2f * d, dateY, paint);

        float titleY = dateY + (compact ? 19f : 26f) * d;
        paint.setColor(0xFFF4F2EE);
        paint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        paint.setTextSize((compact ? 13.5f : 17f) * d);
        String maxTitle = title;
        int limit = compact ? 10 : 14;
        if (maxTitle.length() > limit) {
            maxTitle = maxTitle.substring(0, limit - 1) + "…";
        }
        cv.drawText(maxTitle, 2f * d, titleY, paint);
    }

    /**
     * 胶囊播放器/录音条模式 (模仿 voiceexp1.jpg / voiceexp2.jpg 的圆角胶囊条，适配暗色背景)
     */
    private void drawEditorPillBar(Canvas cv, int w, int h) {
        float d = getResources().getDisplayMetrics().density;
        rect.set(d, d, w - d, h - d);
        float radius = rect.height() / 2f;

        // 暗色磨砂胶囊底板
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF232625);
        cv.drawRoundRect(rect, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(d);
        paint.setColor(recording ? 0x88D94E43 : 0xFF373B39);
        cv.drawRoundRect(rect, radius, radius, paint);

        float cy = rect.centerY();
        float leftX = rect.left + 22f * d;

        // 左侧状态图标: 录音中为红点，播放中为双竖线暂停，静止为三角播放
        paint.setStyle(Paint.Style.FILL);
        if (recording) {
            paint.setColor(0xFFE05246);
            cv.drawCircle(leftX + 5f * d, cy, 6f * d, paint);
        } else if (playing) {
            paint.setColor(0xFFF4F2EE);
            cv.drawRoundRect(leftX, cy - 7f * d, leftX + 3.5f * d, cy + 7f * d, d, d, paint);
            cv.drawRoundRect(leftX + 7f * d, cy - 7f * d, leftX + 10.5f * d, cy + 7f * d, d, d, paint);
        } else {
            paint.setColor(0xFFF4F2EE);
            Path tri = new Path();
            tri.moveTo(leftX, cy - 7.5f * d);
            tri.lineTo(leftX + 11.5f * d, cy);
            tri.lineTo(leftX, cy + 7.5f * d);
            tri.close();
            cv.drawPath(tri, paint);
        }

        // 时间文字
        paint.setColor(0xFFE8E4DC);
        paint.setTypeface(AppFonts.regular());
        paint.setTextSize(13f * d);
        paint.setTextAlign(Paint.Align.LEFT);
        String timeStr = durationText != null ? durationText : "0:00";
        float timeX = leftX + 22f * d;
        cv.drawText(timeStr, timeX, cy + 4.5f * d, paint);

        // 中右部: 录音时显示动态声波，平时显示极简进度轨道 (还原 voiceexp1/2.jpg)
        float trackLeft = timeX + paint.measureText(timeStr) + 16f * d;
        float trackRight = rect.right - 24f * d;
        if (trackRight > trackLeft + 30f * d) {
            if (recording) {
                int n = 24;
                float step = (trackRight - trackLeft) / n;
                float barW = Math.max(1.8f * d, step * 0.52f);
                paint.setColor(0xFFE05246);
                for (int i = 0; i < n; i++) {
                    float amp = waveBars[(i + waveBars.length - n) % waveBars.length];
                    float bh = Math.max(3f * d, amp * (rect.height() * 0.52f));
                    float bx = trackLeft + i * step;
                    cv.drawRoundRect(bx, cy - bh / 2f, bx + barW, cy + bh / 2f, barW / 2f, barW / 2f, paint);
                }
            } else {
                float trackH = 4f * d;
                paint.setColor(0xFF454947);
                cv.drawRoundRect(trackLeft, cy - trackH / 2f, trackRight, cy + trackH / 2f, trackH / 2f, trackH / 2f, paint);

                float fillW = (trackRight - trackLeft) * Math.max(0.04f, progress);
                paint.setColor(0xFFF4F2EE);
                cv.drawRoundRect(trackLeft, cy - trackH / 2f, trackLeft + fillW, cy + trackH / 2f, trackH / 2f, trackH / 2f, paint);
            }
        }

        if (playing || recording) {
            animPhase = (animPhase + 4.5f) % 360f;
            postInvalidateOnAnimation();
        }
    }

    private void drawCenterCropBitmap(Canvas canvas, Bitmap bm, RectF dst) {
        int bw = bm.getWidth(), bh = bm.getHeight();
        if (bw <= 0 || bh <= 0) return;
        float scale = Math.max(dst.width() / bw, dst.height() / bh);
        float dx = dst.centerX() - (bw * scale) / 2f;
        float dy = dst.centerY() - (bh * scale) / 2f;
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        canvas.drawBitmap(bm, m, bitmapPaint);
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
