package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 极简质感信封视图 (1:1 模仿 example/letter1.jpg):
 * - 哑光灰石质重磅卡纸材质 (带细腻纸浆纤维颗粒与光影折痕)
 * - 宽幅信封翻盖 (下延至 58% 高度，底角呈大圆角弧线，向下方信袋投射柔和阴影)
 * - 标志性水平「拉链式易撕封条」(横贯翻盖中部，内含右向箭头/锯齿交错切槽；
 *   静止态左端小舌片即微翘并投射立体对角阴影，极具撕扯触发感；
 *   拆信撕开时支持 tearProgress: 0.0 -> 1.0 沿拉链轨迹向右剥离并露出信封深色内腔)
 * - 底部正中央两行极简打字机/非衬线铭文 (1:1 还原 letter1.jpg 的版式与行距)
 */
public class EnvelopeView extends View {
    private String recipient = "Mira";
    private String dateStr = "2026.09.14";
    private int stampTheme = 0; // 0=岩石灰纸(letter1.jpg同款), 1=暖原木牛皮, 2=深炭灰卡, 3=素白羊皮
    private float tearProgress = 0f; // 0f=静止未撕(letter1.jpg微翘状态), 0..1f=向右撕开拉链

    private final Paint paperPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flapPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cutLinePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cutHighlightPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tabPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tabShadowPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint insideCavityPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);

    public EnvelopeView(Context context) {
        super(context);
        initPaints(context);
    }

    private void initPaints(Context context) {
        cutLinePaint.setStyle(Paint.Style.STROKE);
        cutLinePaint.setColor(0x55000000);
        cutLinePaint.setStrokeWidth(Cards.dp(context, 1f));

        cutHighlightPaint.setStyle(Paint.Style.STROKE);
        cutHighlightPaint.setColor(0x35FFFFFF);
        cutHighlightPaint.setStrokeWidth(Cards.dp(context, 1f));

        insideCavityPaint.setColor(0xFF2B2926);
    }

    public void bind(String recipient, String dateStr, int stampTheme) {
        this.recipient = (recipient == null || recipient.trim().isEmpty()) ? "Mira" : recipient.trim();
        this.dateStr = (dateStr == null || dateStr.trim().isEmpty()) ? "2026.10.18" : dateStr.trim();
        this.stampTheme = stampTheme;
        invalidate();
    }

    public void setTearProgress(float p) {
        this.tearProgress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public float getTearProgress() {
        return tearProgress;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (hMode != MeasureSpec.EXACTLY && w > 0) {
            // 还原 letter1.jpg C6/A6 比例 (长宽比约为 1.4 : 1)
            h = Math.round(w * 0.70f);
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
        float s = w / (dp1 * 320f);
        s = Math.max(0.6f, Math.min(1.4f, s));

        // 基础配色方案 (0=letter1.jpg 标志性灰石纸)
        int paperBase, paperDark, flapTopColor, flapBotColor;
        switch (stampTheme) {
            case 1: // 暖原木牛皮
                paperBase = 0xFFCBBDA7; paperDark = 0xFFBBAA92;
                flapTopColor = 0xFFCFC2AD; flapBotColor = 0xFFC2B39A;
                break;
            case 2: // 深炭黑卡
                paperBase = 0xFF464441; paperDark = 0xFF383633;
                flapTopColor = 0xFF4A4845; flapBotColor = 0xFF3E3C39;
                break;
            case 3: // 素白羊皮纸
                paperBase = 0xFFEBE7DD; paperDark = 0xFFDFDACD;
                flapTopColor = 0xFFF0ECE3; flapBotColor = 0xFFE4DFD3;
                break;
            default: // 0: letter1.jpg 经典岩石灰
                paperBase = 0xFFB7B6B3; paperDark = 0xFFA9A8A5;
                flapTopColor = 0xFFBCBBB8; flapBotColor = 0xFFB0AFAB;
                break;
        }

        RectF body = new RectF(dp1 * 2, dp1 * 2, w - dp1 * 2, h - dp1 * 2);
        float bodyRad = dp1 * 3.5f * s;

        // 1. 底层信袋主体卡纸 (背景与侧褶)
        paperPaint.setShader(new LinearGradient(body.left, body.top, body.left, body.bottom,
                paperBase, paperDark, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(body, bodyRad, bodyRad, paperPaint);
        paperPaint.setShader(null);

        // 侧边折痕缝线 (展现信纸背面的折叠构造)
        Paint creasePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        creasePaint.setStyle(Paint.Style.STROKE);
        creasePaint.setStrokeWidth(dp1 * 0.9f);
        creasePaint.setColor(0x22000000);
        canvas.drawLine(body.left + dp1 * 1f, body.bottom - dp1 * 1f, body.left + dp1 * 14f * s, body.top + h * 0.48f, creasePaint);
        canvas.drawLine(body.right - dp1 * 1f, body.bottom - dp1 * 1f, body.right - dp1 * 14f * s, body.top + h * 0.48f, creasePaint);

        // 2. 宽幅信封翻盖 (下延至 56% 高度，还原 letter1.jpg 大圆角底边)
        float flapBottom = body.top + h * 0.56f;
        float flapLeft = body.left + dp1 * 6f * s;
        float flapRight = body.right - dp1 * 6f * s;
        float flapCornerRad = dp1 * 11f * s;

        Path flapPath = new Path();
        flapPath.moveTo(body.left, body.top);
        flapPath.lineTo(body.right, body.top);
        flapPath.lineTo(flapRight, flapBottom - flapCornerRad);
        flapPath.quadTo(flapRight, flapBottom, flapRight - flapCornerRad, flapBottom);
        flapPath.lineTo(flapLeft + flapCornerRad, flapBottom);
        flapPath.quadTo(flapLeft, flapBottom, flapLeft, flapBottom - flapCornerRad);
        flapPath.close();

        // 翻盖底边投向下方信袋的柔和深色阴影 (强化 3D 悬浮纸张质感)
        shadowPaint.setShader(new LinearGradient(0, flapBottom - dp1 * 1, 0, flapBottom + dp1 * 14f * s,
                new int[]{0x4D000000, 0x1E000000, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        RectF flapShadowRect = new RectF(flapLeft - dp1 * 3, flapBottom - dp1 * 1, flapRight + dp1 * 3, flapBottom + dp1 * 14f * s);
        canvas.drawRoundRect(flapShadowRect, flapCornerRad, flapCornerRad, shadowPaint);
        shadowPaint.setShader(null);

        // 绘制翻盖本体
        flapPaint.setShader(new LinearGradient(body.left, body.top, body.left, flapBottom,
                flapTopColor, flapBotColor, Shader.TileMode.CLAMP));
        canvas.drawPath(flapPath, flapPaint);
        flapPaint.setShader(null);

        // 翻盖四周极细微高光/阴影边缘
        Paint flapEdge = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        flapEdge.setStyle(Paint.Style.STROKE);
        flapEdge.setStrokeWidth(dp1 * 0.9f);
        flapEdge.setColor(0x28000000);
        canvas.drawPath(flapPath, flapEdge);

        // 3. 核心：仿 letter1.jpg 的「拉链式易撕封条」(Chevron/Zipper Perforated Tear Strip)
        drawZipperTearStrip(canvas, flapLeft, flapRight, flapBottom, dp1, s);

        // 4. 底部正中央两行极简排版文字 (1:1 模仿 letter1.jpg 底部的摄影师信息排版)
        drawBottomCenteredTypography(canvas, body, dp1, s);

        // 5. 信封最外层微弱磨砂包边
        Paint outerBorder = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        outerBorder.setStyle(Paint.Style.STROKE);
        outerBorder.setStrokeWidth(dp1 * 1.1f);
        outerBorder.setColor(0x2E000000);
        canvas.drawRoundRect(body, bodyRad, bodyRad, outerBorder);
    }

    /**
     * 仿 letter1.jpg 拉链式锯齿撕条 (由左向右动态剥离)：
     * - 上下两条平行切口槽与微凹刻痕
     * - 槽内连续重复的 Chevron 箭形咬合齿
     * - 静止态 (tearProgress == 0)：左端起始拉片微翘并投射经典对角阴影 (1:1 还原 letter1.jpg)
     * - 撕开态 (tearProgress > 0)：封条真正从左端向右端一路剥离撕开，形成立体卷筒 (Paper Curl Roll)、
     *   悬浮撕带 (Floating Ribbon)、撕裂纤维毛刺 (Perforated Fibers) 与动态阴影，极具物理真实感！
     */
    private void drawZipperTearStrip(Canvas canvas, float flapLeft, float flapRight,
                                     float flapBottom, float dp1, float s) {
        float stripTop = flapBottom - dp1 * 26f * s;
        float stripBottom = flapBottom - dp1 * 11f * s;
        float stripH = stripBottom - stripTop;

        float stripMarginX = dp1 * 14f * s;
        float stripStartX = flapLeft + stripMarginX;
        float stripEndX = flapRight - stripMarginX;
        float stripTotalW = stripEndX - stripStartX;
        if (stripTotalW <= 0) return;

        float toothPitch = dp1 * 17f * s; // 每个拉链齿节的间距
        int toothCount = Math.max(4, Math.round(stripTotalW / toothPitch));
        float actualPitch = stripTotalW / toothCount;
        float chevronIndent = dp1 * 5.2f * s;

        // 计算当前撕开的水平进度 X (tearProgress: 0f -> 1f，从最左端一路撕到最右端)
        float activeTearX = stripStartX + (tearProgress * stripTotalW);

        // 1. 若有撕开进度，先绘制已剥离区域露出的深色信封内部槽腔 (带内凹阴影与撕裂纤维毛边)
        if (tearProgress > 0.005f) {
            float cavityRight = Math.min(stripEndX, activeTearX);
            RectF cavityRect = new RectF(stripStartX, stripTop, cavityRight, stripBottom);
            canvas.drawRect(cavityRect, insideCavityPaint);

            // 内部顶沿投射的深沉暗影
            Paint cavityShadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            cavityShadow.setShader(new LinearGradient(0, stripTop, 0, stripTop + dp1 * 4.5f,
                    0x99000000, 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawRect(cavityRect, cavityShadow);

            // 上下两道撕裂破口的微型纤维咬合齿毛边 (Perforated Fiber Fringe)
            drawTornFiberFringe(canvas, stripStartX, cavityRight, stripTop, stripBottom, dp1, s);
        }

        // 2. 绘制右侧未撕开区域的平铺切槽虚线与 Chevron 箭形咬合齿
        if (tearProgress < 0.999f) {
            drawIntactPerforations(canvas, activeTearX, stripStartX, stripEndX, stripTop, stripBottom,
                    toothCount, actualPitch, chevronIndent, dp1);
        }

        // 3. 核心：由左至右动态卷起剥离的 3D 立体封条 (从左边被撕到右边)
        if (tearProgress <= 0.005f) {
            // 静止未撕态：1:1 还原 letter1.jpg 左侧起始小舌片的微翘与对角立体阴影
            drawIdleLiftedPullTab(canvas, stripStartX, stripTop, stripBottom, actualPitch, chevronIndent, dp1, s);
        } else {
            // 撕开动态：纸条从左端沿拉链轨迹一路卷折撕裂至右端，具备真实的立体卷曲与阴影投影
            drawDynamicTearingStrip(canvas, stripStartX, activeTearX, stripEndX, stripTop, stripBottom,
                    actualPitch, chevronIndent, dp1, s);
        }
    }

    /** 绘制槽腔上下破口的真实撕裂毛刺纤维 */
    private void drawTornFiberFringe(Canvas canvas, float startX, float endX,
                                     float stripTop, float stripBottom, float dp1, float s) {
        Path topFibers = new Path();
        Path botFibers = new Path();
        float step = dp1 * 3.8f * s;
        float toothLen = dp1 * 1.5f * s;

        topFibers.moveTo(startX, stripTop);
        for (float x = startX; x < endX; x += step) {
            float mid = x + step * 0.5f;
            float nx = Math.min(endX, x + step);
            topFibers.lineTo(mid, stripTop + toothLen);
            topFibers.lineTo(nx, stripTop);
        }

        botFibers.moveTo(startX, stripBottom);
        for (float x = startX; x < endX; x += step) {
            float mid = x + step * 0.5f;
            float nx = Math.min(endX, x + step);
            botFibers.lineTo(mid, stripBottom - toothLen);
            botFibers.lineTo(nx, stripBottom);
        }

        Paint fp = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        fp.setStyle(Paint.Style.FILL);
        fp.setColor(getTabColor());
        canvas.drawPath(topFibers, fp);
        canvas.drawPath(botFibers, fp);
    }

    /** 绘制尚未被撕开的右半区域的切痕虚线与齿条 */
    private void drawIntactPerforations(Canvas canvas, float activeTearX, float stripStartX, float stripEndX,
                                        float stripTop, float stripBottom, int toothCount, float actualPitch,
                                        float chevronIndent, float dp1) {
        canvas.save();
        // 剪裁到当前尚未撕开的区域 [activeTearX, stripEndX]
        canvas.clipRect(activeTearX, stripTop - dp1 * 2f, stripEndX + dp1 * 2f, stripBottom + dp1 * 2f);

        Path topCutPath = new Path();
        Path botCutPath = new Path();
        Path chevronPath = new Path();

        for (int i = 0; i < toothCount; i++) {
            float x1 = stripStartX + i * actualPitch;
            float x2 = x1 + actualPitch;
            if (x2 < activeTearX) continue;

            topCutPath.moveTo(x1, stripTop);
            topCutPath.lineTo(x2, stripTop);

            botCutPath.moveTo(x1, stripBottom);
            botCutPath.lineTo(x2, stripBottom);

            chevronPath.moveTo(x1, stripTop);
            chevronPath.lineTo(x1 + chevronIndent, (stripTop + stripBottom) * 0.5f);
            chevronPath.lineTo(x1, stripBottom);
        }

        canvas.drawPath(topCutPath, cutLinePaint);
        canvas.drawPath(botCutPath, cutLinePaint);
        canvas.drawPath(chevronPath, cutLinePaint);

        canvas.save();
        canvas.translate(0, dp1 * 0.7f);
        canvas.drawPath(topCutPath, cutHighlightPaint);
        canvas.drawPath(botCutPath, cutHighlightPaint);
        canvas.restore();

        canvas.restore();
    }

    /** 静止态左端起始小舌片 (1:1 还原 letter1.jpg) */
    private void drawIdleLiftedPullTab(Canvas canvas, float stripStartX, float stripTop,
                                       float stripBottom, float toothPitch, float chevronIndent,
                                       float dp1, float s) {
        float stripH = stripBottom - stripTop;
        float peelLength = dp1 * 34f * s;

        // A. 翘起舌片在信封表面投射的三角形立体阴影
        tabShadowPaint.setColor(0x52000000);
        Path shadowPath = new Path();
        float shadowSpread = dp1 * 7f * s;
        shadowPath.moveTo(stripStartX, stripBottom);
        shadowPath.lineTo(stripStartX + toothPitch * 2.2f, stripBottom + shadowSpread);
        shadowPath.lineTo(stripStartX + toothPitch * 1.2f, stripBottom + shadowSpread * 1.5f);
        shadowPath.lineTo(stripStartX - dp1 * 3f, stripBottom + dp1 * 2f);
        shadowPath.close();
        canvas.drawPath(shadowPath, tabShadowPaint);

        // B. 舌片下沿的暗色切槽空隙
        Paint gapPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        gapPaint.setColor(0xAA1C1A18);
        canvas.drawRect(stripStartX, stripTop + dp1 * 1f, stripStartX + toothPitch * 1.8f, stripBottom - dp1 * 0.5f, gapPaint);

        // C. 静止微翘起立体纸舌
        canvas.save();
        canvas.translate(stripStartX, stripTop - dp1 * 4.5f);
        canvas.rotate(-7.5f, 0, stripH * 0.5f);

        tabPaint.setStyle(Paint.Style.FILL);
        tabPaint.setColor(getTabColor());

        Path tabPath = new Path();
        tabPath.moveTo(0, 0);
        tabPath.lineTo(peelLength, 0);
        tabPath.lineTo(peelLength - chevronIndent * 0.7f, stripH * 0.5f);
        tabPath.lineTo(peelLength, stripH);
        tabPath.lineTo(0, stripH);
        tabPath.close();
        canvas.drawPath(tabPath, tabPaint);

        Paint tabBorder = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        tabBorder.setStyle(Paint.Style.STROKE);
        tabBorder.setStrokeWidth(dp1 * 0.9f);
        tabBorder.setColor(0x33000000);
        canvas.drawPath(tabPath, tabBorder);

        canvas.restore();
    }

    /**
     * 纸条从左端沿拉链轨迹一路卷折撕裂至右端 (解决僵硬问题，具备真实物理撕扯质感)
     * - 随着 activeTearX 向右移动，纸条主体在空中向上扬起并悬浮
     * - 撕裂点 activeTearX 处形成真实的 3D 卷纸圆柱筒 (Paper Curl Roll)
     * - 纸条随撕开在信封表面投射动态向右移动的深色柔和阴影
     * - 撕裂点散落微弱纸屑纤维 (Paper Fibers / Flying Confetti)，强化撕裂张力
     */
    private void drawDynamicTearingStrip(Canvas canvas, float stripStartX, float activeTearX,
                                         float stripEndX, float stripTop, float stripBottom,
                                         float actualPitch, float chevronIndent, float dp1, float s) {
        float stripH = stripBottom - stripTop;
        float stripTotalW = stripEndX - stripStartX;
        float progress = Math.max(0.01f, Math.min(1f, (activeTearX - stripStartX) / stripTotalW));

        // 如果已经撕到最右端结束，渐隐并轻微飞出
        float globalAlpha = (progress > 0.94f) ? Math.max(0f, 1f - (progress - 0.94f) / 0.06f) : 1f;
        if (globalAlpha <= 0.01f) return;

        canvas.save();
        if (globalAlpha < 0.99f) {
            // 撕开最后一瞬的纸条微弱上扬漂浮感
            canvas.translate(dp1 * 4f * s * (1f - globalAlpha), -dp1 * 8f * s * (1f - globalAlpha));
        }

        // 卷筒参数
        float rollR = dp1 * (8f + 2f * (float) Math.sin(progress * Math.PI)) * s;
        float tearHeadX = activeTearX;

        // -------------------------------------------------------------
        // A. 悬浮纸条投射在信封翻盖上的动态投影 (伴随撕扯由左向右平滑扫移)
        // -------------------------------------------------------------
        Paint movingShadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        movingShadow.setColor(Color.argb(Math.round(75 * globalAlpha), 0, 0, 0));

        Path shadowPath = new Path();
        float shadowSpread = dp1 * (8f + 10f * (float) Math.sin(progress * Math.PI)) * s;
        shadowPath.moveTo(stripStartX, stripBottom);
        shadowPath.lineTo(tearHeadX + dp1 * 6f * s, stripBottom + shadowSpread * 0.8f);
        shadowPath.quadTo(tearHeadX, stripBottom + shadowSpread * 1.3f, tearHeadX - rollR * 1.5f, stripBottom + shadowSpread);
        shadowPath.lineTo(stripStartX - dp1 * 3f, stripBottom + dp1 * 3f);
        shadowPath.close();
        canvas.drawPath(shadowPath, movingShadow);

        // 撕扯卷筒正下方核心深影
        Paint rollSpotShadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        rollSpotShadow.setShader(new RadialGradient(tearHeadX - rollR * 0.5f, stripBottom + dp1 * 3f * s,
                rollR * 1.8f, Color.argb(Math.round(90 * globalAlpha), 0, 0, 0), 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(tearHeadX - rollR * 0.5f, stripBottom + dp1 * 3f * s, rollR * 1.8f, rollSpotShadow);

        // -------------------------------------------------------------
        // B. 撕下的纸条在空中的悬浮主带 (从 stripStartX 连到 tearHeadX - rollR)
        // -------------------------------------------------------------
        float ribbonEndX = tearHeadX - rollR * 0.5f;
        if (ribbonEndX > stripStartX + dp1 * 3f) {
            Path ribbon = new Path();
            float liftLeftY = -dp1 * 3.5f * s;
            float liftRightY = -dp1 * (11f + 6f * (float) Math.sin(progress * Math.PI)) * s;

            ribbon.moveTo(stripStartX, stripTop + liftLeftY);
            ribbon.cubicTo(
                    stripStartX + (ribbonEndX - stripStartX) * 0.45f, stripTop + liftLeftY - dp1 * 4f * s,
                    stripStartX + (ribbonEndX - stripStartX) * 0.75f, stripTop + liftRightY + dp1 * 2f * s,
                    ribbonEndX, stripTop + liftRightY);
            ribbon.lineTo(ribbonEndX, stripBottom + liftRightY);
            ribbon.cubicTo(
                    stripStartX + (ribbonEndX - stripStartX) * 0.75f, stripBottom + liftRightY + dp1 * 2f * s,
                    stripStartX + (ribbonEndX - stripStartX) * 0.45f, stripBottom + liftLeftY - dp1 * 4f * s,
                    stripStartX, stripBottom + liftLeftY);
            ribbon.close();

            Paint ribbonPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            ribbonPaint.setShader(new LinearGradient(stripStartX, stripTop + liftRightY, ribbonEndX, stripBottom,
                    getTabColor(), getTabDarkColor(), Shader.TileMode.CLAMP));
            ribbonPaint.setAlpha(Math.round(255 * globalAlpha));
            canvas.drawPath(ribbon, ribbonPaint);

            Paint ribbonStroke = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            ribbonStroke.setStyle(Paint.Style.STROKE);
            ribbonStroke.setStrokeWidth(dp1 * 0.8f);
            ribbonStroke.setColor(Color.argb(Math.round(55 * globalAlpha), 0, 0, 0));
            canvas.drawPath(ribbon, ribbonStroke);

            // 在悬浮纸带上绘制撕出的 Chevron 箭形齿纹理
            float step = actualPitch;
            Paint toothPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            toothPaint.setStyle(Paint.Style.STROKE);
            toothPaint.setStrokeWidth(dp1 * 0.9f);
            toothPaint.setColor(Color.argb(Math.round(60 * globalAlpha), 0, 0, 0));
            for (float tx = stripStartX + step; tx < ribbonEndX - step * 0.5f; tx += step) {
                float fracAlong = (tx - stripStartX) / (ribbonEndX - stripStartX);
                float curLift = liftLeftY + (liftRightY - liftLeftY) * fracAlong;
                float cy = (stripTop + stripBottom) * 0.5f + curLift;
                Path ch = new Path();
                ch.moveTo(tx - chevronIndent * 0.3f, stripTop + curLift);
                ch.lineTo(tx + chevronIndent * 0.6f, cy);
                ch.lineTo(tx - chevronIndent * 0.3f, stripBottom + curLift);
                canvas.drawPath(ch, toothPaint);
            }
        }

        // -------------------------------------------------------------
        // C. 撕扯核心点：3D 卷纸圆柱筒 (Paper Curl Roll at activeTearX)
        // -------------------------------------------------------------
        float rollCx = tearHeadX - rollR * 0.65f;
        float rollCy = stripTop - dp1 * (4f + 3f * (float) Math.sin(progress * Math.PI)) * s;
        float rollRx = rollR * 1.15f;
        float rollRy = rollR * 0.95f;

        // 1. 卷筒外轮廓 (饱满立体圆弧)
        Path rollShell = new Path();
        rollShell.moveTo(tearHeadX, stripBottom - dp1 * 2f);
        rollShell.cubicTo(
                tearHeadX + rollR * 0.4f, rollCy + rollRy,
                rollCx + rollRx, rollCy - rollRy * 1.2f,
                rollCx, rollCy - rollRy);
        rollShell.cubicTo(
                rollCx - rollRx * 1.1f, rollCy - rollRy,
                rollCx - rollRx, rollCy + rollRy,
                rollCx, rollCy + rollRy);
        rollShell.lineTo(tearHeadX, stripBottom);
        rollShell.close();

        Paint rollOuterPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        rollOuterPaint.setShader(new LinearGradient(rollCx - rollRx, rollCy, rollCx + rollRx, rollCy,
                new int[]{getTabDarkColor(), 0xFFFFFFFF, getTabColor(), getTabDarkColor()},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        rollOuterPaint.setAlpha(Math.round(255 * globalAlpha));
        canvas.drawPath(rollShell, rollOuterPaint);

        // 2. 卷筒侧面空心圆孔 (展示卷纸中空 3D 深度)
        RectF holeRect = new RectF(rollCx - rollRx * 0.55f, rollCy - rollRy * 0.65f,
                rollCx + rollRx * 0.25f, rollCy + rollRy * 0.65f);
        Paint rollHolePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        rollHolePaint.setColor(getTabDarkColor());
        rollHolePaint.setAlpha(Math.round(255 * globalAlpha));
        canvas.drawOval(holeRect, rollHolePaint);

        // 圆孔内沿微深阴影
        Paint holeInnerShadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        holeInnerShadow.setStyle(Paint.Style.STROKE);
        holeInnerShadow.setStrokeWidth(dp1 * 1.2f);
        holeInnerShadow.setColor(Color.argb(Math.round(110 * globalAlpha), 0, 0, 0));
        canvas.drawOval(holeRect, holeInnerShadow);

        // 3. 正在被向右牵引的拉片箭头端头 (Pull-Tab Head Leading Rightward)
        if (progress < 0.95f) {
            Path tabHead = new Path();
            float headTipX = tearHeadX + dp1 * 10f * s;
            float headTopY = rollCy - rollRy * 0.85f;
            float headBotY = rollCy + rollRy * 0.45f;
            tabHead.moveTo(rollCx, headTopY);
            tabHead.lineTo(headTipX, headTopY);
            tabHead.lineTo(headTipX - chevronIndent * 0.7f, (headTopY + headBotY) * 0.5f);
            tabHead.lineTo(headTipX, headBotY);
            tabHead.lineTo(rollCx, headBotY);
            tabHead.close();

            Paint headPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            headPaint.setColor(getTabColor());
            headPaint.setAlpha(Math.round(255 * globalAlpha));
            canvas.drawPath(tabHead, headPaint);

            Paint headStroke = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            headStroke.setStyle(Paint.Style.STROKE);
            headStroke.setStrokeWidth(dp1 * 0.8f);
            headStroke.setColor(Color.argb(Math.round(60 * globalAlpha), 0, 0, 0));
            canvas.drawPath(tabHead, headStroke);
        }

        // -------------------------------------------------------------
        // D. 撕裂点空中的微型撕纸纸屑纤维 (Paper Fibers / Flying Confetti)
        // -------------------------------------------------------------
        if (progress > 0.05f && progress < 0.95f) {
            Paint particlePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            particlePaint.setColor(getTabColor());
            for (int k = 0; k < 4; k++) {
                float pCycle = ((progress * 7f) + k * 0.28f) % 1f;
                float px = tearHeadX - dp1 * (4f + k * 5.5f * s) + (float) Math.sin(progress * 18f + k * 2f) * dp1 * 3f;
                float py = stripTop - dp1 * (3f + pCycle * 14f * s);
                float pr = dp1 * (0.8f + (k % 2) * 0.4f);
                particlePaint.setAlpha(Math.round(180 * (1f - pCycle) * globalAlpha));
                canvas.drawCircle(px, py, pr, particlePaint);
            }
        }

        canvas.restore();
    }

    private int getTabColor() {
        switch (stampTheme) {
            case 1: return 0xFFD4C7B3; // 暖原木
            case 2: return 0xFF4D4B48; // 深炭黑
            case 3: return 0xFFEDE9E0; // 素白羊皮
            default: return 0xFFC5C4C1; // 0: 岩石灰 (letter1.jpg)
        }
    }

    private int getTabDarkColor() {
        switch (stampTheme) {
            case 1: return 0xFF968875;
            case 2: return 0xFF2A2826;
            case 3: return 0xFFBDB7A8;
            default: return 0xFF878683;
        }
    }

    /**
     * 仿 letter1.jpg 底部居中极简铭文 (Katie Carmichael | Photographer 风格):
     * 第一行: Recipient | DontWannaForget (小号正文字体，字距微宽，深炭灰)
     * 第二行: Date · archive.letter · ID (更小字号，中灰，优雅克制)
     */
    private void drawBottomCenteredTypography(Canvas canvas, RectF body, float dp1, float s) {
        float centerX = body.centerX();
        float startY = body.bottom - dp1 * 26f * s;

        // 第一行: 收件人与归档名
        String line1 = (recipient.startsWith("To:") ? recipient.substring(3).trim() : recipient)
                + "  |  DontWannaForget";
        textPaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.NORMAL));
        textPaint.setTextSize(10.5f * s * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setLetterSpacing(0.045f);
        textPaint.setColor(stampTheme == 2 ? 0xFFD2D0CB : 0xFF2D2C2A);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(line1, centerX, startY, textPaint);

        // 第二行: 格式化日期与信件编号
        String engDate = formatEnglishDate(dateStr);
        String line2 = engDate + "  ·  archive.letter  ·  +852 2026";
        textPaint.setTextSize(8.2f * s * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setLetterSpacing(0.035f);
        textPaint.setColor(stampTheme == 2 ? 0xFF8E8B86 : 0xFF585754);
        canvas.drawText(line2, centerX, startY + dp1 * 13f * s, textPaint);

        // 恢复对齐方式
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public static String formatEnglishDate(String rawDate) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy.MM.dd", Locale.US);
            Date d = in.parse(rawDate);
            if (d != null) {
                return new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(d);
            }
        } catch (Exception ignored) {}
        return rawDate;
    }
}
