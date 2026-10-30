package hk.hkucs.dontwannaforget;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 写信(便签)——复古打字机界面(1:1 还原 typeexp.jpg):
 * - 顶部返回键、字数统计 (177 / 3,000) 与「装入信封 ›」按钮
 * - 中央 3D 弧面打字纸张 + 横向黑色金属滚轴 + 打字机打印头(带红黄绿指示灯与 T 型字锤)
 * - 锥形暖光灯束照射在纸张上,下方配有「灯光(弱/中/强)」「扩散(角度)」「照射范围(%)」三组刻度滑轨与灯光开关
 * - 直接调用手机自带系统输入法打字,字锤随敲击轻微跳动,写完可选择信封样式并封入信封按月份日期保存
 */
public class TypewriterActivity extends AppCompatActivity {
    private static final int MAX_CHARS = 3000;

    private String recordId;
    private Record existingRecord;
    private String dateStr;
    private String recipient = "Mira";
    private String letterTitle = "";
    private int stampTheme = 0;

    private EditText hiddenInput;
    private TextView tvCounter, tvLightVal, tvSpreadVal, tvRangeVal;
    private TypewriterSceneView sceneView;

    private boolean lampOn = true;
    private float lampIntensity = 0.15f + (45f / 80f) * 0.85f; // Matches the initial slider position
    private int spreadDeg = 132;         // 70° ~ 160°
    private int rangePct = 120;          // 60% ~ 150%

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        recordId = getIntent().getStringExtra("id");
        dateStr = new SimpleDateFormat("yyyy.MM.dd", Locale.US).format(new Date());

        buildUi();

        if (recordId != null) {
            new Repo(this).get(recordId, r -> runOnUiThread(() -> {
                if (r != null) {
                    existingRecord = r;
                    dateStr = r.date;
                    letterTitle = r.title;
                    recipient = r.sub.isEmpty() ? "Mira" : r.sub;
                    if (r.fields != null && r.fields.length > 0) {
                        try { stampTheme = Integer.parseInt(r.fields[0]); } catch (Exception ignored) {}
                    }
                    hiddenInput.setText(r.note);
                    hiddenInput.setSelection(hiddenInput.getText().length());
                }
            }));
        } else {
            // 默认示例诗句或直接继续输入
            String defaultSample = "";
            hiddenInput.setText(defaultSample);
        }

        // 自动弹出手机自带输入法
        hiddenInput.postDelayed(this::focusAndShowKeyboard, 250);
    }

    private void focusAndShowKeyboard() {
        hiddenInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF191917);

        LinearLayout mainCol = new LinearLayout(this);
        mainCol.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainCol, new FrameLayout.LayoutParams(-1, -1));

        // 1. 顶部栏: ← 返回 | 177 / 3,000 | 装入信封 ›
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Cards.dp(this, 20);
        topBar.setPadding(pad, Cards.dp(this, 14), pad, Cards.dp(this, 8));

        TextView btnBack = Cards.text(this, L10n.s(R.string.ui_060), 13, 0xFFC5C0B7, AppFonts.regular());
        btnBack.setOnClickListener(v -> finish());
        HomeNavigation.bind(btnBack);
        topBar.addView(btnBack, new LinearLayout.LayoutParams(0, -2, 1f));

        tvCounter = Cards.text(this, "0 / 3,000", 13, 0xFFB5B0A6, AppFonts.regular());
        tvCounter.setPadding(0, 0, Cards.dp(this, 14), 0);
        topBar.addView(tvCounter);

        TextView btnEnvelope = Cards.text(this, L10n.s(R.string.ui_099), 13, 0xFF191917,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        btnEnvelope.setPadding(Cards.dp(this, 14), Cards.dp(this, 7), Cards.dp(this, 14), Cards.dp(this, 7));
        GradientDrawable envBg = new GradientDrawable();
        envBg.setColor(0xFFEADBC0);
        envBg.setCornerRadius(Cards.dp(this, 16));
        btnEnvelope.setBackground(envBg);
        btnEnvelope.setOnClickListener(v -> showEnvelopeSealDialog());
        topBar.addView(btnEnvelope);

        root.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        FloatingHeader.style(topBar);

        // 2. 中央打字机弧面纸张与聚光灯区域 (点击任意处唤起手机自带键盘)
        FrameLayout stageWrap = new FrameLayout(this);
        sceneView = new TypewriterSceneView(this);
        stageWrap.addView(sceneView, new FrameLayout.LayoutParams(-1, -1));

        // 透明 EditText 承载系统自带输入法(支持拼音/手写/语音/多语言键盘)
        hiddenInput = new EditText(this);
        hiddenInput.setBackground(null);
        hiddenInput.setTextColor(Color.TRANSPARENT);
        hiddenInput.setCursorVisible(false);
        hiddenInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_CHARS)});
        hiddenInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                sceneView.triggerHammerStrike();
            }
            @Override
            public void afterTextChanged(Editable s) {
                String str = s.toString();
                tvCounter.setText(String.format(Locale.US, "%,d / 3,000", str.length()));
                sceneView.setText(str);
            }
        });
        // 放在画面内但全透明,确保所有原生输入法候选词条正常跟随
        stageWrap.addView(hiddenInput, new FrameLayout.LayoutParams(Cards.dp(this, 10), Cards.dp(this, 10), Gravity.CENTER));
        sceneView.setOnClickListener(v -> focusAndShowKeyboard());

        mainCol.addView(stageWrap, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 3. 底部灯光控制面板(灯光 / 扩散 / 照射范围 / 开关, 1:1 还原 typeexp.jpg)
        buildLampControls(mainCol);

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });
    }

    private void buildLampControls(LinearLayout parent) {
        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.HORIZONTAL);
        deck.setGravity(Gravity.CENTER_VERTICAL);
        deck.setBackgroundColor(0xFF33322E);
        int padH = Cards.dp(this, 16);
        deck.setPadding(padH, Cards.dp(this, 12), padH, Cards.dp(this, 14));

        // 控制项 1: 灯光 (弱/中/强)
        tvLightVal = Cards.text(this, L10n.s(R.string.ui_100), 12, 0xFFA9A49B, AppFonts.regular());
        LinearLayout c1 = buildSliderColumn(L10n.s(R.string.ui_101), tvLightVal, 80, 45, progress -> {
            lampIntensity = 0.15f + (progress / 80f) * 0.85f;
            tvLightVal.setText(progress < 25 ? L10n.s(R.string.ui_102) : progress < 55 ? L10n.s(R.string.ui_100) : L10n.s(R.string.ui_103));
            sceneView.invalidate();
        });
        deck.addView(c1, new LinearLayout.LayoutParams(0, -2, 1f));

        // 控制项 2: 扩散 (70° ~ 160°)
        tvSpreadVal = Cards.text(this, "132°", 12, 0xFFA9A49B, AppFonts.regular());
        LinearLayout c2 = buildSliderColumn(L10n.s(R.string.ui_104), tvSpreadVal, 90, 62, progress -> {
            spreadDeg = 70 + progress;
            tvSpreadVal.setText(spreadDeg + "°");
            sceneView.invalidate();
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp2.leftMargin = Cards.dp(this, 10);
        deck.addView(c2, lp2);

        // 控制项 3: 照射范围 (60% ~ 150%)
        tvRangeVal = Cards.text(this, "120%", 12, 0xFFA9A49B, AppFonts.regular());
        LinearLayout c3 = buildSliderColumn(L10n.s(R.string.ui_105), tvRangeVal, 90, 60, progress -> {
            rangePct = 60 + progress;
            tvRangeVal.setText(rangePct + "%");
            sceneView.invalidate();
        });
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0, -2, 1.15f);
        lp3.leftMargin = Cards.dp(this, 10);
        deck.addView(c3, lp3);

        // 右侧太阳灯光开关按钮 (还原 typeexp.jpg 右下角图标)
        TextView btnLamp = Cards.text(this, "☼", 24, 0xFFB8B3A8, AppFonts.regular());
        btnLamp.setGravity(Gravity.CENTER);
        btnLamp.setPadding(Cards.dp(this, 12), 0, Cards.dp(this, 4), 0);
        btnLamp.setOnClickListener(v -> {
            lampOn = !lampOn;
            btnLamp.setTextColor(lampOn ? 0xFFEADBC0 : 0xFF6E6B64);
            sceneView.invalidate();
        });
        deck.addView(btnLamp);

        parent.addView(deck, new LinearLayout.LayoutParams(-1, -2));
    }

    private interface OnSlide { void onChanged(int progress); }

    private LinearLayout buildSliderColumn(String title, TextView valueTv, int max, int init, OnSlide cb) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbl = Cards.text(this, title, 12, 0xFFA9A49B, AppFonts.regular());
        head.addView(lbl, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(valueTv);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(init);
        sb.setPadding(0, Cards.dp(this, 6), 0, 0);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cb.onChanged(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        col.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        return col;
    }

    /** 点击「装入信封 ›」弹出信封预览与收件人/标题/日期设置面板(还原 letterexp1.jpg) */
    private void showEnvelopeSealDialog() {
        String content = hiddenInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, L10n.s(R.string.ui_106), Toast.LENGTH_SHORT).show();
            focusAndShowKeyboard();
            return;
        }

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFFEFEDE7);
        int p = Cards.dp(this, 20);
        box.setPadding(p, p, p, p);
        sv.addView(box);

        TextView head = Cards.text(this, L10n.s(R.string.ui_107), 16, getColor(R.color.ink),
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        head.setPadding(0, 0, 0, Cards.dp(this, 12));
        box.addView(head);

        // 实时信封预览 (还原 letterexp1.jpg)
        EnvelopeView envPreview = new EnvelopeView(this);
        envPreview.setElevation(Cards.dp(this, 4));
        envPreview.bind(recipient, dateStr, stampTheme);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, -2);
        elp.bottomMargin = Cards.dp(this, 16);
        box.addView(envPreview, elp);

        // 信封卡纸质感切换 (还原 letter1.jpg)
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] themes = {L10n.s(R.string.ui_108), L10n.s(R.string.ui_109), L10n.s(R.string.ui_110), L10n.s(R.string.ui_111)};
        for (int i = 0; i < themes.length; i++) {
            final int ti = i;
            TextView chip = Cards.text(this, themes[i], 12, getColor(R.color.ink), AppFonts.regular());
            chip.setPadding(Cards.dp(this, 10), Cards.dp(this, 6), Cards.dp(this, 10), Cards.dp(this, 6));
            GradientDrawable cbg = new GradientDrawable();
            cbg.setColor(0xFFFAF8F3);
            cbg.setCornerRadius(Cards.dp(this, 12));
            cbg.setStroke(Cards.dp(this, 1), 0xFFD5CFC3);
            chip.setBackground(cbg);
            chip.setOnClickListener(v -> {
                stampTheme = ti;
                envPreview.bind(recipient, dateStr, stampTheme);
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.rightMargin = Cards.dp(this, 8);
            themeRow.addView(chip, clp);
        }
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1, -2);
        trLp.bottomMargin = Cards.dp(this, 12);
        box.addView(themeRow, trLp);

        // 收件人输入 (To: Mira)
        EditText etTo = new EditText(this);
        etTo.setHint(L10n.s(R.string.ui_112));
        etTo.setText(recipient);
        etTo.setSingleLine(true);
        etTo.setTextSize(15);
        etTo.setTypeface(AppFonts.regular());
        styleDialogInput(etTo);
        etTo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                recipient = s.toString().trim();
                envPreview.bind(recipient, dateStr, stampTheme);
            }
        });
        box.addView(etTo);

        // 信件标题输入 (The umbrella you left behind)
        EditText etTitle = new EditText(this);
        etTitle.setHint(L10n.s(R.string.ui_113));
        String defaultTitle = letterTitle;
        if (defaultTitle.isEmpty()) {
            String firstLine = content.split("\n")[0].trim();
            defaultTitle = firstLine.length() > 24 ? firstLine.substring(0, 24) : firstLine;
        }
        etTitle.setText(defaultTitle);
        etTitle.setSingleLine(true);
        etTitle.setTextSize(15);
        etTitle.setTypeface(AppFonts.regular());
        styleDialogInput(etTitle);
        box.addView(etTitle);

        // 日期选择(按月份日期保存)
        TextView tvPickDate = Cards.text(this, L10n.s(R.string.ui_114) + dateStr + L10n.s(R.string.ui_115), 13,
                getColor(R.color.wine), AppFonts.regular());
        tvPickDate.setPadding(Cards.dp(this, 4), Cards.dp(this, 6), 0, Cards.dp(this, 4));
        tvPickDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> {
                dateStr = String.format(Locale.US, "%04d.%02d.%02d", y, m + 1, d);
                tvPickDate.setText(L10n.s(R.string.ui_114) + dateStr + L10n.s(R.string.ui_115));
                envPreview.bind(recipient, dateStr, stampTheme);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        box.addView(tvPickDate);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(sv)
                .setNegativeButton(L10n.s(R.string.ui_116), null)
                .setPositiveButton(L10n.s(R.string.ui_117), (d, which) -> {
                    recipient = etTo.getText().toString().trim();
                    if (recipient.isEmpty()) recipient = "Mira";
                    letterTitle = etTitle.getText().toString().trim();
                    if (letterTitle.isEmpty()) letterTitle = "A Letter to " + recipient;
                    saveLetterAndOpenEnvelope(content);
                })
                .create();
        dialog.show();
    }

    private void styleDialogInput(EditText et) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFAF8F3);
        bg.setCornerRadius(Cards.dp(this, 10));
        bg.setStroke(Cards.dp(this, 1), 0xFFD8D2C5);
        et.setBackground(bg);
        int p = Cards.dp(this, 12);
        et.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Cards.dp(this, 10);
        et.setLayoutParams(lp);
    }

    private void saveLetterAndOpenEnvelope(String content) {
        Record r = new Record(Record.LETTER, letterTitle, recipient, dateStr, null, content,
                "#D9D2C5", "#F4EFE4", String.valueOf(stampTheme));
        if (existingRecord != null) {
            r.id = existingRecord.id;
            r.createdAt = existingRecord.createdAt;
        }
        new Repo(this).save(r, () -> runOnUiThread(() -> {
            Toast.makeText(this, L10n.s(R.string.ui_118) + dateStr, Toast.LENGTH_SHORT).show();
            if (existingRecord == null) {
                Intent i = new Intent(this, LetterActivity.class);
                i.putExtra("id", r.id);
                startActivity(i);
            }
            finish();
        }));
    }

    /**
     * 3D 弧面复古信纸 + 质感工业光影打字机 (同步 letter2.jpg 与 40bfdf8b1v60f132aaf8d6bdb1e2c773.PNG 原木纤维信纸)
     */
    private class TypewriterSceneView extends View {
        private String rawText = "";
        private float hammerOffset = 0f;

        private Bitmap paperBgBitmap;
        private final Rect paperSrcRect = new Rect();
        private final RectF paperDstRect = new RectF();

        private final Paint paperPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint paperShadePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rollerPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint carriagePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lightPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = AppFonts.textPaint(Paint.ANTI_ALIAS_FLAG);

        TypewriterSceneView(Context c) {
            super(c);
            tp.setTypeface(AppFonts.regular());
            try {
                paperBgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.letter_paper_bg);
            } catch (Throwable ignored) {}
        }

        void setText(String t) {
            this.rawText = t != null ? t : "";
            invalidate();
        }

        void triggerHammerStrike() {
            hammerOffset = -Cards.dp(getContext(), 8);
            invalidate();
            postDelayed(() -> {
                hammerOffset = 0f;
                invalidate();
            }, 65);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float dp1 = Cards.dp(getContext(), 1);
            float rollerY = h * 0.77f;
            float paperTop = h * 0.11f;
            float paperLeft = w * 0.045f;
            float paperRight = w - w * 0.045f;
            float headCenterX = w * 0.40f;

            // 1. 绘制下部打字机铸铁底盘机台 (带金属烤漆光泽与机身深色倒角)
            Paint deckBg = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            deckBg.setShader(new LinearGradient(0, rollerY, 0, h,
                    new int[]{0xFF242321, 0xFF35332F, 0xFF1E1D1B},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, rollerY, w, h, deckBg);

            // 机台顶边铣削高光亮线 (Machined Metal Edge Bevel)
            Paint bevelLine = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            bevelLine.setStyle(Paint.Style.STROKE);
            bevelLine.setStrokeWidth(dp1 * 1.5f);
            bevelLine.setColor(0x44FFFFFF);
            canvas.drawLine(0, rollerY, w, rollerY, bevelLine);

            // 2. 绘制 3D 弧面纸张 (轮廓圆滑收分，体现纸张卷入滚筒的纵深曲率)
            Path paperPath = new Path();
            float topInset = w * 0.055f;
            paperPath.moveTo(paperLeft + topInset, paperTop);
            paperPath.quadTo(w * 0.5f, paperTop - dp1 * 7, paperRight - topInset, paperTop);
            paperPath.quadTo(paperRight, (paperTop + rollerY) * 0.5f, paperRight, rollerY);
            paperPath.lineTo(paperLeft, rollerY);
            paperPath.quadTo(paperLeft, (paperTop + rollerY) * 0.5f, paperLeft + topInset, paperTop);
            paperPath.close();

            // 纸张投影 (纸张拱起在桌面两侧投下的柔和深色阴影)
            Paint paperCastShadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            paperCastShadow.setColor(0x38000000);
            canvas.save();
            canvas.translate(0, dp1 * 4f);
            canvas.drawPath(paperPath, paperCastShadow);
            canvas.restore();

            // 3. 绘制信纸贴图 (letter_paper_bg.jpg) 与 letter2.jpg 复古抬头
            canvas.save();
            canvas.clipPath(paperPath);

            if (paperBgBitmap != null && !paperBgBitmap.isRecycled()) {
                paperSrcRect.set(0, 0, paperBgBitmap.getWidth(), paperBgBitmap.getHeight());
                paperDstRect.set(paperLeft, paperTop - dp1 * 15, paperRight, rollerY + dp1 * 20);
                canvas.drawBitmap(paperBgBitmap, paperSrcRect, paperDstRect, paperPaint);
            } else {
                canvas.drawColor(0xFFEFEBE0);
            }

            // 3D 卷纸弧面纵深阴影 (顶部卷曲暗角 + 底部滚筒插槽阴影)
            paperShadePaint.setShader(new LinearGradient(0, paperTop, 0, paperTop + dp1 * 44f,
                    new int[]{0x661A1815, 0x221A1815, 0x001A1815},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(paperLeft, paperTop - dp1 * 10, paperRight, paperTop + dp1 * 44f, paperShadePaint);

            paperShadePaint.setShader(new LinearGradient(0, rollerY - dp1 * 32f, 0, rollerY,
                    new int[]{0x001A1815, 0x331A1815, 0x661A1815},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(paperLeft, rollerY - dp1 * 32f, paperRight, rollerY, paperShadePaint);

            // 在卷纸上方印制 letter2.jpg 复古公文抬头
            drawStationeryHeaderOnPaper(canvas, w, paperTop, rollerY, dp1);

            // Give the paper ambient shading so a warm spotlight has visible contrast.
            // The texture and stationery remain visible; typed ink is drawn afterward.
            lightPaint.setShader(null);
            lightPaint.setColor(Color.argb(88, 28, 25, 22));
            canvas.drawRect(paperLeft, paperTop-dp1*15, paperRight, rollerY, lightPaint);

            float headTopY = rollerY - dp1 * 6;
            if (lampOn) {
                float reach = (rollerY - paperTop) * (rangePct / 115f);
                float halfSpreadRad = (float) Math.toRadians(Math.min(165, Math.max(50, spreadDeg)) / 2.0);
                float spreadHalfW = (float) (Math.tan(halfSpreadRad * 0.62f) * reach);

                Path cone = new Path();
                cone.moveTo(headCenterX - dp1 * 18, headTopY);
                cone.lineTo(headCenterX - spreadHalfW, headTopY - reach);
                cone.quadTo(headCenterX, headTopY - reach - dp1 * 28, headCenterX + spreadHalfW, headTopY - reach);
                cone.lineTo(headCenterX + dp1 * 18, headTopY);
                cone.close();

                int centerAlpha = Math.round(55 + 170 * lampIntensity);
                int midAlpha = Math.round(20 + 115 * lampIntensity);
                int glowColor1 = Color.argb(centerAlpha, 255, 246, 220); // 暖白淡黄微光
                int glowColor2 = Color.argb(midAlpha, 248, 230, 195);
                int glowColor3 = Color.argb(0, 240, 220, 180);

                lightPaint.setAlpha(255);
                lightPaint.setShader(new RadialGradient(
                        headCenterX, headTopY - reach * .28f, Math.max(dp1 * 40, reach * .85f),
                        new int[]{glowColor1, glowColor2, glowColor3},
                        new float[]{0f, 0.62f, 1f},
                        Shader.TileMode.CLAMP));
                canvas.drawPath(cone, lightPaint);
            }

            // 5. 在弧面纸张上绘制打字机等宽文字 (墨迹印在受光照的纸张之上，黑度浓郁，字体清晰可辨)
            drawTypedLinesOnPaper(canvas, w, paperTop, rollerY, headCenterX, dp1);

            canvas.restore();

            // 6. 绘制高质感黑色橡胶打字滚轴 (Platen Roller) 与两端车削旋钮 (Platen Knobs)
            drawPlatenRollerAndKnobs(canvas, w, rollerY, dp1);

            // 7. 绘制不锈钢压纸横杆与刻度尺压条 (Paper Bail Bar & Margin Scale)
            drawPaperBailBarAndScale(canvas, w, rollerY, paperLeft, paperRight, dp1);

            // 8. 绘制经典中央 V 型字导翼片与红黑双色打印色带 (Type Guide & Dual-Color Ribbon)
            drawTypeGuideAndRibbon(canvas, headCenterX, rollerY, dp1);

            // 9. 绘制 T 型金属打击字锤 (Typebar Hammer，敲击时向前跳动)
            drawTypebarHammer(canvas, headCenterX, rollerY, dp1);

            // 10. 绘制质感铸铁打字机打印头外壳与三色凸面水晶指示灯 (Carriage Housing & Jewel Lamps)
            drawCarriageHousingAndJewels(canvas, headCenterX, rollerY, dp1);
        }

        /** 在纸面上印刷 letter2.jpg 复古公函抬头与紫色戳印 */
        private void drawStationeryHeaderOnPaper(Canvas canvas, int w, float paperTop, float rollerY, float dp1) {
            Paint hp = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            hp.setColor(0xCC33302C);

            // Subject: 与钉书针印
            hp.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
            hp.setTextSize(dp1 * 9.5f);
            canvas.drawText("Subject:", w * 0.12f, paperTop + dp1 * 26f, hp);

            // 针孔暗影
            Paint staple = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            staple.setColor(0x55302C28);
            staple.setStrokeWidth(dp1 * 1.6f);
            canvas.drawLine(w * 0.08f, paperTop + dp1 * 20f, w * 0.08f + dp1 * 10f, paperTop + dp1 * 23f, staple);

            // Form G. 8.
            hp.setTypeface(AppFonts.regular());
            hp.setTextSize(dp1 * 8f);
            canvas.drawText("Form G. 8.", w * 0.76f, paperTop + dp1 * 24f, hp);

            // DONTWANNAFORGET ARCHIVE CO.
            hp.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
            hp.setTextSize(dp1 * 10.5f);
            hp.setLetterSpacing(0.12f);
            hp.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("DONTWANNAFORGET ARCHIVE CO.", w * 0.50f, paperTop + dp1 * 44f, hp);
            hp.setTextAlign(Paint.Align.LEFT);

            // 右上方日期与案卷号
            hp.setTypeface(AppFonts.regular());
            hp.setTextSize(dp1 * 8.5f);
            hp.setLetterSpacing(0.02f);
            canvas.drawText("Hong Kong, " + EnvelopeView.formatEnglishDate(dateStr) + ".", w * 0.54f, paperTop + dp1 * 60f, hp);
            canvas.drawText("File 1420", w * 0.74f, paperTop + dp1 * 72f, hp);

            // 倾斜紫色橡胶验讫印章 (letter2.jpg 同款)
            canvas.save();
            canvas.rotate(-7.5f, w * 0.68f, paperTop + dp1 * 96f);
            Paint stampPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            stampPaint.setColor(0x6E7E395F);
            stampPaint.setStyle(Paint.Style.STROKE);
            stampPaint.setStrokeWidth(dp1 * 1.1f);
            RectF stampRect = new RectF(w * 0.55f, paperTop + dp1 * 80f, w * 0.82f, paperTop + dp1 * 116f);
            canvas.drawRoundRect(stampRect, dp1 * 2f, dp1 * 2f, stampPaint);
            stampPaint.setStrokeWidth(dp1 * 0.6f);
            RectF innerRect = new RectF(stampRect.left + dp1 * 2f, stampRect.top + dp1 * 2f,
                    stampRect.right - dp1 * 2f, stampRect.bottom - dp1 * 2f);
            canvas.drawRoundRect(innerRect, dp1 * 1f, dp1 * 1f, stampPaint);

            stampPaint.setStyle(Paint.Style.FILL);
            stampPaint.setTextAlign(Paint.Align.CENTER);
            stampPaint.setTextSize(dp1 * 6f);
            canvas.drawText("OFFICE · ARCHIVE DEPT.", stampRect.centerX(), stampRect.top + dp1 * 12f, stampPaint);
            stampPaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
            stampPaint.setTextSize(dp1 * 7.5f);
            canvas.drawText("OCT 18 2026", stampRect.centerX(), stampRect.top + dp1 * 23f, stampPaint);
            canvas.restore();
        }

        /** 绘制黑色橡胶打字滚轴 (Platen Roller) 与两侧金属滚轮旋钮 */
        private void drawPlatenRollerAndKnobs(Canvas canvas, int w, float rollerY, float dp1) {
            float barH = dp1 * 20f;
            float topY = rollerY - barH * 0.5f;
            float botY = rollerY + barH * 0.5f;

            // 1. 橡胶滚筒圆柱体光影渲染
            rollerPaint.setShader(new LinearGradient(0, topY, 0, botY,
                    new int[]{0xFF0E0E0E, 0xFF32302D, 0xFF181716, 0xFF080807},
                    new float[]{0f, 0.32f, 0.78f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, topY, w, botY, rollerPaint);
            rollerPaint.setShader(null);

            // 滚筒表面细微橡胶磨砂细槽线
            Paint groove = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            groove.setColor(0x22FFFFFF);
            groove.setStrokeWidth(dp1 * 0.8f);
            canvas.drawLine(0, topY + dp1 * 5f, w, topY + dp1 * 5f, groove);

            // 滚筒底部的深色阴影槽
            Paint slotShadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            slotShadow.setShader(new LinearGradient(0, botY, 0, botY + dp1 * 7f,
                    0x66000000, 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0, botY, w, botY + dp1 * 7f, slotShadow);

            // 2. 两侧金属旋钮 (Platen Knobs)
            drawPlatenKnob(canvas, dp1 * 8f, rollerY, dp1 * 18f, dp1 * 34f, dp1, true);
            drawPlatenKnob(canvas, w - dp1 * 26f, rollerY, dp1 * 18f, dp1 * 34f, dp1, false);
        }

        private void drawPlatenKnob(Canvas canvas, float x, float y, float kw, float kh, float dp1, boolean leftSide) {
            RectF knobRect = new RectF(x, y - kh * 0.5f, x + kw, y + kh * 0.5f);
            Paint kp = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);

            // 旋钮主体金属渐变
            kp.setShader(new LinearGradient(0, knobRect.top, 0, knobRect.bottom,
                    new int[]{0xFF484643, 0xFF1E1D1B, 0xFF353330},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(knobRect, dp1 * 3f, dp1 * 3f, kp);
            kp.setShader(null);

            // 旋钮侧面防滑凹槽齿纹 (Fluted Teeth)
            Paint teethPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            teethPaint.setColor(0x55000000);
            teethPaint.setStrokeWidth(dp1 * 1.5f);
            for (float ty = knobRect.top + dp1 * 4f; ty < knobRect.bottom - dp1 * 2f; ty += dp1 * 5f) {
                canvas.drawLine(knobRect.left + dp1 * 2f, ty, knobRect.right - dp1 * 2f, ty, teethPaint);
            }

            // 旋钮金属轴心高光
            Paint centerCore = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            centerCore.setColor(0xFF787570);
            float coreX = leftSide ? knobRect.right - dp1 * 5f : knobRect.left + dp1 * 5f;
            canvas.drawCircle(coreX, knobRect.centerY(), dp1 * 4f, centerCore);
        }

        /** 绘制不锈钢压纸横杆与刻度标尺 (Paper Bail Bar & Scale) */
        private void drawPaperBailBarAndScale(Canvas canvas, int w, float rollerY, float paperLeft, float paperRight, float dp1) {
            float bailY = rollerY - dp1 * 14f;
            float bailH = dp1 * 5.5f;

            // 压纸杆投射在纸张上的微薄阴影 (极大增强层次)
            Paint bailShadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            bailShadow.setColor(0x44000000);
            canvas.drawRect(paperLeft + dp1 * 4f, bailY + bailH, paperRight - dp1 * 4f, bailY + bailH + dp1 * 2.8f, bailShadow);

            // 不锈钢金属横杆本体 (亮银拉丝镀铬效果)
            Paint bailPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            bailPaint.setShader(new LinearGradient(0, bailY, 0, bailY + bailH,
                    new int[]{0xFF555350, 0xFFF2EFE8, 0xFF2A2927},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(paperLeft + dp1 * 4f, bailY, paperRight - dp1 * 4f, bailY + bailH, bailPaint);

            // 压纸杆左右两颗黑色橡胶小滚轮 (Paper Pinch Rollers)
            Paint rollerPinch = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            rollerPinch.setColor(0xFF161514);
            float rW = dp1 * 8f;
            canvas.drawRoundRect(new RectF(paperLeft + dp1 * 18f, bailY - dp1 * 1.5f, paperLeft + dp1 * 18f + rW, bailY + bailH + dp1 * 1.5f), dp1 * 1.5f, dp1 * 1.5f, rollerPinch);
            canvas.drawRoundRect(new RectF(paperRight - dp1 * 18f - rW, bailY - dp1 * 1.5f, paperRight - dp1 * 18f, bailY + bailH + dp1 * 1.5f), dp1 * 1.5f, dp1 * 1.5f, rollerPinch);

            // 压纸杆上的刻度划线 (| · | · |)
            Paint scaleTick = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            scaleTick.setColor(0x77000000);
            scaleTick.setStrokeWidth(dp1 * 0.8f);
            float tickStart = paperLeft + dp1 * 40f;
            float tickEnd = paperRight - dp1 * 40f;
            for (float x = tickStart; x <= tickEnd; x += dp1 * 8f) {
                canvas.drawLine(x, bailY + dp1 * 1f, x, bailY + bailH - dp1 * 1f, scaleTick);
            }
        }

        /** 绘制经典 V 型镀铬字导翼片与红黑双色织物色带 (Type Guide & Dual-Color Ribbon) */
        private void drawTypeGuideAndRibbon(Canvas canvas, float headCenterX, float rollerY, float dp1) {
            float guideTop = rollerY - dp1 * 26f;
            float guideBottom = rollerY - dp1 * 2f;

            // 1. 红黑双色色带 (Classic Typewriter Ribbon: 上半部黑带，下半部鲜红带)
            float ribbonW = dp1 * 120f;
            float ribbonH = dp1 * 6f;
            float ribbonY = rollerY - dp1 * 18f;

            Paint ribbonBlack = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            ribbonBlack.setColor(0xFF181716);
            canvas.drawRect(headCenterX - ribbonW * 0.5f, ribbonY, headCenterX + ribbonW * 0.5f, ribbonY + ribbonH * 0.5f, ribbonBlack);

            Paint ribbonRed = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            ribbonRed.setColor(0xFFA82626);
            canvas.drawRect(headCenterX - ribbonW * 0.5f, ribbonY + ribbonH * 0.5f, headCenterX + ribbonW * 0.5f, ribbonY + ribbonH, ribbonRed);

            // 2. 镀铬中央 V 型字导翼片 (Type Guide Wings)
            Paint guidePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            guidePaint.setShader(new LinearGradient(headCenterX - dp1 * 16, guideTop, headCenterX + dp1 * 16, guideBottom,
                    new int[]{0xFFE6E2D8, 0xFF7A7772, 0xFFCFCBC2},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

            Path leftWing = new Path();
            leftWing.moveTo(headCenterX - dp1 * 14f, guideTop);
            leftWing.lineTo(headCenterX - dp1 * 3.5f, guideTop + dp1 * 6f);
            leftWing.lineTo(headCenterX - dp1 * 3.5f, guideBottom);
            leftWing.lineTo(headCenterX - dp1 * 14f, guideBottom);
            leftWing.close();
            canvas.drawPath(leftWing, guidePaint);

            Path rightWing = new Path();
            rightWing.moveTo(headCenterX + dp1 * 14f, guideTop);
            rightWing.lineTo(headCenterX + dp1 * 3.5f, guideTop + dp1 * 6f);
            rightWing.lineTo(headCenterX + dp1 * 3.5f, guideBottom);
            rightWing.lineTo(headCenterX + dp1 * 14f, guideBottom);
            rightWing.close();
            canvas.drawPath(rightWing, guidePaint);
        }

        /** 绘制 T 型打击字锤 (Typebar Hammer) 与黄铜卡座 */
        private void drawTypebarHammer(Canvas canvas, float headCenterX, float rollerY, float dp1) {
            float hammerY = rollerY - dp1 * 38f + hammerOffset;

            // 字锤打击产生的纸面微弱落影
            Paint hammerShadow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            hammerShadow.setColor(0x40000000);
            canvas.drawRoundRect(new RectF(headCenterX - dp1 * 11f, hammerY - dp1 * 3f, headCenterX + dp1 * 11f, hammerY + dp1 * 6f),
                    dp1 * 2f, dp1 * 2f, hammerShadow);

            // 锤柄 (Hammer Stem)
            Paint stemPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            stemPaint.setShader(new LinearGradient(headCenterX - dp1 * 2.5f, 0, headCenterX + dp1 * 2.5f, 0,
                    0xFF787570, 0xFFE0DDD5, Shader.TileMode.CLAMP));
            canvas.drawRect(headCenterX - dp1 * 2.2f, hammerY, headCenterX + dp1 * 2.2f, rollerY - dp1 * 14f, stemPaint);

            // T 型锤头 (Hammer Slug Head)
            Paint headPaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            headPaint.setColor(0xFF282725);
            RectF headRect = new RectF(headCenterX - dp1 * 10f, hammerY - dp1 * 6f, headCenterX + dp1 * 10f, hammerY + dp1 * 1.5f);
            canvas.drawRoundRect(headRect, dp1 * 1.5f, dp1 * 1.5f, headPaint);

            // 锤头正面活字铅字雕刻面
            Paint slugFace = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            slugFace.setColor(0xFF88847E);
            canvas.drawRect(headCenterX - dp1 * 7f, hammerY - dp1 * 4.5f, headCenterX + dp1 * 7f, hammerY - dp1 * 0.5f, slugFace);

            // 底部黄铜固定箍 (Brass Collar)
            Paint brassCollar = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            brassCollar.setColor(0xFFD6B254);
            canvas.drawRect(headCenterX - dp1 * 8f, rollerY - dp1 * 16f, headCenterX + dp1 * 8f, rollerY - dp1 * 13f, brassCollar);
        }

        /** 绘制打字机打印头护罩与红黄绿三颗凸透镜水晶指示灯 (Carriage Housing & Jewel Lamps) */
        private void drawCarriageHousingAndJewels(Canvas canvas, float headCenterX, float rollerY, float dp1) {
            RectF carriage = new RectF(headCenterX - dp1 * 50f, rollerY - dp1 * 13f,
                    headCenterX + dp1 * 50f, rollerY + dp1 * 22f);

            // 外壳金属铸件渐变
            carriagePaint.setShader(new LinearGradient(0, carriage.top, 0, carriage.bottom,
                    new int[]{0xFF484642, 0xFF2C2B28, 0xFF1C1B1A},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            carriagePaint.setShadowLayer(dp1 * 10f, 0, dp1 * 5f, 0x99000000);
            canvas.drawRoundRect(carriage, dp1 * 18f, dp1 * 18f, carriagePaint);
            carriagePaint.setShader(null);

            // 外壳顶沿高光反光边
            Paint housingHighlight = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            housingHighlight.setStyle(Paint.Style.STROKE);
            housingHighlight.setStrokeWidth(dp1 * 1.2f);
            housingHighlight.setColor(0x40FFFFFF);
            canvas.drawRoundRect(carriage, dp1 * 18f, dp1 * 18f, housingHighlight);

            // 中央复古铭牌: DONTWANNAFORGET / MODEL 1901
            Paint badgePaint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            badgePaint.setColor(0xFFD8B568);
            badgePaint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
            badgePaint.setTextSize(dp1 * 5.8f);
            badgePaint.setLetterSpacing(0.12f);
            badgePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("DONTWANNAFORGET", carriage.centerX(), carriage.centerY() + dp1 * 2f, badgePaint);

            // 左侧三颗凸面水晶指示灯 (带外圈黄铜卡扣与玻璃折射高光)
            float dotY = carriage.centerY();
            drawJewelLamp(canvas, carriage.left + dp1 * 14f, dotY, dp1 * 3.6f, 0xFF4FD875, 0xFF147A34, dp1);
            drawJewelLamp(canvas, carriage.left + dp1 * 24f, dotY, dp1 * 3.0f, 0xFFFFB547, 0xFFA05510, dp1);
            drawJewelLamp(canvas, carriage.left + dp1 * 33f, dotY, dp1 * 3.0f, 0xFFFF5545, 0xFF8A1414, dp1);
        }

        private void drawJewelLamp(Canvas canvas, float cx, float cy, float rad, int glowColor, int baseColor, float dp1) {
            // 黄铜金属外圈
            Paint bezel = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            bezel.setColor(0xFFB89648);
            canvas.drawCircle(cx, cy, rad + dp1 * 1.2f, bezel);

            // 凸透镜发光渐变
            Paint lens = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            lens.setShader(new RadialGradient(cx - rad * 0.3f, cy - rad * 0.3f, rad,
                    new int[]{0xFFFFFFFF, glowColor, baseColor},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, rad, lens);
        }

        private void drawTypedLinesOnPaper(Canvas canvas, int w, float paperTop, float rollerY,
                                           float headCenterX, float dp1) {
            String display = rawText.isEmpty()
                    ? L10n.s(R.string.ui_119)
                    : (rawText + "_");

            tp.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
            tp.setLetterSpacing(0.06f);
            int textBoxW = Math.round(w * 0.58f);

            StaticLayout sl = StaticLayout.Builder.obtain(display, 0, display.length(), tp, textBoxW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.18f)
                    .setIncludePad(false)
                    .build();

            int lineCount = sl.getLineCount();
            int maxVisibleLines = 11;
            int startLine = Math.max(0, lineCount - maxVisibleLines);

            // 最新一行位于字锤上方 (rollerY - 52dp), 越往上的行字距越紧凑并带透视纵向压缩(模拟卷纸弧度)
            float currentY = rollerY - dp1 * 52f;
            float leftX = w * 0.23f;

            java.util.List<String> visible = new java.util.ArrayList<>();
            for (int i = startLine; i < lineCount; i++) {
                int s = sl.getLineStart(i);
                int e = sl.getLineEnd(i);
                visible.add(display.substring(s, e).replace("\n", ""));
            }

            for (int idx = visible.size() - 1; idx >= 0; idx--) {
                int distFromBottom = (visible.size() - 1) - idx;
                float perspectiveScaleY = Math.max(0.45f, 1.0f - distFromBottom * 0.052f);
                float perspectiveScaleX = Math.max(0.88f, 1.0f - distFromBottom * 0.012f);
                float stepH = dp1 * 23f * perspectiveScaleY;

                int alpha = rawText.isEmpty() ? 130 : Math.max(95, 235 - distFromBottom * 14);
                tp.setColor((alpha << 24) | 0xFF1F1D1A);

                canvas.save();
                canvas.translate(leftX + distFromBottom * dp1 * 1.4f, currentY);
                canvas.scale(perspectiveScaleX, perspectiveScaleY);
                canvas.drawText(visible.get(idx), 0, 0, tp);
                canvas.restore();

                currentY -= stepH;
                if (currentY < paperTop + dp1 * 14) break;
            }
        }
    }
}
