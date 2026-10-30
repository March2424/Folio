package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 复古信纸视图 (1:1 模仿 example/letter2.jpg + 40bfdf8b1v60f132aaf8d6bdb1e2c773.PNG):
 * - 背景采用实打实的高清原木纤维纸浆背景图 (letter_paper_bg.jpg)
 * - 左上角 Subject: 标引 + 复古金属装订针脚印迹
 * - 右上角 Form G. 8. 档案表格标号
 * - 顶部中央 DONTWANNAFORGET ARCHIVE CO. 宽字距大写打字机抬头
 * - 右上方寄信地与日期 (如 Hong Kong, Oct. 18, 2026.) 及 File 1420 案卷号
 * - 左侧梯级缩进收件人阶梯栏 (To: Mira, Esq. / Archive Dept. / Dear Mira:-)
 * - 右侧倾斜复古双框紫色橡胶验讫印章 (OFFICE / Memory Archive Agent / 日期 / D. W. F. CO.)
 * - 正文采用打字机经典等宽墨色字体，段落首行缩进，双引号暖杏色荧光划线
 * - 底部右对齐 Yours truly, 随函附签章 (J. R. Ruffin) 与 D F A 签发代码
 */
public class VintageLetterSheetView extends FrameLayout {
    private Bitmap paperBgBitmap;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadowPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);

    // 控件引用
    private TextView tvSubject;
    private TextView tvCompanyHeader;
    private TextView tvDateLine;
    private TextView tvFileLine;
    private TextView tvAddressBlock;
    private TextView tvBody;
    private LinearLayout signatureLayout;
    private TextView tvYoursTruly;
    private TextView tvSigner;
    private TextView tvSignerInitials;

    private String recipient = "Mira";
    private String dateStr = "2026.10.18";
    private String title = "";
    private boolean isFirstPage = true;
    private boolean isLastPage = true;

    public VintageLetterSheetView(Context context) {
        super(context);
        init(context);
    }

    public VintageLetterSheetView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        try {
            paperBgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.letter_paper_bg);
        } catch (Throwable ignored) {}

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        int padH = Cards.dp(context, 26);
        int padV = Cards.dp(context, 28);
        contentCol.setPadding(padH, padV, padH, padV);

        // 1. 顶部表头行: Subject: (左) | Form G. 8. (右)
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        tvSubject = Cards.text(context, "Subject: ", 12.5f, 0xFF2B2825,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        topRow.addView(tvSubject, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvForm = Cards.text(context, "Form G. 8.", 10f, 0xFF58544F, AppFonts.regular());
        topRow.addView(tvForm);
        contentCol.addView(topRow);

        // 2. 中央宽距大写报头: DONTWANNAFORGET ARCHIVE CO.
        tvCompanyHeader = Cards.text(context, "DONTWANNAFORGET ARCHIVE CO.", 11.5f, 0xFF22201D,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        tvCompanyHeader.setLetterSpacing(0.06f);
        tvCompanyHeader.setGravity(Gravity.CENTER);
        tvCompanyHeader.setPadding(0, Cards.dp(context, 14), 0, Cards.dp(context, 12));
        contentCol.addView(tvCompanyHeader);

        // 3. 右侧寄信日期与 File 案卷号
        LinearLayout dateBox = new LinearLayout(context);
        dateBox.setOrientation(LinearLayout.VERTICAL);
        dateBox.setGravity(Gravity.END);

        tvDateLine = Cards.text(context, "Hong Kong, Oct. 18, 2026.", 11.5f, 0xFF2E2B27, AppFonts.regular());
        dateBox.addView(tvDateLine);

        tvFileLine = Cards.text(context, "File 1420", 11.5f, 0xFF2E2B27, AppFonts.regular());
        tvFileLine.setPadding(0, Cards.dp(context, 4), 0, 0);
        dateBox.addView(tvFileLine);

        contentCol.addView(dateBox);

        // 4. 左侧阶梯缩进收件人栏目 (1:1 还原 letter2.jpg)
        tvAddressBlock = Cards.text(context, "", 12f, 0xFF282522, AppFonts.regular());
        tvAddressBlock.setLineSpacing(0f, 1.35f);
        tvAddressBlock.setPadding(0, Cards.dp(context, 8), 0, Cards.dp(context, 14));
        contentCol.addView(tvAddressBlock);

        // 5. 正文正文 (打字机等宽字体，双引号高光)
        tvBody = Cards.text(context, "", 14f, 0xFF22201D, AppFonts.regular());
        tvBody.setLineSpacing(0f, 1.55f);
        tvBody.setPadding(0, Cards.dp(context, 4), 0, Cards.dp(context, 18));
        contentCol.addView(tvBody);

        // 6. 底部右侧签名结语栏 (Yours truly, / 印章 / D F A)
        signatureLayout = new LinearLayout(context);
        signatureLayout.setOrientation(LinearLayout.VERTICAL);
        signatureLayout.setGravity(Gravity.END);
        signatureLayout.setPadding(0, Cards.dp(context, 10), Cards.dp(context, 12), Cards.dp(context, 8));

        tvYoursTruly = Cards.text(context, "Yours truly,", 12.5f, 0xFF2E2B27, AppFonts.regular());
        signatureLayout.addView(tvYoursTruly);

        // 紫红色复古手迹印章 J. R. Ruffin
        tvSigner = Cards.text(context, "J. R. Ruffin", 14f, 0xCC7F385F,
                Typeface.create(AppFonts.regular(), Typeface.BOLD_ITALIC));
        tvSigner.setPadding(0, Cards.dp(context, 6), Cards.dp(context, 8), Cards.dp(context, 2));
        tvSigner.setRotation(-2.5f);
        signatureLayout.addView(tvSigner);

        tvSignerInitials = Cards.text(context, "D   F   A", 11f, 0xFF35322E, AppFonts.regular());
        tvSignerInitials.setLetterSpacing(0.25f);
        signatureLayout.addView(tvSignerInitials);

        TextView tvFooterCode = Cards.text(context, "w", 11f, 0xFF45423E, AppFonts.regular());
        tvFooterCode.setPadding(0, Cards.dp(context, 8), 0, 0);
        signatureLayout.addView(tvFooterCode);

        contentCol.addView(signatureLayout);

        scroll.addView(contentCol, new FrameLayout.LayoutParams(-1, -2));
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    public void bindPage(String pageText, String recipient, String dateStr, String title,
                         boolean isFirstPage, boolean isLastPage) {
        this.recipient = (recipient == null || recipient.trim().isEmpty()) ? "Mira" : recipient.trim();
        this.dateStr = (dateStr == null || dateStr.trim().isEmpty()) ? "2026.10.18" : dateStr.trim();
        this.title = (title == null || title.trim().isEmpty()) ? "" : title.trim();
        this.isFirstPage = isFirstPage;
        this.isLastPage = isLastPage;

        // 顶部表头
        if (isFirstPage) {
            tvSubject.setVisibility(View.VISIBLE);
            tvCompanyHeader.setVisibility(View.VISIBLE);
            tvDateLine.setVisibility(View.VISIBLE);
            tvFileLine.setVisibility(View.VISIBLE);
            tvAddressBlock.setVisibility(View.VISIBLE);

            String subText = "Subject: " + (title.isEmpty() ? "Personal Correspondence" : title);
            tvSubject.setText(subText);

            String engDate = EnvelopeView.formatEnglishDate(dateStr);
            tvDateLine.setText(engDate + ".");

            // 案卷号 (根据日期与标题哈希生成经典 File 号)
            int fileNum = 1400 + Math.abs((dateStr + title).hashCode()) % 800;
            tvFileLine.setText("File " + fileNum);

            // 还原 letter2.jpg 阶梯递进 Address block
            String steppedAddress = recipient + ", Esq.,\n"
                    + "    Memory Archive Dept.,";
            tvAddressBlock.setText(steppedAddress);
        } else {
            tvSubject.setVisibility(View.GONE);
            tvCompanyHeader.setVisibility(View.GONE);
            tvDateLine.setVisibility(View.GONE);
            tvFileLine.setVisibility(View.GONE);
            tvAddressBlock.setVisibility(View.GONE);
        }

        // 正文段落缩进与双引号高光处理
        String formatted = pageText;
        if (formatted != null && !formatted.trim().isEmpty()) {
            String[] lines = formatted.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    sb.append("\n");
                } else if (!line.startsWith("    ") && !line.startsWith("Dear ") && !line.startsWith("To:")) {
                    sb.append("    ").append(line).append("\n");
                } else {
                    sb.append(line).append("\n");
                }
            }
            formatted = sb.toString().trim();
        }

        SpannableString span = new SpannableString(formatted != null ? formatted : "");
        Pattern quotePattern = Pattern.compile("(“[^”]{2,80}”|\"[^\"]{2,80}\")");
        Matcher m = quotePattern.matcher(span);
        while (m.find()) {
            span.setSpan(new BackgroundColorSpan(0xFFF3DEC3),
                    m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvBody.setText(span);

        // 尾部签署栏只在最后一页展示
        signatureLayout.setVisibility(isLastPage ? View.VISIBLE : View.GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float dp1 = Cards.dp(getContext(), 1f);

        // 1. 绘制高清木质纤维信纸贴图 (letter_paper_bg.jpg)
        if (paperBgBitmap != null && !paperBgBitmap.isRecycled()) {
            srcRect.set(0, 0, paperBgBitmap.getWidth(), paperBgBitmap.getHeight());
            dstRect.set(0, 0, w, h);
            canvas.drawBitmap(paperBgBitmap, srcRect, dstRect, bgPaint);
        } else {
            canvas.drawColor(0xFFEBE6D9);
        }

        // 纸张四周柔和暗角与边缘细微发黄光影 (强化岁月泛黄质感)
        Paint borderAged = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        borderAged.setStyle(Paint.Style.STROKE);
        borderAged.setStrokeWidth(dp1 * 1.5f);
        borderAged.setColor(0x22554636);
        canvas.drawRect(0, 0, w, h, borderAged);

        // 2. 左上角复古订书机针脚痕迹 (Staple Impression)
        if (isFirstPage) {
            Paint staplePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            staplePaint.setColor(0x6633302C);
            staplePaint.setStrokeWidth(dp1 * 2f);
            float stX = dp1 * 18f;
            float stY = dp1 * 18f;
            canvas.drawLine(stX, stY, stX + dp1 * 14f, stY + dp1 * 4f, staplePaint);
            // 穿透针孔阴影
            Paint holePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            holePaint.setColor(0x44000000);
            canvas.drawCircle(stX - dp1 * 1.5f, stY - dp1 * 0.5f, dp1 * 1.6f, holePaint);
            canvas.drawCircle(stX + dp1 * 15.5f, stY + dp1 * 4.5f, dp1 * 1.6f, holePaint);
        }

        // 3. 右上方复古双框紫色橡胶验讫印章 (1:1 还原 letter2.jpg 右侧倾斜戳记)
        if (isFirstPage) {
            drawVioletOfficeRubberStamp(canvas, w, dp1);
        }

        super.onDraw(canvas);
    }

    /**
     * 绘制 1:1 还原 letter2.jpg 的双线框紫红验讫戳记:
     * - 逆时针倾斜 -7.5°
     * - 双层细矩形线框 (带橡胶印章轻微透墨断续感)
     * - OFFICE / Asst. General Archive Agent / SEP 10 1901 / C. P. & V. R. R. CO.
     */
    private void drawVioletOfficeRubberStamp(Canvas canvas, int w, float dp1) {
        float stampW = dp1 * 105f;
        float stampH = dp1 * 54f;
        float stampX = w - stampW - dp1 * 28f;
        float stampY = dp1 * 110f;

        canvas.save();
        canvas.rotate(-7.5f, stampX + stampW * 0.5f, stampY + stampH * 0.5f);

        int stampInk = 0x887E395F; // 怀旧紫红透墨色

        Paint framePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setColor(stampInk);
        framePaint.setStrokeWidth(dp1 * 1.2f);

        // 外层框
        RectF outerRect = new RectF(stampX, stampY, stampX + stampW, stampY + stampH);
        canvas.drawRoundRect(outerRect, dp1 * 2f, dp1 * 2f, framePaint);

        // 内层细框
        framePaint.setStrokeWidth(dp1 * 0.7f);
        RectF innerRect = new RectF(stampX + dp1 * 2.5f, stampY + dp1 * 2.5f,
                stampX + stampW - dp1 * 2.5f, stampY + stampH - dp1 * 2.5f);
        canvas.drawRoundRect(innerRect, dp1 * 1.5f, dp1 * 1.5f, framePaint);

        // 印章内部文字
        Paint sp = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        sp.setColor(stampInk);
        sp.setTextAlign(Paint.Align.CENTER);
        float cx = stampX + stampW * 0.5f;

        sp.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        sp.setTextSize(dp1 * 7.5f);
        canvas.drawText("OFFICE", cx, stampY + dp1 * 12f, sp);

        sp.setTypeface(Typeface.create(AppFonts.regular(), Typeface.NORMAL));
        sp.setTextSize(dp1 * 6.5f);
        canvas.drawText("Memory Archive Agent", cx, stampY + dp1 * 22f, sp);

        // 大号日期戳
        sp.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        sp.setTextSize(dp1 * 9.5f);
        String stampDate = formatStampDate(dateStr);
        canvas.drawText(stampDate, cx, stampY + dp1 * 36f, sp);

        sp.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        sp.setTextSize(dp1 * 6.2f);
        canvas.drawText("D. W. F. ARCHIVE CO.", cx, stampY + dp1 * 47f, sp);

        canvas.restore();
    }

    private static String formatStampDate(String raw) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy.MM.dd", Locale.US);
            Date d = in.parse(raw);
            if (d != null) {
                return new SimpleDateFormat("MMM dd yyyy", Locale.US).format(d).toUpperCase(Locale.US);
            }
        } catch (Exception ignored) {}
        return "OCT 18 2026";
    }
}
