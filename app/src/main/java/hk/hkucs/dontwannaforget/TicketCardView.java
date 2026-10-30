package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import android.view.View;

import java.io.File;
import java.io.InputStream;
import java.util.Random;

/**
 * 电影便签/海报(正面) 与 电影院票根(背面) 自定义视图:
 * - 正面支持:
 *   1. 模仿 example/layoutexp3.jpg 的便签文字排版 (标题与其余文字字体、大小不同，含穿插式/交叠式/斜签式/横幅式)
 *   2. 竖排电影海报
 *   3. 横排电影海报
 * - 容器边框支持:
 *   随机齿痕或撕裂、细密锯齿齿痕、侧边不规则手撕纹路、四周手撕毛边、复古便签+NEW角标贴纸
 * - 背面支持:
 *   5 套电影院票根模版 (经典红白热敏票 / 百老汇复古胶片票 / IMAX黑金珍藏票 / 艺术影展手写票 / 扫描票根原件全幅)
 *   并可将拍照/扫描上传的实体电影票根自动转为高对比扫描件融合在票根背面。
 */
public class TicketCardView extends View {

    private Record record;
    private TicketStyle style = new TicketStyle();
    private boolean showingBack = false;
    private boolean compactListMode = false;

    private Bitmap coverBitmap;
    private Bitmap ticketScanBitmap;

    private final Paint paperPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scanFilterPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint titlePaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bodyPaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint metaPaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);

    private final Path paperPath = new Path();
    private final RectF contentBounds = new RectF();

    public TicketCardView(Context context) {
        super(context);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x44000000);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Cards.dp(context, 0.9f));

        // 高对比度黑白热敏票据扫描增强滤镜
        ColorMatrix cm = new ColorMatrix(new float[]{
                0.55f, 0.55f, 0.55f, 0, -75f,
                0.55f, 0.55f, 0.55f, 0, -75f,
                0.52f, 0.52f, 0.50f, 0, -70f,
                0,     0,     0,     1,   0
        });
        scanFilterPaint.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    public void bind(Record r, TicketStyle customStyle, boolean showBack, boolean compact) {
        this.record = r;
        this.style = customStyle != null ? customStyle : TicketStyle.fromRecord(r);
        this.showingBack = showBack;
        this.compactListMode = compact;

        if (r != null && r.id != null) {
            Media cov = MediaIndex.first(r.id, Media.COVER);
            if (cov != null) loadMediaAsync(cov, true);
            else coverBitmap = null;

            Media orig = MediaIndex.first(r.id, Media.ORIGINAL);
            if (orig != null) loadMediaAsync(orig, false);
            else ticketScanBitmap = null;
        }
        requestLayout();
        invalidate();
    }

    public void setShowingBack(boolean back) {
        this.showingBack = back;
        requestLayout();
        invalidate();
    }

    public boolean isShowingBack() {
        return showingBack;
    }

    public void setStyle(TicketStyle s) {
        this.style = s != null ? s : new TicketStyle();
        requestLayout();
        invalidate();
    }

    public TicketStyle getStyle() {
        return style;
    }

    public void setPreviewCoverUri(Uri uri) {
        if (uri == null) {
            coverBitmap = null;
            invalidate();
            return;
        }
        new Thread(() -> {
            Bitmap bm = decodeSampledFromUri(getContext(), uri, 1080);
            post(() -> {
                coverBitmap = bm;
                invalidate();
            });
        }).start();
    }

    public void setPreviewTicketScanUri(Uri uri) {
        if (uri == null) {
            ticketScanBitmap = null;
            invalidate();
            return;
        }
        new Thread(() -> {
            Bitmap bm = decodeSampledFromUri(getContext(), uri, 1080);
            post(() -> {
                ticketScanBitmap = bm;
                invalidate();
            });
        }).start();
    }

    private void loadMediaAsync(Media m, boolean isCover) {
        new Thread(() -> {
            File f = new File(getContext().getFilesDir(), m.path);
            Bitmap bm = decodeSampledFromFile(f, 960);
            post(() -> {
                if (isCover) coverBitmap = bm;
                else ticketScanBitmap = bm;
                invalidate();
            });
        }).start();
    }

    /** 根据当前正面/背面模式计算卡片理想宽高比 (高 / 宽) */
    public float computeHeightRatio() {
        if (showingBack) {
            return 1.38f;
        }
        if (style.frontMode == TicketStyle.FRONT_POSTER_PORTRAIT) {
            return 1.36f;
        } else if (style.frontMode == TicketStyle.FRONT_POSTER_LANDSCAPE) {
            return 0.68f;
        } else {
            // 便签文字排版: 根据 4 种版式返回真实便签长宽比 (还原 layoutexp3.jpg)
            switch (style.noteLayout) {
                case TicketStyle.LAYOUT_BANNER_STRIP:
                    return 0.42f; // 仿 About 窄幅横签
                case TicketStyle.LAYOUT_TOP_TILTED:
                    return 0.92f; // 仿 親愛的男孩 偏方斜签
                case TicketStyle.LAYOUT_RIGHT_OVERLAP:
                    return 0.74f; // 仿 麻雀 / 弓箭 宽便签
                case TicketStyle.LAYOUT_CENTER_INSERT:
                default:
                    return 0.70f; // 仿 筆記 / 陽台 经典横便签
            }
        }
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

        if (showingBack) {
            drawCinemaTicketBack(canvas, w, h);
        } else {
            drawFrontNoteOrPoster(canvas, w, h);
        }
    }

    // =========================================================================
    // 正面渲染: 模仿 layoutexp3.jpg 的齿痕/撕裂便签文字排版 或 横竖排电影海报
    // =========================================================================
    private void drawFrontNoteOrPoster(Canvas canvas, int w, int h) {
        float dp1 = Cards.dp(getContext(), 1);
        float pad = dp1 * 6f;
        contentBounds.set(pad, pad, w - pad, h - pad);

        int seed = (record != null && record.title != null) ? record.title.hashCode() : 4234;
        int edge = style.resolvedEdgeStyle(seed);
        buildNoteContainerPath(contentBounds, edge, seed, dp1, paperPath);

        // 1. 绘制纸片底部阴影
        canvas.save();
        canvas.translate(dp1 * 2f, dp1 * 3.5f);
        canvas.drawPath(paperPath, shadowPaint);
        canvas.restore();

        // 2. 确定纸张底色与文字主副色 (还原 layoutexp3.jpg 的冷雾灰、暗夜炭黑、复古姜黄、暖米白)
        int paperColor, inkMain, inkSub, inkMeta;
        switch (style.paperTone) {
            case TicketStyle.TONE_CHARCOAL:
                paperColor = 0xFF333636;
                inkMain = 0xFF969A98;
                inkSub = 0xFF7D817F;
                inkMeta = 0xFF646866;
                break;
            case TicketStyle.TONE_OCHRE:
                paperColor = 0xFFA89A64;
                inkMain = 0xFF231D15;
                inkSub = 0xFF3D3426;
                inkMeta = 0xFF6B5F42;
                break;
            case TicketStyle.TONE_WARM_CREAM:
                paperColor = 0xFFCBC7BE;
                inkMain = 0xFF1A1918;
                inkSub = 0xFF3A3835;
                inkMeta = 0xFF75716A;
                break;
            case TicketStyle.TONE_MIST_GREY:
            default:
                paperColor = 0xFF8F9392;
                inkMain = 0xFF141616;
                inkSub = 0xFF242727;
                inkMeta = 0xFF5A5E5D;
                break;
        }

        paperPaint.setStyle(Paint.Style.FILL);
        paperPaint.setColor(paperColor);
        canvas.drawPath(paperPath, paperPaint);

        // 3. 裁剪进入不规则齿痕/撕裂纸片容器内部
        canvas.save();
        canvas.clipPath(paperPath);

        // 叠加细微纸张纤维杂点感
        drawSubtlePaperTexture(canvas, contentBounds, seed, dp1);

        if (style.frontMode == TicketStyle.FRONT_POSTER_PORTRAIT
                || style.frontMode == TicketStyle.FRONT_POSTER_LANDSCAPE) {
            drawPosterInsideContainer(canvas, contentBounds, dp1, inkMain, inkMeta);
        } else {
            drawTypographyNoteInsideContainer(canvas, contentBounds, dp1, inkMain, inkSub, inkMeta);
        }

        // 4. 若勾选了 NEW 角标贴纸或为 EDGE_CLEAN_TAPE + showNewBadge，在左上角绘制黄色斜贴条 (还原 layoutexp3.jpg「麻雀」)
        if (style.showNewBadge) {
            drawNewCornerTape(canvas, contentBounds, dp1);
        }

        canvas.restore();

        // 轻微纸张边缘高光描边
        borderPaint.setColor(style.paperTone == TicketStyle.TONE_CHARCOAL ? 0x22FFFFFF : 0x28000000);
        canvas.drawPath(paperPath, borderPaint);
    }

    /**
     * 构建带齿痕(锯齿孔)或不规则手撕纹路的便签纸外轮廓 Path
     */
    private void buildNoteContainerPath(RectF r, int edgeStyle, int seed, float dp1, Path out) {
        out.reset();
        Random rnd = new Random(seed ^ 0x5DEECE66DL);

        if (edgeStyle == TicketStyle.EDGE_TEETH) {
            // 细密齿痕边缘 (模仿右上「親愛的男孩」与票据锯齿边缘)
            float toothW = dp1 * 5.5f;
            float toothDepth = dp1 * 2.8f;
            out.moveTo(r.left, r.top);
            // 上边: 轻微自然起伏
            addWavyEdge(out, r.left, r.top, r.right, r.top, dp1 * 0.8f, 12, rnd);
            // 右边: 锯齿齿痕
            addSawtoothEdge(out, r.right, r.top, r.right, r.bottom, toothW, toothDepth, true);
            // 下边: 锯齿齿痕
            addSawtoothEdge(out, r.right, r.bottom, r.left, r.bottom, toothW, toothDepth, true);
            // 左边: 锯齿齿痕
            addSawtoothEdge(out, r.left, r.bottom, r.left, r.top, toothW, toothDepth, true);
            out.close();
        } else if (edgeStyle == TicketStyle.EDGE_TORN_SIDE) {
            // 侧边不规则手撕纹路 (模仿「弓箭」右侧撕裂与「About」左侧撕裂)
            boolean tearLeft = (style.noteLayout == TicketStyle.LAYOUT_BANNER_STRIP) || (Math.abs(seed) % 2 == 1);
            out.moveTo(r.left, r.top);
            addWavyEdge(out, r.left, r.top, r.right, r.top, dp1 * 0.6f, 10, rnd);
            if (!tearLeft) {
                addTornEdge(out, r.right, r.top, r.right, r.bottom, dp1 * 4.5f, 22, rnd);
            } else {
                addWavyEdge(out, r.right, r.top, r.right, r.bottom, dp1 * 0.6f, 10, rnd);
            }
            addWavyEdge(out, r.right, r.bottom, r.left, r.bottom, dp1 * 0.7f, 10, rnd);
            if (tearLeft) {
                addTornEdge(out, r.left, r.bottom, r.left, r.top, dp1 * 4.5f, 20, rnd);
            } else {
                addWavyEdge(out, r.left, r.bottom, r.left, r.top, dp1 * 0.6f, 10, rnd);
            }
            out.close();
        } else if (edgeStyle == TicketStyle.EDGE_TORN_ALL) {
            // 四周不规则手撕毛边
            out.moveTo(r.left, r.top);
            addTornEdge(out, r.left, r.top, r.right, r.top, dp1 * 2.6f, 24, rnd);
            addTornEdge(out, r.right, r.top, r.right, r.bottom, dp1 * 3.2f, 20, rnd);
            addTornEdge(out, r.right, r.bottom, r.left, r.bottom, dp1 * 2.8f, 24, rnd);
            addTornEdge(out, r.left, r.bottom, r.left, r.top, dp1 * 3.2f, 20, rnd);
            out.close();
        } else {
            // 复古微毛边便签 (仿「筆記 / 陽台 / 麻雀」)
            out.moveTo(r.left, r.top);
            addWavyEdge(out, r.left, r.top, r.right, r.top, dp1 * 0.5f, 14, rnd);
            addWavyEdge(out, r.right, r.top, r.right, r.bottom, dp1 * 0.5f, 12, rnd);
            addWavyEdge(out, r.right, r.bottom, r.left, r.bottom, dp1 * 0.5f, 14, rnd);
            addWavyEdge(out, r.left, r.bottom, r.left, r.top, dp1 * 0.5f, 12, rnd);
            out.close();
        }
    }

    private void addSawtoothEdge(Path p, float x1, float y1, float x2, float y2,
                                 float stepLen, float depth, boolean inward) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        int count = Math.max(4, Math.round(len / stepLen));
        float ux = dx / count, uy = dy / count;
        // 法向量指向内部
        float nx = -dy / len * (inward ? depth : -depth);
        float ny = dx / len * (inward ? depth : -depth);
        for (int i = 0; i < count; i++) {
            float mx = x1 + (i + 0.5f) * ux + nx;
            float my = y1 + (i + 0.5f) * uy + ny;
            float ex = x1 + (i + 1f) * ux;
            float ey = y1 + (i + 1f) * uy;
            p.lineTo(mx, my);
            p.lineTo(ex, ey);
        }
    }

    private void addTornEdge(Path p, float x1, float y1, float x2, float y2,
                             float maxJitter, int segments, Random rnd) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float nx = -dy / len, ny = dx / len;
        float offset = 0f;
        for (int i = 1; i < segments; i++) {
            float t = i / (float) segments;
            offset = offset * 0.45f + (rnd.nextFloat() * 2f - 1f) * maxJitter;
            // 偶发深撕裂缺口
            if (rnd.nextFloat() < 0.22f) {
                offset += (rnd.nextBoolean() ? 1f : -0.6f) * maxJitter * 0.85f;
            }
            p.lineTo(x1 + dx * t + nx * offset, y1 + dy * t + ny * offset);
        }
        p.lineTo(x2, y2);
    }

    private void addWavyEdge(Path p, float x1, float y1, float x2, float y2,
                             float jitter, int segments, Random rnd) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float nx = -dy / len, ny = dx / len;
        for (int i = 1; i < segments; i++) {
            float t = i / (float) segments;
            float off = (rnd.nextFloat() * 2f - 1f) * jitter;
            p.lineTo(x1 + dx * t + nx * off, y1 + dy * t + ny * off);
        }
        p.lineTo(x2, y2);
    }

    private void drawSubtlePaperTexture(Canvas canvas, RectF r, int seed, float dp1) {
        Random rnd = new Random(seed + 101);
        accentPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 28; i++) {
            accentPaint.setColor((i % 3 == 0) ? 0x10000000 : 0x0CFFFFFF);
            float cx = r.left + rnd.nextFloat() * r.width();
            float cy = r.top + rnd.nextFloat() * r.height();
            canvas.drawCircle(cx, cy, dp1 * (0.6f + rnd.nextFloat() * 1.1f), accentPaint);
        }
    }

    private void drawNewCornerTape(Canvas canvas, RectF r, float dp1) {
        canvas.save();
        float cx = r.left + dp1 * 22f;
        float cy = r.top + dp1 * 10f;
        canvas.rotate(-24f, cx, cy);
        accentPaint.setStyle(Paint.Style.FILL);
        accentPaint.setColor(0xFFC5B438); // 还原 layoutexp3.jpg「麻雀」左上角芥末黄 NEW 贴条
        RectF tape = new RectF(cx - dp1 * 18f, cy - dp1 * 6.5f, cx + dp1 * 18f, cy + dp1 * 6.5f);
        canvas.drawRect(tape, accentPaint);

        metaPaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        metaPaint.setColor(0xFF1E1D18);
        metaPaint.setTextSize(dp1 * 7.5f);
        metaPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("NEW", cx, cy + dp1 * 2.6f, metaPaint);
        metaPaint.setTextAlign(Paint.Align.LEFT);
        canvas.restore();
    }

    /**
     * 在齿痕/撕裂纸片内绘制模仿 layoutexp3.jpg 的文字便签排版:
     * 标题(title)与正文(note)具有截然不同的字体与字号对比!
     */
    private void drawTypographyNoteInsideContainer(Canvas canvas, RectF r, float dp1,
                                                   int inkMain, int inkSub, int inkMeta) {
        float scale = r.width() / (dp1 * 270f);
        scale = Math.max(0.68f, Math.min(1.35f, scale));

        String titleStr = (record != null && record.title != null && !record.title.isEmpty())
                ? record.title : L10n.s(R.string.ui_505);
        String rawNote = (record != null && record.note != null && !record.note.isEmpty())
                ? record.note : L10n.s(R.string.ui_506);
        String dateText = (record != null && record.date != null) ? record.date.replace('.', '-') : "2026-10-18";
        String serial = (style.serialCode != null && !style.serialCode.isEmpty())
                ? style.serialCode : TicketStyle.defaultSerial(record);

        // 配置大标题画笔 (粗衬线/斜体或自定义字体，字号明显大于正文)
        Typeface baseTitleTf = FontManager.resolve(getContext(), style.titleFontId);
        titlePaint.setTypeface(Typeface.create(baseTitleTf, Typeface.BOLD_ITALIC));
        titlePaint.setColor(inkMain);
        float titlePx = style.titleSizeSp * getResources().getDisplayMetrics().scaledDensity * scale;
        titlePaint.setTextSize(titlePx);

        // 配置小号正文画笔
        Typeface baseBodyTf = FontManager.resolve(getContext(), style.bodyFontId);
        bodyPaint.setTypeface(baseBodyTf);
        bodyPaint.setColor(inkSub);
        float bodyPx = style.bodySizeSp * getResources().getDisplayMetrics().scaledDensity * scale;
        bodyPaint.setTextSize(bodyPx);

        // 配置底部编号与日期画笔
        metaPaint.setTypeface(AppFonts.regular());
        metaPaint.setColor(inkMeta);
        metaPaint.setTextSize(dp1 * 8.2f * scale);
        metaPaint.setTextAlign(Paint.Align.LEFT);

        float padX = dp1 * 18f * scale;
        float padY = dp1 * 15f * scale;
        int fullTextW = Math.max(60, Math.round(r.width() - padX * 2f));

        // 将正文拆分为上下两段 (供穿插式和交叠式使用)
        String[] split = splitNoteIntoTwo(rawNote, record != null ? record.sub : "");

        if (style.noteLayout == TicketStyle.LAYOUT_BANNER_STRIP) {
            // 样式 4: 仿「About」姜黄横幅便签 — 居中粗斜体大标题 + 左下角 ID 编号
            titlePaint.setTextSize(titlePx * 1.08f);
            StaticLayout tl = StaticLayout.Builder.obtain(titleStr, 0, titleStr.length(), titlePaint, fullTextW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build();
            canvas.save();
            canvas.translate(r.left + padX, r.centerY() - tl.getHeight() / 2f - dp1 * 3f * scale);
            tl.draw(canvas);
            canvas.restore();

            canvas.drawText(serial, r.left + padX * 0.8f, r.bottom - dp1 * 10f * scale, metaPaint);
            metaPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(dateText, r.right - padX * 0.8f, r.bottom - dp1 * 10f * scale, metaPaint);
            metaPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        if (style.noteLayout == TicketStyle.LAYOUT_TOP_TILTED) {
            // 样式 3: 仿右上角「親愛的男孩」— 顶部大号标题 + 下方紧密多行独白 + 左下 ID
            StaticLayout tl = StaticLayout.Builder.obtain(titleStr, 0, titleStr.length(), titlePaint, fullTextW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build();
            float curY = r.top + padY * 0.85f;
            canvas.save();
            canvas.translate(r.left + padX, curY);
            tl.draw(canvas);
            canvas.restore();
            curY += tl.getHeight() + dp1 * 6f * scale;

            String combined = rawNote;
            if (record != null && record.sub != null && !record.sub.isEmpty() && !combined.contains(record.sub)) {
                combined = L10n.s(R.string.ui_507) + record.sub + "\n" + combined;
            }
            StaticLayout bl = StaticLayout.Builder.obtain(combined, 0, combined.length(), bodyPaint, fullTextW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.25f)
                    .setMaxLines(7)
                    .setIncludePad(false)
                    .build();
            canvas.save();
            canvas.translate(r.left + padX, curY);
            bl.draw(canvas);
            canvas.restore();

            canvas.drawText(serial, r.left + padX, r.bottom - dp1 * 10f * scale, metaPaint);
            return;
        }

        if (style.noteLayout == TicketStyle.LAYOUT_RIGHT_OVERLAP) {
            // 样式 2: 仿「麻雀 / 弓箭」— 上方左对齐导语 + 中右部超大标题与缩进段落交叠 + 底部编号日期
            float curY = r.top + padY * (style.showNewBadge ? 1.25f : 0.95f);
            StaticLayout topSl = StaticLayout.Builder.obtain(split[0], 0, split[0].length(), bodyPaint, fullTextW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.26f)
                    .setMaxLines(4)
                    .setIncludePad(false)
                    .build();
            canvas.save();
            canvas.translate(r.left + padX, curY);
            topSl.draw(canvas);
            canvas.restore();
            curY += topSl.getHeight() + dp1 * 8f * scale;

            // 左侧序号 "1" (还原「麻雀」细节)
            canvas.drawText("1", r.left + padX, curY + dp1 * 12f * scale, bodyPaint);

            // 中间偏右的大号斜体标题
            titlePaint.setTextSize(titlePx * 1.06f);
            int titleW = Math.max(50, Math.round(fullTextW * 0.72f));
            StaticLayout midTitle = StaticLayout.Builder.obtain(titleStr, 0, titleStr.length(), titlePaint, titleW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(2)
                    .setIncludePad(false)
                    .build();
            canvas.save();
            canvas.translate(r.left + r.width() * 0.34f, curY - dp1 * 2f * scale);
            midTitle.draw(canvas);
            canvas.restore();

            // 右下缩进段落与大标题形成视觉交叠
            int rightColW = Math.max(50, Math.round(r.width() * 0.50f));
            StaticLayout botSl = StaticLayout.Builder.obtain(split[1], 0, split[1].length(), bodyPaint, rightColW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.24f)
                    .setMaxLines(4)
                    .setIncludePad(false)
                    .build();
            canvas.save();
            canvas.translate(r.left + r.width() * 0.44f, curY + midTitle.getHeight() * 0.55f);
            botSl.draw(canvas);
            canvas.restore();

            canvas.drawText(serial, r.left + padX, r.bottom - dp1 * 10f * scale, metaPaint);
            metaPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(dateText, r.right - padX, r.bottom - dp1 * 10f * scale, metaPaint);
            metaPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        // 样式 1 (默认): 仿「筆記 / 陽台 / 詞彙」— 上段导语 + 居中醒目大标题 + 下段结语 + 左右底角编号与日期
        float curY = r.top + padY;
        StaticLayout topSl = StaticLayout.Builder.obtain(split[0], 0, split[0].length(), bodyPaint, fullTextW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.28f)
                .setMaxLines(3)
                .setIncludePad(false)
                .build();
        canvas.save();
        canvas.translate(r.left + padX, curY);
        topSl.draw(canvas);
        canvas.restore();
        curY += topSl.getHeight() + dp1 * 10f * scale;

        StaticLayout centerTitle = StaticLayout.Builder.obtain(titleStr, 0, titleStr.length(), titlePaint, fullTextW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setIncludePad(false)
                .build();
        canvas.save();
        canvas.translate(r.left + padX, curY);
        centerTitle.draw(canvas);
        canvas.restore();
        curY += centerTitle.getHeight() + dp1 * 10f * scale;

        StaticLayout botSl = StaticLayout.Builder.obtain(split[1], 0, split[1].length(), bodyPaint, fullTextW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.28f)
                .setMaxLines(3)
                .setIncludePad(false)
                .build();
        canvas.save();
        canvas.translate(r.left + padX, curY);
        botSl.draw(canvas);
        canvas.restore();

        // 底角编号与日期 (还原 layoutexp3.jpg: 左下 ID #004234, 右下 2026-08-06)
        canvas.drawText(serial, r.left + padX, r.bottom - dp1 * 10f * scale, metaPaint);
        metaPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(dateText, r.right - padX, r.bottom - dp1 * 10f * scale, metaPaint);
        metaPaint.setTextAlign(Paint.Align.LEFT);
    }

    private static String[] splitNoteIntoTwo(String note, String sub) {
        String clean = note != null ? note.trim() : "";
        if (clean.contains("\n")) {
            String[] parts = clean.split("\n", 2);
            return new String[]{parts[0].trim(), parts[1].trim()};
        }
        int comma = clean.indexOf('，');
        if (comma < 0) comma = clean.indexOf(',');
        if (comma < 0) comma = clean.indexOf('。');
        if (comma > 4 && comma < clean.length() - 4) {
            return new String[]{
                    clean.substring(0, comma + 1).trim(),
                    clean.substring(comma + 1).trim()
            };
        }
        String top = (sub != null && !sub.isEmpty()) ? (L10n.s(R.string.ui_508) + sub + L10n.s(R.string.ui_509)) : L10n.s(R.string.ui_510);
        String bot = clean.isEmpty() ? L10n.s(R.string.ui_511) : clean;
        return new String[]{top, bot};
    }

    /**
     * 在齿痕/撕裂纸片容器内绘制电影海报 (支持竖排与横排海报)
     */
    private void drawPosterInsideContainer(Canvas canvas, RectF r, float dp1, int inkMain, int inkMeta) {
        float border = dp1 * 8f;
        RectF imgRect = new RectF(r.left + border, r.top + border, r.right - border, r.bottom - border - dp1 * 22f);

        if (coverBitmap != null && !coverBitmap.isRecycled()) {
            drawCenterCropBitmap(canvas, coverBitmap, imgRect, bitmapPaint);
        } else {
            int c1 = record != null ? record.c1 : 0xFF2E5E8C;
            int c2 = record != null ? record.c2 : 0xFFA3322B;
            Paint gp = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            gp.setShader(new LinearGradient(imgRect.left, imgRect.top, imgRect.right, imgRect.bottom,
                    c1, c2, Shader.TileMode.CLAMP));
            canvas.drawRect(imgRect, gp);

            // 无海报图片时在渐变海报中央优雅展示片名
            String titleStr = (record != null && record.title != null) ? record.title : "MOVIE POSTER";
            titlePaint.setTypeface(Typeface.create(FontManager.resolve(getContext(), style.titleFontId), Typeface.BOLD));
            titlePaint.setColor(0xF2FFFFFF);
            titlePaint.setTextSize(dp1 * (style.frontMode == TicketStyle.FRONT_POSTER_LANDSCAPE ? 20f : 24f));
            StaticLayout sl = StaticLayout.Builder.obtain(titleStr, 0, titleStr.length(),
                            titlePaint, Math.max(40, Math.round(imgRect.width() - dp1 * 20f)))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build();
            canvas.save();
            canvas.translate(imgRect.left + dp1 * 10f, imgRect.centerY() - sl.getHeight() / 2f);
            sl.draw(canvas);
            canvas.restore();
        }

        // 海报底部留白栏: 左侧片名与导演，右侧日期与评分
        String title = (record != null && record.title != null) ? record.title : "";
        String sub = (record != null && record.sub != null && !record.sub.isEmpty()) ? (" · " + record.sub) : "";
        String dateStr = (record != null && record.date != null) ? record.date.replace('.', '-') : "";
        metaPaint.setTypeface(AppFonts.regular());
        metaPaint.setTextSize(dp1*8.5f);
        float dateWidth=metaPaint.measureText(dateStr);
        metaPaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        metaPaint.setColor(inkMain);
        metaPaint.setTextSize(dp1 * 10.5f);
        metaPaint.setTextAlign(Paint.Align.LEFT);
        String leftCaption = title + sub;
        leftCaption=android.text.TextUtils.ellipsize(leftCaption,metaPaint,
                Math.max(0,imgRect.width()-dateWidth-dp1*8),android.text.TextUtils.TruncateAt.END).toString();
        canvas.drawText(leftCaption, imgRect.left, r.bottom - dp1 * 9f, metaPaint);

        metaPaint.setTypeface(AppFonts.regular());
        metaPaint.setColor(inkMeta);
        metaPaint.setTextSize(dp1 * 8.5f);
        metaPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(dateStr, imgRect.right, r.bottom - dp1 * 9.5f, metaPaint);
        metaPaint.setTextAlign(Paint.Align.LEFT);
    }

    // =========================================================================
    // 背面渲染: 5 套电影院票根模版 + 扫描/拍照上传的票根原件
    // =========================================================================
    private void drawCinemaTicketBack(Canvas canvas, int w, int h) {
        float dp1 = Cards.dp(getContext(), 1);
        float pad = dp1 * 6f;
        contentBounds.set(pad, pad, w - pad, h - pad);

        int tpl = Math.abs(style.ticketTemplate) % 5;
        String title = (record != null && !record.title.isEmpty()) ? record.title : L10n.s(R.string.ui_512);
        String director = (record != null && !record.sub.isEmpty()) ? record.sub : L10n.s(R.string.ui_513);
        String date = (record != null && !record.date.isEmpty()) ? record.date : "2026.10.18";
        String rating = (record != null && record.rating != null && !record.rating.isEmpty()) ? record.rating : "4.8";
        String cinema = fieldAt(0, L10n.s(R.string.ui_514));
        String hall = fieldAt(1, L10n.s(R.string.ui_515));
        String seat = fieldAt(2, L10n.s(R.string.ui_516));
        String ver = fieldAt(3, L10n.s(R.string.ui_517));
        String price = fieldAt(4, "¥55");
        String time = fieldAt(5, "19:40");
        String serial = (style.serialCode != null && !style.serialCode.isEmpty())
                ? style.serialCode : TicketStyle.defaultSerial(record);

        // 构造带两侧半圆检票撕口的电影票根外轮廓
        float splitY = contentBounds.top + contentBounds.height() * 0.62f;
        buildTicketStubPath(contentBounds, splitY, dp1, paperPath);

        // 阴影
        canvas.save();
        canvas.translate(dp1 * 2f, dp1 * 3.5f);
        canvas.drawPath(paperPath, shadowPaint);
        canvas.restore();

        // 根据票根模版设定底色与主题色
        int bgCol, headerCol, inkCol, subCol, lineCol;
        if (tpl == TicketStyle.TPL_IMAX_DARK) {
            bgCol = 0xFF18191C;
            headerCol = 0xFFD4AF6A;
            inkCol = 0xFFF2ECE1;
            subCol = 0xFF9A9488;
            lineCol = 0x44D4AF6A;
        } else if (tpl == TicketStyle.TPL_BROADWAY_RETRO) {
            bgCol = 0xFFEADFC9;
            headerCol = 0xFF7A2E25;
            inkCol = 0xFF2B231B;
            subCol = 0xFF6B5B4B;
            lineCol = 0x557A2E25;
        } else if (tpl == TicketStyle.TPL_ART_HOUSE) {
            bgCol = 0xFFD7D5CE;
            headerCol = 0xFF2B2927;
            inkCol = 0xFF1A1918;
            subCol = 0xFF55524D;
            lineCol = 0x442B2927;
        } else {
            // TPL_THERMAL_RED 或 TPL_FULL_SCAN
            bgCol = 0xFFFAF6EE;
            headerCol = 0xFFB83B32;
            inkCol = 0xFF22201E;
            subCol = 0xFF6E685F;
            lineCol = 0x55B83B32;
        }

        paperPaint.setColor(bgCol);
        canvas.drawPath(paperPath, paperPaint);

        canvas.save();
        canvas.clipPath(paperPath);

        float lx = contentBounds.left + dp1 * 18f;
        float rx = contentBounds.right - dp1 * 18f;
        float curY = contentBounds.top + dp1 * 18f;

        // 模版 5: 扫描票根原件全幅模式 (优先大尺寸展示拍照/扫描的票根图像)
        if (tpl == TicketStyle.TPL_FULL_SCAN && ticketScanBitmap != null && !ticketScanBitmap.isRecycled()) {
            metaPaint.setTypeface(AppFonts.regular());
            metaPaint.setColor(headerCol);
            metaPaint.setTextSize(dp1 * 9.5f);
            canvas.drawText("SCANNED TICKET STUB · " + cinema, lx, curY + dp1 * 8f, metaPaint);

            RectF scanRect = new RectF(lx, curY + dp1 * 16f, rx, splitY - dp1 * 12f);
            drawScannedTicketImage(canvas, scanRect, dp1);
        } else {
            // 顶部影院抬头栏
            metaPaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
            metaPaint.setColor(headerCol);
            metaPaint.setTextSize(dp1 * 9.5f);
            String headTag = tpl == TicketStyle.TPL_IMAX_DARK ? ("IMAX COLLECTOR STUB · " + cinema)
                    : tpl == TicketStyle.TPL_BROADWAY_RETRO ? ("BROADWAY CINEMATHEQUE · " + cinema)
                    : tpl == TicketStyle.TPL_ART_HOUSE ? ("ART HOUSE SCREENING · " + cinema)
                    : ("ADMIT ONE · " + cinema);
            canvas.drawText(headTag, lx, curY + dp1 * 8f, metaPaint);

            // 百老汇胶片模版: 左右两侧绘制胶片齿孔装饰
            if (tpl == TicketStyle.TPL_BROADWAY_RETRO) {
                accentPaint.setColor(0x282B231B);
                for (float y = contentBounds.top + dp1 * 14f; y < contentBounds.bottom - dp1 * 14f; y += dp1 * 14f) {
                    canvas.drawRoundRect(contentBounds.left + dp1 * 4f, y, contentBounds.left + dp1 * 9f, y + dp1 * 8f, dp1 * 1.5f, dp1 * 1.5f, accentPaint);
                    canvas.drawRoundRect(contentBounds.right - dp1 * 9f, y, contentBounds.right - dp1 * 4f, y + dp1 * 8f, dp1 * 1.5f, dp1 * 1.5f, accentPaint);
                }
            }

            // 片名与导演
            curY += dp1 * 22f;
            titlePaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
            titlePaint.setColor(inkCol);
            titlePaint.setTextSize(dp1 * 22f);
            StaticLayout tl = StaticLayout.Builder.obtain(title, 0, title.length(),
                            titlePaint, Math.max(60, Math.round(rx - lx)))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(2)
                    .setIncludePad(false)
                    .build();
            canvas.save();
            canvas.translate(lx, curY);
            tl.draw(canvas);
            canvas.restore();
            curY += tl.getHeight() + dp1 * 4f;

            bodyPaint.setTypeface(AppFonts.regular());
            bodyPaint.setColor(subCol);
            bodyPaint.setTextSize(dp1 * 11.5f);
            canvas.drawText(director + "  ·  " + ver, lx, curY + dp1 * 10f, bodyPaint);
            curY += dp1 * 20f;

            // 若用户上传了扫描/拍照的实体票根，在上半部嵌入扫描件预览框
            if (ticketScanBitmap != null && !ticketScanBitmap.isRecycled()) {
                float scanBottom = splitY - dp1 * 10f;
                if (scanBottom - curY > dp1 * 45f) {
                    RectF scanBox = new RectF(lx, curY, rx, scanBottom);
                    drawScannedTicketImage(canvas, scanBox, dp1);
                }
            } else {
                // 未上传实体票根照片时，展示日期场次大字与圆形检票印章
                metaPaint.setTypeface(AppFonts.regular());
                metaPaint.setColor(inkCol);
                metaPaint.setTextSize(dp1 * 16f);
                canvas.drawText(date + "   " + time, lx, splitY - dp1 * 22f, metaPaint);

                drawTicketSealStamp(canvas, rx - dp1 * 34f, splitY - dp1 * 34f, dp1 * 24f, headerCol, rating);
            }
        }

        // 检票撕口虚线
        Paint dash = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        dash.setStyle(Paint.Style.STROKE);
        dash.setColor(lineCol);
        dash.setStrokeWidth(dp1 * 1.2f);
        dash.setPathEffect(new DashPathEffect(new float[]{dp1 * 5f, dp1 * 4f}, 0));
        canvas.drawLine(lx, splitY, rx, splitY, dash);

        // 下半部: 影厅 / 座位 / 场次 / 票价 四宫格 + 底部条形码与编号
        float gridTop = splitY + dp1 * 14f;
        float colW = (rx - lx) / 2f;
        drawTicketField(canvas, L10n.s(R.string.ui_518), hall, lx, gridTop, dp1, subCol, inkCol);
        drawTicketField(canvas, L10n.s(R.string.ui_519), seat, lx + colW, gridTop, dp1, subCol, inkCol);

        float row2Top = gridTop + dp1 * 38f;
        drawTicketField(canvas, L10n.s(R.string.ui_520), date + " " + time, lx, row2Top, dp1, subCol, inkCol);
        drawTicketField(canvas, L10n.s(R.string.ui_521), price + " (" + ver + ")", lx + colW, row2Top, dp1, subCol, inkCol);

        // 底部条形码与序列号
        float barTop = contentBounds.bottom - dp1 * 30f;
        drawBarcode(canvas, lx, barTop, rx - lx, dp1 * 14f, inkCol, serial.hashCode());
        metaPaint.setTypeface(AppFonts.regular());
        metaPaint.setColor(subCol);
        metaPaint.setTextSize(dp1 * 8f);
        metaPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(serial + "  ·  " + cinema, contentBounds.centerX(), contentBounds.bottom - dp1 * 6f, metaPaint);
        metaPaint.setTextAlign(Paint.Align.LEFT);

        canvas.restore();

        borderPaint.setColor(lineCol);
        canvas.drawPath(paperPath, borderPaint);
    }

    private void drawScannedTicketImage(Canvas canvas, RectF box, float dp1) {
        accentPaint.setStyle(Paint.Style.FILL);
        accentPaint.setColor(0xFFF4F1EA);
        canvas.drawRoundRect(box, dp1 * 4f, dp1 * 4f, accentPaint);

        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(box, dp1 * 4f, dp1 * 4f, Path.Direction.CW);
        canvas.clipPath(clip);
        drawCenterCropBitmap(canvas, ticketScanBitmap, box, style.scanEnhance ? scanFilterPaint : bitmapPaint);
        canvas.restore();

        borderPaint.setColor(0x33000000);
        canvas.drawRoundRect(box, dp1 * 4f, dp1 * 4f, borderPaint);
    }

    private void drawTicketSealStamp(Canvas canvas, float cx, float cy, float radius, int color, String rating) {
        canvas.save();
        canvas.rotate(-14f, cx, cy);
        Paint sp = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        sp.setStyle(Paint.Style.STROKE);
        sp.setColor(color);
        sp.setAlpha(180);
        sp.setStrokeWidth(radius * 0.08f);
        canvas.drawCircle(cx, cy, radius, sp);
        canvas.drawCircle(cx, cy, radius * 0.84f, sp);

        metaPaint.setColor(color);
        metaPaint.setAlpha(200);
        metaPaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        metaPaint.setTextSize(radius * 0.42f);
        metaPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("★ " + rating, cx, cy + radius * 0.15f, metaPaint);
        metaPaint.setTextAlign(Paint.Align.LEFT);
        metaPaint.setAlpha(255);
        canvas.restore();
    }

    private void drawTicketField(Canvas canvas, String label, String val, float x, float y,
                                 float dp1, int subCol, int inkCol) {
        metaPaint.setTypeface(AppFonts.regular());
        metaPaint.setColor(subCol);
        metaPaint.setTextSize(dp1 * 8.5f);
        canvas.drawText(label, x, y, metaPaint);

        bodyPaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        bodyPaint.setColor(inkCol);
        bodyPaint.setTextSize(dp1 * 12.5f);
        String v = (val == null || val.isEmpty()) ? "—" : val;
        if (v.length() > 14) v = v.substring(0, 14) + "…";
        canvas.drawText(v, x, y + dp1 * 15f, bodyPaint);
    }

    private void drawBarcode(Canvas canvas, float x, float y, float w, float h, int color, int seed) {
        Random rnd = new Random(seed);
        accentPaint.setStyle(Paint.Style.FILL);
        accentPaint.setColor(color);
        accentPaint.setAlpha(180);
        float curX = x;
        while (curX < x + w - 4f) {
            float bw = (1 + rnd.nextInt(3)) * 1.6f;
            if (curX + bw > x + w) break;
            canvas.drawRect(curX, y, curX + bw, y + h, accentPaint);
            curX += bw + (1 + rnd.nextInt(3)) * 1.5f;
        }
        accentPaint.setAlpha(255);
    }

    private void buildTicketStubPath(RectF r, float splitY, float dp1, Path out) {
        out.reset();
        float notchR = dp1 * 9f;
        // 顶部与底部采用细密票根齿痕，左右在 splitY 处留出半圆检票缺口
        Path base = new Path();
        base.addRoundRect(r, dp1 * 6f, dp1 * 6f, Path.Direction.CW);
        Path holes = new Path();
        holes.addCircle(r.left, splitY, notchR, Path.Direction.CW);
        holes.addCircle(r.right, splitY, notchR, Path.Direction.CW);
        float step = dp1 * 9f;
        for (float x = r.left + step; x < r.right - step / 2f; x += step) {
            holes.addCircle(x, r.top, dp1 * 2.5f, Path.Direction.CW);
            holes.addCircle(x, r.bottom, dp1 * 2.5f, Path.Direction.CW);
        }
        out.op(base, holes, Path.Op.DIFFERENCE);
    }

    private void drawCenterCropBitmap(Canvas canvas, Bitmap bm, RectF dst, Paint paint) {
        int bw = bm.getWidth(), bh = bm.getHeight();
        if (bw <= 0 || bh <= 0) return;
        float scale = Math.max(dst.width() / bw, dst.height() / bh);
        float dx = dst.centerX() - (bw * scale) / 2f;
        float dy = dst.centerY() - (bh * scale) / 2f;
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        canvas.save();
        canvas.clipRect(dst);
        canvas.drawBitmap(bm, m, paint);
        canvas.restore();
    }

    private String fieldAt(int idx, String fallback) {
        if (record != null && record.fields != null && idx < record.fields.length
                && record.fields[idx] != null && !record.fields[idx].trim().isEmpty()) {
            return record.fields[idx].trim();
        }
        return fallback;
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
