package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import java.util.Random;

/**
 * 书籍摘抄便签自定义视图 (模仿 example/bookexp1.jpg 与 example/bookexp2.jpg):
 * - 默认正面展示摘抄的句子或自己的想法；
 * - 最底下的文字固定展示书名（如 "@太阳照常升起" 或 "《小王子》 · 圣埃克苏佩里"）；
 * - 支持 6 种源于 bookexp1.jpg & bookexp2.jpg 的便签器物样式：
 *   0. 金砂和纸胶带方笺 (仿 bookexp1.jpg / bookexp2 左上)
 *   1. 活页打孔方格纸 + 金色回形针 (仿 bookexp2 左中)
 *   2. 黄铜圆图钉 + 右下卷角便签 (仿 bookexp2 左下中)
 *   3. 金色燕尾长尾夹 + 四周手撕棉纸 (仿 bookexp2 右下)
 *   4. 宽边相框胶带便签 (仿 bookexp2 右上)
 *   5. 复古牛皮手撕签 + 麻绳打孔 (仿 bookexp2 左下 & 右中)
 */
public class BookNoteView extends View {

    private Record record;
    private BookNoteStyle style = new BookNoteStyle();
    private boolean compactMode = false;
    private boolean paperOnly = false;

    private final Paint paperPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint decorPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint quotePaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bookTitlePaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);

    private final Path paperPath = new Path();
    private final Path auxPath = new Path();
    private final RectF paperRect = new RectF();

    public BookNoteView(Context context) {
        super(context);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x42000000);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Cards.dp(context, 0.8f));
    }

    public void bind(Record r, BookNoteStyle customStyle, boolean compact) {
        this.record = r;
        this.style = customStyle != null ? customStyle : BookNoteStyle.fromRecord(r);
        this.compactMode = compact;
        requestLayout();
        invalidate();
    }

    public void setStyle(BookNoteStyle s) {
        this.style = s != null ? s : new BookNoteStyle();
        requestLayout();
        invalidate();
    }

    public BookNoteStyle getStyle() {
        return style;
    }

    /** Reading pages reuse the paper and its decorations behind full-height native content. */
    public void setPaperOnly(boolean paperOnly) {
        this.paperOnly = paperOnly;
        invalidate();
    }

    public int getReadingTextColor() {
        return resolveInkMainColor(resolvePaperColor());
    }

    public int getReadingSecondaryColor() {
        return resolveInkSubColor(resolvePaperColor());
    }

    /** 根据不同便签样式返回最贴合 bookexp1.jpg / bookexp2.jpg 的高宽比 (高 / 宽) */
    public float computeHeightRatio() {
        switch (style.noteStyle) {
            case BookNoteStyle.STYLE_WASHI_SQUARE:
                return 0.98f; // 仿 bookexp1.jpg 接近正方形的便签
            case BookNoteStyle.STYLE_GRID_PAPERCLIP:
                return 0.84f; // 仿 bookexp2 左中 横向打孔活页便签
            case BookNoteStyle.STYLE_PIN_CURLED:
                return 0.96f; // 仿 bookexp2 左下中 方形卷角图钉便签
            case BookNoteStyle.STYLE_BINDER_TORN:
                return 0.88f; // 仿 bookexp2 右下 燕尾夹手撕便签
            case BookNoteStyle.STYLE_POLAROID_TAPE:
                return 1.08f; // 仿 bookexp2 右上 拍立得相框便签
            case BookNoteStyle.STYLE_KRAFT_TAG:
            default:
                return 0.86f; // 仿 bookexp2 左下 复古牛皮手撕签
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

        float dp1 = Cards.dp(getContext(), 1f);
        // 顶部预留胶带/图钉/燕尾夹/回形针突出的空间
        float topHeadroom = dp1 * (style.noteStyle == BookNoteStyle.STYLE_BINDER_TORN ? 18f : 11f);
        float sidePad = dp1 * 7f;
        float bottomPad = dp1 * 8f;
        paperRect.set(sidePad, topHeadroom, w - sidePad, h - bottomPad);

        int seed = (record != null && record.title != null) ? record.title.hashCode() : 1084;

        // 1. 解析纸张底色与墨色
        int paperColor = resolvePaperColor();
        int inkMain = resolveInkMainColor(paperColor);
        int inkBookTitle = resolveInkSubColor(paperColor);

        // 2. 构建便签外轮廓 Path
        buildPaperOutlinePath(paperRect, style.noteStyle, seed, dp1, paperPath);

        // 3. 绘制便签立体投影
        canvas.save();
        canvas.translate(dp1 * 2.2f, dp1 * 3.8f);
        canvas.drawPath(paperPath, shadowPaint);
        canvas.restore();

        // 若是右下卷角样式 (STYLE_PIN_CURLED)，在右下角折起处额外绘制加深卷角阴影
        if (style.noteStyle == BookNoteStyle.STYLE_PIN_CURLED) {
            drawCurledCornerShadow(canvas, paperRect, dp1);
        }

        // 4. 绘制便签纸主体底色
        paperPaint.setStyle(Paint.Style.FILL);
        paperPaint.setColor(paperColor);
        canvas.drawPath(paperPath, paperPaint);

        // 5. 裁剪在纸张内部绘制纹理、方格线、活页孔、相框内衬与摘抄文字
        canvas.save();
        canvas.clipPath(paperPath);

        // 纸张细微纤维斑点纹理 (还原 bookexp1.jpg 宣纸颗粒感)
        drawPaperFiberSpeckles(canvas, paperRect, seed, dp1, paperColor);

        // 柔和四周环境暗角
        drawSubtlePaperEdgeShading(canvas, paperRect, dp1, paperColor);

        if (style.noteStyle == BookNoteStyle.STYLE_GRID_PAPERCLIP) {
            drawGridLinesAndBinderHoles(canvas, paperRect, dp1, paperColor);
        } else if (style.noteStyle == BookNoteStyle.STYLE_POLAROID_TAPE && !paperOnly) {
            drawPolaroidInnerWell(canvas, paperRect, dp1);
        }

        // 6. 绘制正中央的【书句摘抄 / 自己的想法】与最底下的【书名】
        if (!paperOnly) {
            drawExcerptAndBottomBookTitle(canvas, paperRect, dp1, inkMain, inkBookTitle);
        }

        // 若是右下卷角样式，绘制卷起的三角纸页背面
        if (style.noteStyle == BookNoteStyle.STYLE_PIN_CURLED) {
            drawCurledCornerFlap(canvas, paperRect, dp1, paperColor);
        }

        canvas.restore();

        // 7. 纸张边缘微弱描边
        borderPaint.setColor(isDarkPaper(paperColor) ? 0x24FFFFFF : 0x22000000);
        canvas.drawPath(paperPath, borderPaint);

        // 8. 绘制跨越纸张上边缘的固定件（和纸胶带 / 金色回形针 / 黄铜圆图钉 / 燕尾长尾夹 / 麻绳吊牌孔）
        switch (style.noteStyle) {
            case BookNoteStyle.STYLE_WASHI_SQUARE:
                drawGoldWashiTape(canvas, paperRect, dp1, true);
                break;
            case BookNoteStyle.STYLE_GRID_PAPERCLIP:
                drawGoldPaperclip(canvas, paperRect, dp1);
                break;
            case BookNoteStyle.STYLE_PIN_CURLED:
                drawBrassPushpin(canvas, paperRect, dp1);
                break;
            case BookNoteStyle.STYLE_BINDER_TORN:
                drawGoldBinderClip(canvas, paperRect, dp1);
                break;
            case BookNoteStyle.STYLE_POLAROID_TAPE:
                drawGoldWashiTape(canvas, paperRect, dp1, false);
                break;
            case BookNoteStyle.STYLE_KRAFT_TAG:
            default:
                drawGoldWashiTape(canvas, paperRect, dp1, false);
                drawKraftEyeletAndTwine(canvas, paperRect, dp1);
                break;
        }
    }

    private int resolvePaperColor() {
        switch (style.paperTone) {
            case BookNoteStyle.TONE_WARM_CREAM:
                return 0xFFE8DFCC;
            case BookNoteStyle.TONE_KRAFT_BROWN:
                return 0xFFC2AA8E;
            case BookNoteStyle.TONE_MIST_GREY:
                return 0xFFDADCD9;
            case BookNoteStyle.TONE_INK_DARK:
                return 0xFF242625;
            case BookNoteStyle.TONE_RICE_WHITE:
            default:
                return 0xFFF4F2ED; // 还原 bookexp1.jpg 的温润宣白底色
        }
    }

    private boolean isDarkPaper(int paperColor) {
        return style.paperTone == BookNoteStyle.TONE_INK_DARK
                || (!paperOnly && style.noteStyle == BookNoteStyle.STYLE_POLAROID_TAPE);
    }

    private int resolveInkMainColor(int paperColor) {
        if (style.noteStyle == BookNoteStyle.STYLE_POLAROID_TAPE && !paperOnly) {
            return 0xFFF2EFE9; // 相框内深底衬托浅色字
        }
        if (style.paperTone == BookNoteStyle.TONE_INK_DARK) {
            return 0xFFEAE6DF;
        }
        if (style.paperTone == BookNoteStyle.TONE_KRAFT_BROWN) {
            return 0xFF241C14;
        }
        return 0xFF1A1817; // 浓墨手写黑
    }

    private int resolveInkSubColor(int paperColor) {
        if (style.noteStyle == BookNoteStyle.STYLE_POLAROID_TAPE) {
            // 相框底部边框在米白卡纸上，书名用深灰墨色
            return (style.paperTone == BookNoteStyle.TONE_INK_DARK) ? 0xFFA8A49C : 0xFF5E5850;
        }
        if (style.paperTone == BookNoteStyle.TONE_INK_DARK) {
            return 0xFF96928A;
        }
        if (style.paperTone == BookNoteStyle.TONE_KRAFT_BROWN) {
            return 0xFF594A3A;
        }
        return 0xFF7D7870; // 仿 bookexp1.jpg 最底下浅灰书名字迹
    }

    // =========================================================================
    // 便签轮廓 Path 构建
    // =========================================================================
    private void buildPaperOutlinePath(RectF r, int noteStyle, int seed, float dp1, Path out) {
        out.reset();
        Random rnd = new Random(seed ^ 0x3A8F19C3L);

        if (noteStyle == BookNoteStyle.STYLE_WASHI_SQUARE) {
            // 仿 bookexp1.jpg: 顶部微曲自然纸边，左右轻微波浪，底部柔和或手撕边
            out.moveTo(r.left, r.top);
            addWavyEdge(out, r.left, r.top, r.right, r.top, dp1 * 0.6f, 14, rnd);
            addWavyEdge(out, r.right, r.top, r.right, r.bottom, dp1 * 0.7f, 14, rnd);
            addTornEdge(out, r.right, r.bottom, r.left, r.bottom, dp1 * 1.6f, 22, rnd);
            addWavyEdge(out, r.left, r.bottom, r.left, r.top, dp1 * 0.7f, 14, rnd);
            out.close();
        } else if (noteStyle == BookNoteStyle.STYLE_GRID_PAPERCLIP) {
            // 仿 bookexp2 左中: 上/左/右平直，底部斜向不规则撕裂毛边
            float leftBottomY = r.bottom;
            float rightBottomY = r.bottom - (paperOnly ? dp1 * 16f : r.height() * 0.10f);
            out.moveTo(r.left, r.top);
            out.lineTo(r.right, r.top);
            addWavyEdge(out, r.right, r.top, r.right, rightBottomY, dp1 * 0.5f, 10, rnd);
            addTornEdge(out, r.right, rightBottomY, r.left, leftBottomY, dp1 * 3.2f, 26, rnd);
            out.lineTo(r.left, r.top);
            out.close();
        } else if (noteStyle == BookNoteStyle.STYLE_PIN_CURLED) {
            // 仿 bookexp2 左下中: 齐整方形便签 + 右下角立体向上卷起切角
            float curl = Math.min(r.width(), r.height()) * 0.19f;
            out.moveTo(r.left, r.top);
            out.lineTo(r.right, r.top);
            out.lineTo(r.right, r.bottom - curl);
            out.quadTo(r.right - curl * 0.35f, r.bottom - curl * 0.35f, r.right - curl, r.bottom);
            out.lineTo(r.left, r.bottom);
            out.close();
        } else if (noteStyle == BookNoteStyle.STYLE_BINDER_TORN) {
            // 仿 bookexp2 右下: 四周不规则手撕毛边白棉纸
            out.moveTo(r.left, r.top);
            addTornEdge(out, r.left, r.top, r.right, r.top, dp1 * 2.2f, 22, rnd);
            addTornEdge(out, r.right, r.top, r.right, r.bottom, dp1 * 2.6f, 20, rnd);
            addTornEdge(out, r.right, r.bottom, r.left, r.bottom, dp1 * 2.8f, 24, rnd);
            addTornEdge(out, r.left, r.bottom, r.left, r.top, dp1 * 2.6f, 20, rnd);
            out.close();
        } else if (noteStyle == BookNoteStyle.STYLE_POLAROID_TAPE) {
            // 仿 bookexp2 右上: 拍立得相框外轮廓（上/左/右齐整微毛边，下边手撕毛边）
            out.moveTo(r.left, r.top);
            addWavyEdge(out, r.left, r.top, r.right, r.top, dp1 * 0.5f, 12, rnd);
            addWavyEdge(out, r.right, r.top, r.right, r.bottom, dp1 * 0.5f, 14, rnd);
            addTornEdge(out, r.right, r.bottom, r.left, r.bottom, dp1 * 2.4f, 24, rnd);
            addWavyEdge(out, r.left, r.bottom, r.left, r.top, dp1 * 0.5f, 14, rnd);
            out.close();
        } else {
            // 仿 bookexp2 左下牛皮纸: 不规则有机手撕轮廓
            float insetR = dp1 * 3f;
            out.moveTo(r.left, r.top + dp1 * 2f);
            addTornEdge(out, r.left, r.top + dp1 * 2f, r.right - insetR, r.top + dp1 * 4f, dp1 * 3.0f, 22, rnd);
            addTornEdge(out, r.right - insetR, r.top + dp1 * 4f, r.right, r.bottom - dp1 * 6f, dp1 * 3.5f, 18, rnd);
            addTornEdge(out, r.right, r.bottom - dp1 * 6f, r.left + dp1 * 4f, r.bottom, dp1 * 3.2f, 22, rnd);
            addTornEdge(out, r.left + dp1 * 4f, r.bottom, r.left, r.top + dp1 * 2f, dp1 * 2.8f, 18, rnd);
            out.close();
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
            if (rnd.nextFloat() < 0.2f) {
                offset += (rnd.nextBoolean() ? 0.8f : -0.6f) * maxJitter;
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

    // =========================================================================
    // 纸面肌理与内部装饰绘制
    // =========================================================================
    private void drawPaperFiberSpeckles(Canvas canvas, RectF r, int seed, float dp1, int paperColor) {
        Random rnd = new Random(seed + 307);
        decorPaint.setStyle(Paint.Style.FILL);
        boolean dark = (style.paperTone == BookNoteStyle.TONE_INK_DARK);
        int count = 36;
        for (int i = 0; i < count; i++) {
            if (dark) {
                decorPaint.setColor((i % 2 == 0) ? 0x10FFFFFF : 0x16000000);
            } else {
                decorPaint.setColor((i % 3 == 0) ? 0x126E5D46 : 0x0D000000);
            }
            float cx = r.left + rnd.nextFloat() * r.width();
            float cy = r.top + rnd.nextFloat() * r.height();
            canvas.drawCircle(cx, cy, dp1 * (0.45f + rnd.nextFloat() * 0.95f), decorPaint);
        }
    }

    private void drawSubtlePaperEdgeShading(Canvas canvas, RectF r, float dp1, int paperColor) {
        if (style.paperTone == BookNoteStyle.TONE_INK_DARK) return;
        decorPaint.setStyle(Paint.Style.FILL);
        LinearGradient bottomShade = new LinearGradient(
                r.left, r.bottom - dp1 * 26f, r.left, r.bottom,
                0x00000000, 0x12000000, Shader.TileMode.CLAMP);
        decorPaint.setShader(bottomShade);
        canvas.drawRect(r.left, r.bottom - dp1 * 26f, r.right, r.bottom, decorPaint);
        decorPaint.setShader(null);
    }

    /** 仿 bookexp2 左中: 绘制浅方格暗纹与左侧一列圆形活页装订孔 */
    private void drawGridLinesAndBinderHoles(Canvas canvas, RectF r, float dp1, int paperColor) {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp1 * 0.55f);
        linePaint.setColor(style.paperTone == BookNoteStyle.TONE_INK_DARK ? 0x16FFFFFF : 0x186E736F);

        float gridStep = dp1 * 15f;
        for (float x = r.left + dp1 * 24f; x < r.right; x += gridStep) {
            canvas.drawLine(x, r.top, x, r.bottom, linePaint);
        }
        for (float y = r.top + gridStep; y < r.bottom; y += gridStep) {
            canvas.drawLine(r.left + dp1 * 20f, y, r.right, y, linePaint);
        }

        // 左侧一竖排活页装订圆孔
        float holeX = r.left + dp1 * 10.5f;
        float holeRadius = dp1 * 3.4f;
        float holeStep = dp1 * 16f;
        decorPaint.setStyle(Paint.Style.FILL);
        for (float y = r.top + dp1 * 14f; y < r.bottom - dp1 * 16f; y += holeStep) {
            decorPaint.setColor(0xFF191A19);
            canvas.drawCircle(holeX, y, holeRadius, decorPaint);
            borderPaint.setColor(0x33000000);
            canvas.drawCircle(holeX, y, holeRadius, borderPaint);
        }
    }

    /** 仿 bookexp2 右上: 拍立得相框内部深色窗格 (留出底部加宽边写书名) */
    private void drawPolaroidInnerWell(Canvas canvas, RectF r, float dp1) {
        float frameSide = dp1 * 12f;
        float frameTop = dp1 * 14f;
        float frameBottom = dp1 * 34f;
        RectF inner = new RectF(
                r.left + frameSide,
                r.top + frameTop,
                r.right - frameSide,
                r.bottom - frameBottom
        );
        decorPaint.setStyle(Paint.Style.FILL);
        decorPaint.setColor(0xFF161717);
        canvas.drawRect(inner, decorPaint);

        borderPaint.setColor(0x33000000);
        canvas.drawRect(inner, borderPaint);
    }

    /** 仿 bookexp2 左下中: 右下角卷角底部阴影与翻起折页 */
    private void drawCurledCornerShadow(Canvas canvas, RectF r, float dp1) {
        float curl = Math.min(r.width(), r.height()) * 0.19f;
        auxPath.reset();
        auxPath.moveTo(r.right - curl - dp1 * 4f, r.bottom);
        auxPath.lineTo(r.right, r.bottom - curl - dp1 * 4f);
        auxPath.lineTo(r.right - dp1 * 2f, r.bottom - dp1 * 2f);
        auxPath.close();
        decorPaint.setStyle(Paint.Style.FILL);
        decorPaint.setColor(0x44000000);
        canvas.drawPath(auxPath, decorPaint);
    }

    private void drawCurledCornerFlap(Canvas canvas, RectF r, float dp1, int paperColor) {
        float curl = Math.min(r.width(), r.height()) * 0.19f;
        auxPath.reset();
        auxPath.moveTo(r.right, r.bottom - curl);
        auxPath.quadTo(r.right - curl * 0.72f, r.bottom - curl * 0.68f, r.right - curl, r.bottom);
        auxPath.quadTo(r.right - curl * 0.35f, r.bottom - curl * 0.35f, r.right, r.bottom - curl);
        auxPath.close();

        decorPaint.setStyle(Paint.Style.FILL);
        LinearGradient flapGrad = new LinearGradient(
                r.right - curl, r.bottom - curl, r.right, r.bottom,
                0xFFFDFBF7, 0xFFC9BFA8, Shader.TileMode.CLAMP);
        decorPaint.setShader(flapGrad);
        canvas.drawPath(auxPath, decorPaint);
        decorPaint.setShader(null);

        borderPaint.setColor(0x28000000);
        canvas.drawPath(auxPath, borderPaint);
    }

    // =========================================================================
    // 顶部五金/胶带固定件绘制 (还原 bookexp1.jpg 与 bookexp2.jpg)
    // =========================================================================

    /** 顶部和纸胶带 (Washi Tape): goldShimmer=true 为仿 bookexp1.jpg 的金砂胶带，false 为仿 bookexp2 的米白半透胶带 */
    private void drawGoldWashiTape(Canvas canvas, RectF r, float dp1, boolean goldShimmer) {
        canvas.save();
        float cx = r.centerX();
        float cy = r.top + dp1 * 1.5f;
        canvas.rotate(-2.2f, cx, cy);

        float tapeW = r.width() * 0.28f;
        float tapeH = dp1 * 17f;
        RectF tr = new RectF(cx - tapeW / 2f, cy - tapeH / 2f, cx + tapeW / 2f, cy + tapeH / 2f);

        // 构建左右带细微锯齿撕口的和纸胶带 Path
        auxPath.reset();
        auxPath.moveTo(tr.left, tr.top);
        auxPath.lineTo(tr.right, tr.top);
        int teeth = 6;
        float stepY = tr.height() / teeth;
        for (int i = 0; i < teeth; i++) {
            float yMid = tr.top + (i + 0.5f) * stepY;
            float yEnd = tr.top + (i + 1f) * stepY;
            auxPath.lineTo(tr.right - dp1 * 1.8f, yMid);
            auxPath.lineTo(tr.right, yEnd);
        }
        auxPath.lineTo(tr.left, tr.bottom);
        for (int i = teeth - 1; i >= 0; i--) {
            float yMid = tr.top + (i + 0.5f) * stepY;
            float yStart = tr.top + i * stepY;
            auxPath.lineTo(tr.left + dp1 * 1.8f, yMid);
            auxPath.lineTo(tr.left, yStart);
        }
        auxPath.close();

        // 胶带微弱阴影
        decorPaint.setStyle(Paint.Style.FILL);
        decorPaint.setColor(0x22000000);
        canvas.save();
        canvas.translate(0, dp1 * 1.2f);
        canvas.drawPath(auxPath, decorPaint);
        canvas.restore();

        // 胶带本体渐变
        LinearGradient tg;
        if (goldShimmer) {
            // 还原 bookexp1.jpg 顶部金砂和纸胶带质感
            tg = new LinearGradient(tr.left, tr.top, tr.right, tr.bottom,
                    new int[]{0xE8D8BE8E, 0xE8CBB07A, 0xE8DFC89B, 0xE8C4A66E},
                    null, Shader.TileMode.CLAMP);
        } else {
            // 还原 bookexp2.jpg 半透明米白纹理胶带
            tg = new LinearGradient(tr.left, tr.top, tr.right, tr.bottom,
                    new int[]{0xD8E5DFD3, 0xD8D8D1C4, 0xD8E8E2D6},
                    null, Shader.TileMode.CLAMP);
        }
        decorPaint.setShader(tg);
        canvas.drawPath(auxPath, decorPaint);
        decorPaint.setShader(null);

        // 胶带表面细微金砂/纤维斑点
        Random tapeRnd = new Random(918273L);
        decorPaint.setColor(goldShimmer ? 0x337A5C28 : 0x226E6658);
        for (int i = 0; i < 22; i++) {
            float sx = tr.left + dp1 * 3f + tapeRnd.nextFloat() * (tr.width() - dp1 * 6f);
            float sy = tr.top + dp1 * 2f + tapeRnd.nextFloat() * (tr.height() - dp1 * 4f);
            canvas.drawCircle(sx, sy, dp1 * 0.7f, decorPaint);
        }

        canvas.restore();
    }

    /** 仿 bookexp2 左中: 右上角立体金色金属回形针 (Gold Paperclip) */
    private void drawGoldPaperclip(Canvas canvas, RectF r, float dp1) {
        float cx = r.right - r.width() * 0.20f;
        float topY = r.top - dp1 * 5f;
        float w = dp1 * 12f;
        float h = dp1 * 36f;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(dp1 * 2.3f);

        RectF outerLoop = new RectF(cx - w / 2f, topY, cx + w / 2f, topY + h);
        RectF innerLoop = new RectF(cx - w * 0.28f, topY + dp1 * 5f, cx + w * 0.28f, topY + h - dp1 * 4f);

        // 投影
        linePaint.setColor(0x38000000);
        canvas.save();
        canvas.translate(dp1 * 1.2f, dp1 * 1.6f);
        canvas.drawRoundRect(outerLoop, w / 2f, w / 2f, linePaint);
        canvas.drawRoundRect(innerLoop, w * 0.28f, w * 0.28f, linePaint);
        canvas.restore();

        // 金色回形针主体
        linePaint.setColor(0xFFC9A86A);
        canvas.drawRoundRect(outerLoop, w / 2f, w / 2f, linePaint);
        canvas.drawRoundRect(innerLoop, w * 0.28f, w * 0.28f, linePaint);

        // 高光细丝
        linePaint.setStrokeWidth(dp1 * 0.8f);
        linePaint.setColor(0xFFFFF0C4);
        canvas.drawRoundRect(outerLoop, w / 2f, w / 2f, linePaint);
    }

    /** 仿 bookexp2 左下中: 顶部中央立体黄铜圆头图钉 (Brass Pushpin) */
    private void drawBrassPushpin(Canvas canvas, RectF r, float dp1) {
        float cx = r.centerX();
        float cy = r.top + dp1 * 3f;
        float rad = dp1 * 10.5f;

        // 投射在便签纸上的斜向圆影
        decorPaint.setStyle(Paint.Style.FILL);
        decorPaint.setColor(0x3D000000);
        canvas.drawCircle(cx + dp1 * 1.8f, cy + dp1 * 2.6f, rad, decorPaint);

        // 黄铜金属球面渐变
        RadialGradient rg = new RadialGradient(
                cx - rad * 0.3f, cy - rad * 0.3f, rad * 1.3f,
                new int[]{0xFFE8D2A0, 0xFFB8995E, 0xFF7D6438},
                new float[]{0f, 0.65f, 1f},
                Shader.TileMode.CLAMP);
        decorPaint.setShader(rg);
        canvas.drawCircle(cx, cy, rad, decorPaint);
        decorPaint.setShader(null);

        borderPaint.setColor(0x554A3818);
        canvas.drawCircle(cx, cy, rad, borderPaint);
    }

    /** 仿 bookexp2 右下: 顶部中央金色燕尾长尾夹 (Gold Binder Clip) */
    private void drawGoldBinderClip(Canvas canvas, RectF r, float dp1) {
        float cx = r.centerX();
        float clipBottom = r.top + dp1 * 9f;
        float clipTop = r.top - dp1 * 4f;
        float halfWBottom = dp1 * 17f;
        float halfWTop = dp1 * 13.5f;

        // 1. 绘制顶部向上弯折的钢丝双耳手柄
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp1 * 1.8f);
        linePaint.setColor(0xFFD9C088);
        RectF handleOval = new RectF(cx - dp1 * 7.5f, clipTop - dp1 * 12f, cx + dp1 * 7.5f, clipTop + dp1 * 3f);
        canvas.drawArc(handleOval, 160, 220, false, linePaint);

        // 2. 绘制金色梯形夹体阴影与本体
        auxPath.reset();
        auxPath.moveTo(cx - halfWTop, clipTop);
        auxPath.lineTo(cx + halfWTop, clipTop);
        auxPath.lineTo(cx + halfWBottom, clipBottom);
        auxPath.lineTo(cx - halfWBottom, clipBottom);
        auxPath.close();

        decorPaint.setStyle(Paint.Style.FILL);
        decorPaint.setColor(0x3A000000);
        canvas.save();
        canvas.translate(0, dp1 * 2f);
        canvas.drawPath(auxPath, decorPaint);
        canvas.restore();

        LinearGradient cg = new LinearGradient(
                cx - halfWBottom, clipTop, cx + halfWBottom, clipBottom,
                new int[]{0xFFAE9052, 0xFFE5CD94, 0xFFB89A5B, 0xFF8C713B},
                null, Shader.TileMode.CLAMP);
        decorPaint.setShader(cg);
        canvas.drawPath(auxPath, decorPaint);
        decorPaint.setShader(null);

        // 夹口底部高光横杠
        linePaint.setStrokeWidth(dp1 * 1.6f);
        linePaint.setColor(0xFFF7E5B4);
        canvas.drawLine(cx - halfWBottom, clipBottom - dp1 * 2f, cx + halfWBottom, clipBottom - dp1 * 2f, linePaint);
    }

    /** 仿 bookexp2 右中: 复古吊牌金属孔眼与棉麻绳装饰 */
    private void drawKraftEyeletAndTwine(Canvas canvas, RectF r, float dp1) {
        float hx = r.left + dp1 * 16f;
        float hy = r.top + dp1 * 16f;
        float outerR = dp1 * 5.2f;
        float innerR = dp1 * 2.6f;

        decorPaint.setStyle(Paint.Style.FILL);
        decorPaint.setColor(0xFFA88952);
        canvas.drawCircle(hx, hy, outerR, decorPaint);
        decorPaint.setColor(0xFF191A19);
        canvas.drawCircle(hx, hy, innerR, decorPaint);

        // 麻绳曲线
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp1 * 1.8f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(0xFFD8CEBE);
        auxPath.reset();
        auxPath.moveTo(hx - dp1 * 1f, hy);
        auxPath.quadTo(hx - dp1 * 12f, hy - dp1 * 10f, r.left - dp1 * 4f, hy - dp1 * 4f);
        canvas.drawPath(auxPath, linePaint);
    }

    // =========================================================================
    // 核心文字绘制：中央摘抄句子/想法 + 最底下书名 (严格还原 bookexp1.jpg 布局)
    // =========================================================================
    private void drawExcerptAndBottomBookTitle(Canvas canvas, RectF r, float dp1,
                                               int inkMain, int inkBookTitle) {
        float scale = r.width() / (dp1 * 260f);
        scale = Math.max(0.66f, Math.min(1.30f, scale));

        String displayQuote = style.resolveCardDisplayText(record);
        String rawTitle = (record != null && record.title != null && !record.title.trim().isEmpty())
                ? record.title.trim() : L10n.s(R.string.ui_583);
        String rawAuthor = (record != null && record.sub != null && !record.sub.trim().isEmpty())
                ? record.sub.trim() : "";

        // 仿 bookexp1.jpg 最底下书名样式 (如 "@太阳照常升起" 或 "@小王子 · 圣埃克苏佩里")
        String bottomBookLabel = "@" + rawTitle + (!rawAuthor.isEmpty() ? (" · " + rawAuthor) : "");

        // 计算文本可用区域（避开左侧活页孔、相框宽边与底部书名区）
        float padLeft = dp1 * (style.noteStyle == BookNoteStyle.STYLE_GRID_PAPERCLIP ? 26f : 22f) * scale;
        float padRight = dp1 * 22f * scale;
        float padTop = dp1 * (style.noteStyle == BookNoteStyle.STYLE_POLAROID_TAPE ? 24f : 24f) * scale;
        float bottomTitleZoneH = dp1 * (style.noteStyle == BookNoteStyle.STYLE_POLAROID_TAPE ? 32f : 34f) * scale;
        if(style.noteStyle==BookNoteStyle.STYLE_GRID_PAPERCLIP)
            bottomTitleZoneH=Math.max(bottomTitleZoneH,r.height()*.11f+dp1*22f*scale);

        float textBoxLeft = r.left + padLeft;
        float textBoxRight = r.right - padRight;
        float textBoxTop = r.top + padTop;
        float textBoxBottom = r.bottom - bottomTitleZoneH;
        int availW = Math.max(60, Math.round(textBoxRight - textBoxLeft));
        float availH = Math.max(dp1 * 40f, textBoxBottom - textBoxTop);

        // 配置摘抄主文字画笔
        Typeface tf = FontManager.resolve(getContext(), style.fontId);
        quotePaint.setTypeface(tf);
        quotePaint.setColor(inkMain);

        float targetSp = style.textSizeSp;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        float quotePx = targetSp * scaledDensity * scale;
        quotePaint.setTextSize(quotePx);

        Layout.Alignment align = style.centerAlign
                ? Layout.Alignment.ALIGN_CENTER
                : Layout.Alignment.ALIGN_NORMAL;

        StaticLayout quoteLayout = StaticLayout.Builder
                .obtain(displayQuote, 0, displayQuote.length(), quotePaint, availW)
                .setAlignment(align)
                .setLineSpacing(0f, 1.48f)
                .setIncludePad(false)
                .build();

        // 若摘抄句子较长超出纸面高度，自适应微缩字号以完整呈现
        while (quoteLayout.getHeight() > availH && quotePx > dp1 * 9.5f) {
            quotePx *= 0.90f;
            quotePaint.setTextSize(quotePx);
            quoteLayout = StaticLayout.Builder
                    .obtain(displayQuote, 0, displayQuote.length(), quotePaint, availW)
                    .setAlignment(align)
                    .setLineSpacing(0f, 1.42f)
                    .setIncludePad(false)
                    .build();
        }
        if(quoteLayout.getHeight()>availH) {
            int lines=Math.max(1,(int)(availH/(quotePaint.getFontSpacing()*1.42f)));
            quoteLayout=StaticLayout.Builder.obtain(displayQuote,0,displayQuote.length(),quotePaint,availW)
                    .setAlignment(align).setLineSpacing(0f,1.42f).setIncludePad(false)
                    .setMaxLines(lines).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
        }

        // 垂直居中绘制摘抄句子（仿 bookexp1.jpg 留白呼吸感）
        float quoteY = textBoxTop + Math.max(0f, (availH - quoteLayout.getHeight()) * 0.46f);
        canvas.save();
        canvas.clipRect(textBoxLeft,textBoxTop,textBoxRight,textBoxBottom);
        canvas.translate(textBoxLeft, quoteY);
        quoteLayout.draw(canvas);
        canvas.restore();

        // 最底下居中绘制书名 (严格对应用户需求："最底下的文字是书名"，仿 bookexp1.jpg 底部小字)
        bookTitlePaint.setTypeface(tf);
        bookTitlePaint.setColor(inkBookTitle);
        bookTitlePaint.setTextSize(dp1 * (compactMode ? 9.5f : 10.5f) * scale);
        bookTitlePaint.setTextAlign(Paint.Align.CENTER);

        float titleY;
        if (style.noteStyle == BookNoteStyle.STYLE_POLAROID_TAPE) {
            titleY = r.bottom - dp1 * 12.5f * scale;
        } else if (style.noteStyle == BookNoteStyle.STYLE_GRID_PAPERCLIP) {
            titleY = r.bottom - r.height() * 0.11f - dp1 * 8f * scale;
        } else {
            titleY = r.bottom - dp1 * 14f * scale;
        }

        // 若书名过长则截断
        String fittedBookLabel = android.text.TextUtils.ellipsize(
                bottomBookLabel, bookTitlePaint, availW, android.text.TextUtils.TruncateAt.END).toString();
        canvas.drawText(fittedBookLabel, r.centerX(), titleY, bookTitlePaint);
    }
}
