package hk.hkucs.dontwannaforget;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 音乐歌词卡片编辑器 (模仿 example/IMG_3870.jpg):
 * - 支持上传/拍摄歌曲封面或背景图片，自动生成模糊背景 + 左上角清晰小封面；
 * - 支持调节背景模糊程度（轻度模糊 / 中度柔焦 / 深度朦胧 / 清晰压暗）；
 * - 支持切换内置字体与导入本地自定义字体 (.ttf / .otf) 用于歌词渲染；
 * - 移除原参考图底部的 Spotify 标志，支持新建与从详情页随时修改。
 */
public class MusicEditActivity extends AppCompatActivity {

    private String recordId;
    private Record existingRecord;
    private String dateStr;
    private boolean saving = false;

    private final MusicCardStyle style = new MusicCardStyle();
    private Uri newCoverUri;
    private Uri pendingCameraUri;

    private MusicLyricCardView previewCardView;
    private TextView tvDate, tvPhotoStatus, tvSizeVal, btnSave;
    private LinearLayout blurRow, fontRow, weightRow;
    private SeekBar sbLyricSize;

    private EditText etTitle, etSub, etNote, etThought;

    private ActivityResultLauncher<PickVisualMediaRequest> pickCoverLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String[]> pickFontLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        recordId = getIntent().getStringExtra("id");
        dateStr = new SimpleDateFormat("yyyy.MM.dd", Locale.US).format(new Date());
        style.serialCode = String.format(Locale.US, "MUSIC #%04d", (int) (System.currentTimeMillis() % 9000 + 1000));

        registerLaunchers();
        buildUi();

        if (recordId != null) {
            new Repo(this).get(recordId, r -> runOnUiThread(() -> {
                if (r != null) loadFromRecord(r);
            }));
        } else {
            syncPreviewRecord();
        }
    }

    private void registerLaunchers() {
        pickCoverLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                newCoverUri = uri;
                previewCardView.setPreviewImageUri(uri);
                tvPhotoStatus.setText(L10n.s(R.string.ui_598));
            }
        });
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && pendingCameraUri != null) {
                newCoverUri = pendingCameraUri;
                previewCardView.setPreviewImageUri(pendingCameraUri);
                tvPhotoStatus.setText(L10n.s(R.string.ui_599));
            }
        });
        pickFontLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            String fontId = FontManager.importFont(this, uri);
            if (fontId == null) {
                Toast.makeText(this, L10n.s(R.string.ui_467), Toast.LENGTH_LONG).show();
                return;
            }
            style.fontId = fontId;
            refreshFontRow();
            syncPreviewRecord();
            Toast.makeText(this, L10n.s(R.string.ui_600), Toast.LENGTH_SHORT).show();
        });
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "music_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            takePictureLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, L10n.s(R.string.ui_469), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadFromRecord(Record r) {
        existingRecord = r;
        dateStr = r.date;
        tvDate.setText(dateStr);
        etTitle.setText(r.title);
        etSub.setText(r.sub);
        etNote.setText(r.note);

        MusicCardStyle loaded = MusicCardStyle.fromRecord(r);
        style.fontId = loaded.fontId;
        style.textWeight = loaded.textWeight;
        style.lyricSizeSp = loaded.lyricSizeSp;
        style.blurLevel = loaded.blurLevel;
        style.thought = loaded.thought;
        style.serialCode = loaded.serialCode;

        etThought.setText(style.thought);

        int prog = Math.max(0, Math.min(32, Math.round((style.lyricSizeSp - 14f) * 2f)));
        sbLyricSize.setProgress(prog);
        tvSizeVal.setText(String.format(Locale.US, "%.1fsp", style.lyricSizeSp));

        if (MediaIndex.first(r.id, Media.COVER) != null || MediaIndex.first(r.id, Media.PHOTO) != null) {
            tvPhotoStatus.setText(L10n.s(R.string.ui_601));
        }

        refreshAllSelectors();
        syncPreviewRecord();
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(true);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        sv.setBackgroundColor(0xFF191A19);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        col.setPadding(pad, Cards.dp(this, 10), pad, Cards.dp(this, 44));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));

        // 1. 顶部导航栏
        buildTopBar(col);

        // 2. 顶部实时歌词卡片预览区 (仿 IMG_3870.jpg)
        buildLivePreviewSection(col);

        // 3. 背景图片上传与模糊程度卡片
        buildBackgroundAndBlurCard(col);

        // 4. 歌词字体导入、字形风格与字号调节卡片
        buildFontAndStyleCard(col);

        // 5. 歌名、歌手与歌词内容输入卡片
        buildLyricsInputCard(col);

        // 6. 底部保存按钮
        btnSave = Cards.text(this, L10n.s(R.string.ui_602), 15, 0xFF191A19,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setPadding(0, Cards.dp(this, 14), 0, Cards.dp(this, 14));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xFFD8C690);
        sbg.setCornerRadius(Cards.dp(this, 22));
        btnSave.setBackground(sbg);
        EditorUi.action(btnSave);
        btnSave.setOnClickListener(v -> onSave());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Cards.dp(this, 6);
        col.addView(btnSave, slp);

        refreshAllSelectors();

        setContentView(sv);
        ViewCompat.setOnApplyWindowInsetsListener(sv, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
    }

    private void buildTopBar(LinearLayout col) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, Cards.dp(this, 4), 0, Cards.dp(this, 12));

        TextView back = Cards.text(this, L10n.s(R.string.ui_060), 13, 0xFFC7C2B8, AppFonts.regular());
        back.setOnClickListener(v -> finish());
        HomeNavigation.bind(back);
        top.addView(back);

        TextView head = Cards.text(this, recordId != null ? L10n.s(R.string.ui_603) : L10n.s(R.string.ui_604), 15, 0xFFF2EFE9,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        head.setGravity(Gravity.CENTER);
        head.setMaxLines(2);head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(head, new LinearLayout.LayoutParams(0, -2, 1f));

        tvDate = Cards.text(this, dateStr, 12, 0xFFD8C690, AppFonts.regular());
        tvDate.setPadding(Cards.dp(this, 10), Cards.dp(this, 5), Cards.dp(this, 10), Cards.dp(this, 5));
        GradientDrawable dbg = new GradientDrawable();
        dbg.setColor(0xFF282A29);
        dbg.setCornerRadius(Cards.dp(this, 12));
        dbg.setStroke(Cards.dp(this, 1), 0xFF444644);
        tvDate.setBackground(dbg);
        tvDate.setOnClickListener(v -> pickDate());
        top.addView(tvDate);

        col.addView(top);
        FloatingHeader.pin(top);
    }

    private void buildLivePreviewSection(LinearLayout col) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF121312);
        bg.setCornerRadius(Cards.dp(this, 16));
        bg.setStroke(Cards.dp(this, 1), 0xFF2F3230);
        box.setBackground(bg);
        int p = Cards.dp(this, 14);
        box.setPadding(p, p, p, p);

        TextView hint = Cards.text(this, L10n.s(R.string.ui_605),
                11.5f, 0xFFD8C690, AppFonts.regular());
        box.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        previewCardView = new MusicLyricCardView(this);
        previewCardView.setOnClickListener(v -> pickCoverLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(EditorUi.previewWidth(this, 216), -2);
        clp.topMargin = Cards.dp(this, 10);
        clp.bottomMargin = Cards.dp(this, 4);
        box.addView(previewCardView, clp);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = Cards.dp(this, 14);
        col.addView(box, blp);
    }

    private void buildBackgroundAndBlurCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_606));

        tvPhotoStatus = Cards.text(this,
                L10n.s(R.string.ui_607),
                12f, 0xFFB8B4AC, AppFonts.regular());
        tvPhotoStatus.setLineSpacing(0f, 1.35f);
        tvPhotoStatus.setPadding(0, 0, 0, Cards.dp(this, 10));
        card.addView(tvPhotoStatus);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnGallery = buildActionBtn(L10n.s(R.string.ui_608), true);
        btnGallery.setOnClickListener(v -> pickCoverLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp1.rightMargin = Cards.dp(this, 8);
        btnRow.addView(btnGallery, lp1);

        TextView btnCamera = buildActionBtn(L10n.s(R.string.ui_609), false);
        btnCamera.setOnClickListener(v -> launchCamera());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        btnRow.addView(btnCamera, lp2);

        card.addView(btnRow, new LinearLayout.LayoutParams(-1, -2));

        addSubLabel(card, L10n.s(R.string.ui_610));
        blurRow = createHorizontalChipRow(card);
        for (int i = 0; i < MusicCardStyle.BLUR_NAMES().length; i++) {
            final int idx = i;
            TextView chip = createOptionChip(MusicCardStyle.BLUR_NAMES()[i]);
            chip.setOnClickListener(v -> {
                style.blurLevel = idx;
                refreshAllSelectors();
                previewCardView.setStyle(style);
            });
            blurRow.addView(chip);
        }

        col.addView(card);
    }

    private void buildFontAndStyleCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_611));

        addSubLabel(card, L10n.s(R.string.ui_612));
        fontRow = createHorizontalChipRow(card);
        refreshFontRow();

        addSubLabel(card, L10n.s(R.string.ui_613));
        weightRow = createHorizontalChipRow(card);
        for (int i = 0; i < MusicCardStyle.WEIGHT_NAMES().length; i++) {
            final int idx = i;
            TextView chip = createOptionChip(MusicCardStyle.WEIGHT_NAMES()[i]);
            chip.setOnClickListener(v -> {
                style.textWeight = idx;
                refreshAllSelectors();
                syncPreviewRecord();
            });
            weightRow.addView(chip);
        }

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        sizeRow.setPadding(0, Cards.dp(this, 8), 0, 0);
        TextView sizeLbl = Cards.text(this, L10n.s(R.string.ui_614), 12, 0xFFB8B4AC, AppFonts.regular());
        sizeRow.addView(sizeLbl);

        sbLyricSize = new SeekBar(this);
        sbLyricSize.setMax(32); // 14.0sp ~ 30.0sp
        sbLyricSize.setProgress(Math.round((style.lyricSizeSp - 14f) * 2f));
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(0, -2, 1f);
        sblp.leftMargin = Cards.dp(this, 8);
        sblp.rightMargin = Cards.dp(this, 8);
        sizeRow.addView(sbLyricSize, sblp);

        tvSizeVal = Cards.text(this, String.format(Locale.US, "%.1fsp", style.lyricSizeSp),
                11, 0xFFD8C690, AppFonts.regular());
        sizeRow.addView(tvSizeVal);
        card.addView(sizeRow);

        sbLyricSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                style.lyricSizeSp = 14f + progress * 0.5f;
                tvSizeVal.setText(String.format(Locale.US, "%.1fsp", style.lyricSizeSp));
                syncPreviewRecord();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        col.addView(card);
    }

    private void buildLyricsInputCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_615));

        addSubLabel(card, L10n.s(R.string.ui_616));
        etTitle = addStyledEdit(card, L10n.s(R.string.ui_617), 80, true);

        addSubLabel(card, L10n.s(R.string.ui_618));
        etSub = addStyledEdit(card, L10n.s(R.string.ui_619), 80, true);

        addSubLabel(card, L10n.s(R.string.ui_620));
        etNote = addStyledEdit(card,
                L10n.s(R.string.ui_621),
                2000, false);

        addSubLabel(card, L10n.s(R.string.ui_622));
        etThought = addStyledEdit(card, L10n.s(R.string.ui_623), 2000, false);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                style.thought = etThought.getText().toString();
                syncPreviewRecord();
            }
        };
        etTitle.addTextChangedListener(watcher);
        etSub.addTextChangedListener(watcher);
        etNote.addTextChangedListener(watcher);
        etThought.addTextChangedListener(watcher);

        col.addView(card);
    }

    private void refreshFontRow() {
        if (fontRow == null) return;
        fontRow.removeAllViews();
        List<FontManager.FontOption> opts = FontManager.listOptions(this);
        for (FontManager.FontOption fo : opts) {
            TextView chip = createOptionChip(fo.label);
            markChip(chip, fo.id.equals(style.fontId));
            chip.setOnClickListener(v -> {
                style.fontId = fo.id;
                refreshFontRow();
                syncPreviewRecord();
            });
            fontRow.addView(chip);
        }
        TextView importBtn = createOptionChip(L10n.s(R.string.ui_497));
        importBtn.setTextColor(0xFFD8C690);
        importBtn.setOnClickListener(v -> pickFontLauncher.launch(new String[]{"font/*", "application/octet-stream", "*/*"}));
        fontRow.addView(importBtn);
    }

    private void refreshAllSelectors() {
        if (blurRow != null) {
            for (int i = 0; i < blurRow.getChildCount(); i++) {
                markChip((TextView) blurRow.getChildAt(i), i == style.blurLevel);
            }
        }
        if (weightRow != null) {
            for (int i = 0; i < weightRow.getChildCount(); i++) {
                markChip((TextView) weightRow.getChildAt(i), i == style.textWeight);
            }
        }
        refreshFontRow();
    }

    private void syncPreviewRecord() {
        Record r = buildTransientRecord();
        if (newCoverUri != null) {
            previewCardView.setStyle(style);
            previewCardView.invalidate();
        } else {
            previewCardView.bind(r, style, false);
        }
    }

    private Record buildTransientRecord() {
        Record r = existingRecord != null ? existingRecord : new Record();
        r.type = Record.MUSIC;
        String title = etTitle != null ? etTitle.getText().toString().trim() : "";
        String sub = etSub != null ? etSub.getText().toString().trim() : "";
        String note = etNote != null ? etNote.getText().toString().trim() : "";
        String thought = etThought != null ? etThought.getText().toString().trim() : "";

        style.thought = thought;

        r.title = title.isEmpty() ? L10n.s(R.string.ui_170) : title;
        r.sub = sub.isEmpty() ? "Mayday" : sub;
        r.date = dateStr;
        r.note = note.isEmpty()
                ? L10n.s(R.string.ui_171)
                : note;
        if (r.c1 == 0 && r.c2 == 0) {
            r.c1 = Color.parseColor("#2B383E");
            r.c2 = Color.parseColor("#6E5D4F");
        }
        r.fields = new String[]{style.toJson()};
        return r;
    }

    private void onSave() {
        if (saving) return;
        String title = etTitle.getText().toString().trim();
        String sub = etSub.getText().toString().trim();
        String note = etNote.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, L10n.s(R.string.ui_624), Toast.LENGTH_SHORT).show();
            etTitle.requestFocus();
            return;
        }
        if (note.isEmpty()) {
            Toast.makeText(this, L10n.s(R.string.ui_625), Toast.LENGTH_SHORT).show();
            etNote.requestFocus();
            return;
        }

        saving = true;
        btnSave.setText(L10n.s(R.string.ui_159));
        btnSave.setAlpha(0.6f);

        Record r = existingRecord != null ? existingRecord : new Record();
        r.type = Record.MUSIC;
        r.title = title;
        r.sub = sub.isEmpty() ? "Unknown Artist" : sub;
        r.date = dateStr;
        r.note = note;
        style.thought = etThought.getText().toString().trim();
        if (r.c1 == 0 && r.c2 == 0) {
            r.c1 = Color.parseColor("#2B383E");
            r.c2 = Color.parseColor("#6E5D4F");
        }
        r.fields = new String[]{style.toJson()};

        new Repo(this).saveBookRecord(r, newCoverUri, null, ok -> runOnUiThread(() -> {
            if (!ok) {
                saving = false;
                btnSave.setText(L10n.s(R.string.ui_602));
                btnSave.setAlpha(1f);
                Toast.makeText(this, L10n.s(R.string.ui_503), Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, L10n.s(R.string.ui_626), Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        try {
            Date d = new SimpleDateFormat("yyyy.MM.dd", Locale.US).parse(dateStr);
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {}
        new DatePickerDialog(this, (view, y, m, day) -> {
            dateStr = String.format(Locale.US, "%04d.%02d.%02d", y, m + 1, day);
            tvDate.setText(dateStr);
            syncPreviewRecord();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private LinearLayout createSectionCard(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF222423);
        bg.setCornerRadius(Cards.dp(this, 14));
        bg.setStroke(Cards.dp(this, 1), 0xFF333634);
        card.setBackground(bg);
        int p = Cards.dp(this, 12);
        card.setPadding(p, p, p, p);

        TextView head = Cards.text(this, title, 13.5f, 0xFFF2EFE9,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        head.setPadding(0, 0, 0, Cards.dp(this, 6));
        card.addView(head);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Cards.dp(this, 16);
        card.setLayoutParams(lp);
        return card;
    }

    private void addSubLabel(LinearLayout parent, String text) {
        TextView tv = Cards.text(this, text, 11.5f, 0xFF9E9A90, AppFonts.regular());
        tv.setPadding(0, Cards.dp(this, 8), 0, Cards.dp(this, 6));
        parent.addView(tv);
    }

    private LinearLayout createHorizontalChipRow(LinearLayout parent) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(row);
        parent.addView(hsv, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView createOptionChip(String label) {
        TextView tv = Cards.text(this, label, 12f, 0xFFC7C2B8, AppFonts.regular());
        tv.setPadding(Cards.dp(this, 12), Cards.dp(this, 7), Cards.dp(this, 12), Cards.dp(this, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Cards.dp(this, 8);
        tv.setLayoutParams(lp);
        markChip(tv, false);
        return tv;
    }

    private void markChip(TextView chip, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Cards.dp(this, 14));
        if (selected) {
            g.setColor(0xFF3E3A2E);
            g.setStroke(Cards.dp(this, 1.2f), 0xFFD8C690);
            chip.setTextColor(0xFFF5E6B8);
        } else {
            g.setColor(0xFF191B1A);
            g.setStroke(Cards.dp(this, 1f), 0xFF3A3D3B);
            chip.setTextColor(0xFFB8B4AC);
        }
        chip.setBackground(g);
    }

    private EditText addStyledEdit(LinearLayout parent, String hint, int maxLen, boolean singleLine) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(0xFF6E6B64);
        et.setTextColor(0xFFF2EFE9);
        et.setTextSize(14.5f);
        et.setTypeface(AppFonts.regular());
        et.setSingleLine(singleLine);
        if (!singleLine) {
            et.setMinLines(4);
            et.setGravity(Gravity.TOP | Gravity.START);
            et.setLineSpacing(0f, 1.4f);
        }
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF161817);
        bg.setCornerRadius(Cards.dp(this, 10));
        bg.setStroke(Cards.dp(this, 1), 0xFF343735);
        et.setBackground(bg);
        int p = Cards.dp(this, 12);
        et.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Cards.dp(this, 10);
        parent.addView(et, lp);
        EditorUi.input(et,singleLine);
        return et;
    }

    private TextView buildActionBtn(String text, boolean primary) {
        TextView tv = Cards.text(this, text, 13f, primary ? 0xFF191A19 : 0xFFE8E4DC,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Cards.dp(this, 10), Cards.dp(this, 11), Cards.dp(this, 10), Cards.dp(this, 11));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Cards.dp(this, 12));
        if (primary) {
            g.setColor(0xFFD8C690);
        } else {
            g.setColor(0xFF2D302F);
            g.setStroke(Cards.dp(this, 1), 0xFF454846);
        }
        tv.setBackground(g);
        EditorUi.action(tv);
        return tv;
    }
}
