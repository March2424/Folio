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
import android.widget.ImageView;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 读书摘抄便签与心情照片编辑器 (模仿 example/bookexp1.jpg 与 example/bookexp2.jpg):
 * - 默认外层展示仿便签样式的文字（书句摘抄/个人想法），最底下的文字是书名；
 * - 支持选择 bookexp2.jpg 中的 6 种便签器物样式（和纸胶带、活页打孔+金色回形针、黄铜圆图钉+卷角、金色燕尾夹+手撕棉纸、宽边相框便签、牛皮手撕/麻绳签）；
 * - 支持拍摄或从相册选择一张书的封面照片或代表当时心情的照片，在详情页与摘抄、感悟一起展示在同一张便签纸上；
 * - 支持新建以及从详情页随时进入修改。
 */
public class BookEditActivity extends AppCompatActivity {

    private String recordId;
    private Record existingRecord;
    private String dateStr;
    private boolean saving = false;

    private final BookNoteStyle style = new BookNoteStyle();
    private Uri newCoverUri;
    private final List<Uri> extraPhotoUris = new ArrayList<>();
    private Uri pendingCameraUri;

    private BookNoteView previewNoteView;
    private FrameLayout splicedPhotoContainer;
    private ImageView ivSplicedPreview;
    private TextView tvSplicedHint, tvPhotoStatus;
    private TextView tvDate, btnSave;

    private LinearLayout styleVariantRow, paperToneRow, fontRow, alignRow;
    private TextView tvSizeVal, btnShowThoughtToggle;
    private SeekBar sbTextSize;

    private EditText etNote, etTitle, etSub, etThought;

    private ActivityResultLauncher<PickVisualMediaRequest> pickCoverLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultiPhotosLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String[]> pickFontLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        recordId = getIntent().getStringExtra("id");
        dateStr = new SimpleDateFormat("yyyy.MM.dd", Locale.US).format(new Date());
        style.serialCode = String.format(Locale.US, "BOOK #%04d", (int) (System.currentTimeMillis() % 9000 + 1000));

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
                showSplicedPreviewUri(uri);
                tvPhotoStatus.setText(L10n.s(R.string.ui_463));
            }
        });
        pickMultiPhotosLauncher = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(6), uris -> {
            if (uris != null && !uris.isEmpty()) {
                if (newCoverUri == null) {
                    newCoverUri = uris.get(0);
                    showSplicedPreviewUri(newCoverUri);
                    for (int i = 1; i < uris.size(); i++) extraPhotoUris.add(uris.get(i));
                } else {
                    extraPhotoUris.addAll(uris);
                }
                int total = (newCoverUri != null ? 1 : 0) + extraPhotoUris.size();
                tvPhotoStatus.setText(L10n.s(R.string.ui_464) + total + L10n.s(R.string.ui_465));
            }
        });
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && pendingCameraUri != null) {
                newCoverUri = pendingCameraUri;
                showSplicedPreviewUri(pendingCameraUri);
                tvPhotoStatus.setText(L10n.s(R.string.ui_466));
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
            Toast.makeText(this, L10n.s(R.string.ui_468), Toast.LENGTH_SHORT).show();
        });
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "book_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            takePictureLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, L10n.s(R.string.ui_469), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSplicedPreviewUri(Uri uri) {
        if (ivSplicedPreview != null && uri != null) {
            ivSplicedPreview.setImageURI(uri);
            ivSplicedPreview.setVisibility(View.VISIBLE);
            splicedPhotoContainer.setVisibility(View.VISIBLE);
            if (tvSplicedHint != null) {
                tvSplicedHint.setText(L10n.s(R.string.ui_470));
            }
        }
    }

    private void loadFromRecord(Record r) {
        existingRecord = r;
        dateStr = r.date;
        tvDate.setText(dateStr);
        etTitle.setText(r.title);
        etSub.setText(r.sub);
        etNote.setText(r.note);

        BookNoteStyle loaded = BookNoteStyle.fromRecord(r);
        style.noteStyle = loaded.noteStyle;
        style.paperTone = loaded.paperTone;
        style.fontId = loaded.fontId;
        style.textSizeSp = loaded.textSizeSp;
        style.centerAlign = loaded.centerAlign;
        style.thought = loaded.thought;
        style.showThoughtOnCard = loaded.showThoughtOnCard;
        style.serialCode = loaded.serialCode;

        etThought.setText(style.thought);

        int prog = Math.max(0, Math.min(20, Math.round((style.textSizeSp - 12f) * 2f)));
        sbTextSize.setProgress(prog);
        tvSizeVal.setText(String.format(Locale.US, "%.1fsp", style.textSizeSp));

        Media cov = MediaIndex.first(r.id, Media.COVER);
        if (cov == null) cov = MediaIndex.first(r.id, Media.PHOTO);
        if (cov != null) {
            ivSplicedPreview.setVisibility(View.VISIBLE);
            splicedPhotoContainer.setVisibility(View.VISIBLE);
            ImageLoader.into(ivSplicedPreview, cov, 720);
            tvSplicedHint.setText(L10n.s(R.string.ui_470));
            tvPhotoStatus.setText(L10n.s(R.string.ui_471));
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

        // 2. 外层便签样式预览与配图选择
        buildLivePreviewSection(col);

        // 3. 便签样式选择卡片 (仿 bookexp2.jpg 的 6 种器物便签 + 纸张底色 + 字体与字号)
        buildNoteStyleCard(col);

        // 4. 书中句子摘抄、个人想法与书名输入卡片
        buildExcerptInputCard(col);

        // 5. 书封照片 / 心情照片拍摄与上传卡片 (详情页统一放在便签内)
        buildPhotoUploadCard(col);

        // 6. 底部保存按钮
        btnSave = Cards.text(this, L10n.s(R.string.ui_472), 15, 0xFF191A19,
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

        TextView head = Cards.text(this, recordId != null ? L10n.s(R.string.ui_473) : L10n.s(R.string.ui_474), 15, 0xFFF2EFE9,
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

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = Cards.text(this, L10n.s(R.string.ui_475), 11.5f, 0xFFD8C690, AppFonts.regular());
        headerRow.addView(hint, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView nextStyleBtn = Cards.text(this, L10n.s(R.string.ui_476), 11.5f, Color.WHITE, AppFonts.regular());
        nextStyleBtn.setPadding(Cards.dp(this, 10), Cards.dp(this, 4), Cards.dp(this, 10), Cards.dp(this, 4));
        GradientDrawable nbg = new GradientDrawable();
        nbg.setColor(0xFF323534);
        nbg.setCornerRadius(Cards.dp(this, 12));
        nextStyleBtn.setBackground(nbg);
        nextStyleBtn.setOnClickListener(v -> {
            style.noteStyle = (style.noteStyle + 1) % BookNoteStyle.STYLE_NAMES().length;
            refreshAllSelectors();
            syncPreviewRecord();
        });
        headerRow.addView(nextStyleBtn);
        box.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));

        // 摘抄便签本体预览
        previewNoteView = new BookNoteView(this);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(EditorUi.previewWidth(this, 216), -2);
        nlp.topMargin = Cards.dp(this, 12);
        box.addView(previewNoteView, nlp);

        // 配图选择区；详情页中的照片位于同一张纸的摘抄上方
        splicedPhotoContainer = new FrameLayout(this);
        GradientDrawable spBg = new GradientDrawable();
        spBg.setColor(0xFF1D1F1E);
        spBg.setCornerRadius(Cards.dp(this, 6));
        spBg.setStroke(Cards.dp(this, 1), 0xFF343735);
        splicedPhotoContainer.setBackground(spBg);

        ivSplicedPreview = new ImageView(this);
        ivSplicedPreview.setAdjustViewBounds(true);
        ivSplicedPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivSplicedPreview.setVisibility(View.GONE);
        splicedPhotoContainer.addView(ivSplicedPreview, new FrameLayout.LayoutParams(-1, Cards.dp(this, 150)));

        tvSplicedHint = Cards.text(this, L10n.s(R.string.ui_477),
                11f, 0xFF9E9A90, AppFonts.regular());
        tvSplicedHint.setGravity(Gravity.CENTER);
        int hp = Cards.dp(this, 12);
        tvSplicedHint.setPadding(hp, hp, hp, hp);
        splicedPhotoContainer.addView(tvSplicedHint, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        splicedPhotoContainer.setOnClickListener(v -> pickCoverLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        LinearLayout.LayoutParams splp = new LinearLayout.LayoutParams(EditorUi.previewWidth(this, 208), -2);
        splp.topMargin = Cards.dp(this, 2);
        box.addView(splicedPhotoContainer, splp);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = Cards.dp(this, 14);
        col.addView(box, blp);
    }

    private void buildNoteStyleCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_478));

        addSubLabel(card, L10n.s(R.string.ui_479));
        styleVariantRow = createHorizontalChipRow(card);
        for (int i = 0; i < BookNoteStyle.STYLE_NAMES().length; i++) {
            final int idx = i;
            TextView chip = createOptionChip(BookNoteStyle.STYLE_NAMES()[i]);
            chip.setOnClickListener(v -> {
                style.noteStyle = idx;
                if (idx == BookNoteStyle.STYLE_KRAFT_TAG) {
                    style.paperTone = BookNoteStyle.TONE_KRAFT_BROWN;
                } else if (idx == BookNoteStyle.STYLE_WASHI_SQUARE) {
                    style.paperTone = BookNoteStyle.TONE_RICE_WHITE;
                }
                refreshAllSelectors();
                syncPreviewRecord();
            });
            styleVariantRow.addView(chip);
        }

        addSubLabel(card, L10n.s(R.string.ui_480));
        paperToneRow = createHorizontalChipRow(card);
        for (int i = 0; i < BookNoteStyle.PAPER_TONE_NAMES().length; i++) {
            final int idx = i;
            TextView chip = createOptionChip(BookNoteStyle.PAPER_TONE_NAMES()[i]);
            chip.setOnClickListener(v -> {
                style.paperTone = idx;
                refreshAllSelectors();
                syncPreviewRecord();
            });
            paperToneRow.addView(chip);
        }

        addSubLabel(card, L10n.s(R.string.ui_481));
        fontRow = createHorizontalChipRow(card);
        refreshFontRow();

        addSubLabel(card, L10n.s(R.string.ui_482));
        alignRow = createHorizontalChipRow(card);
        TextView centerChip = createOptionChip(L10n.s(R.string.ui_483));
        centerChip.setOnClickListener(v -> {
            style.centerAlign = true;
            refreshAllSelectors();
            syncPreviewRecord();
        });
        TextView leftChip = createOptionChip(L10n.s(R.string.ui_484));
        leftChip.setOnClickListener(v -> {
            style.centerAlign = false;
            refreshAllSelectors();
            syncPreviewRecord();
        });
        alignRow.addView(centerChip);
        alignRow.addView(leftChip);

        // 字号调节滑块
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        sizeRow.setPadding(0, Cards.dp(this, 8), 0, 0);
        TextView sizeLbl = Cards.text(this, L10n.s(R.string.ui_485), 12, 0xFFB8B4AC, AppFonts.regular());
        sizeRow.addView(sizeLbl);

        sbTextSize = new SeekBar(this);
        sbTextSize.setMax(20); // 12.0sp ~ 22.0sp
        sbTextSize.setProgress(Math.round((style.textSizeSp - 12f) * 2f));
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(0, -2, 1f);
        sblp.leftMargin = Cards.dp(this, 8);
        sblp.rightMargin = Cards.dp(this, 8);
        sizeRow.addView(sbTextSize, sblp);

        tvSizeVal = Cards.text(this, String.format(Locale.US, "%.1fsp", style.textSizeSp),
                11, 0xFFD8C690, AppFonts.regular());
        sizeRow.addView(tvSizeVal);
        card.addView(sizeRow);

        sbTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                style.textSizeSp = 12f + progress * 0.5f;
                tvSizeVal.setText(String.format(Locale.US, "%.1fsp", style.textSizeSp));
                syncPreviewRecord();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        col.addView(card);
    }

    private void buildExcerptInputCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_486));

        addSubLabel(card, L10n.s(R.string.ui_487));
        etNote = addStyledEdit(card,
                L10n.s(R.string.ui_488),
                2000, false);

        addSubLabel(card, L10n.s(R.string.ui_137));
        etTitle = addStyledEdit(card, L10n.s(R.string.ui_489), 80, true);

        addSubLabel(card, L10n.s(R.string.ui_490));
        etSub = addStyledEdit(card, L10n.s(R.string.ui_491), 80, true);

        addSubLabel(card, L10n.s(R.string.ui_492));
        etThought = addStyledEdit(card, L10n.s(R.string.ui_493), 2000, false);

        btnShowThoughtToggle = createOptionChip(L10n.s(R.string.ui_494));
        btnShowThoughtToggle.setOnClickListener(v -> {
            style.showThoughtOnCard = !style.showThoughtOnCard;
            refreshAllSelectors();
            syncPreviewRecord();
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Cards.dp(this, 6);
        card.addView(btnShowThoughtToggle, tlp);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                style.thought = etThought.getText().toString();
                syncPreviewRecord();
            }
        };
        etNote.addTextChangedListener(watcher);
        etTitle.addTextChangedListener(watcher);
        etSub.addTextChangedListener(watcher);
        etThought.addTextChangedListener(watcher);

        col.addView(card);
    }

    private void buildPhotoUploadCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_495));

        tvPhotoStatus = Cards.text(this,
                L10n.s(R.string.ui_496),
                12f, 0xFFB8B4AC, AppFonts.regular());
        tvPhotoStatus.setLineSpacing(0f, 1.35f);
        tvPhotoStatus.setPadding(0, 0, 0, Cards.dp(this, 10));
        card.addView(tvPhotoStatus);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnCamera = buildActionBtn(L10n.s(R.string.ui_010), true);
        btnCamera.setOnClickListener(v -> launchCamera());
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp1.rightMargin = Cards.dp(this, 8);
        btnRow.addView(btnCamera, lp1);

        TextView btnGallery = buildActionBtn(L10n.s(R.string.ui_129), false);
        btnGallery.setOnClickListener(v -> pickMultiPhotosLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        btnRow.addView(btnGallery, lp2);

        card.addView(btnRow, new LinearLayout.LayoutParams(-1, -2));
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
        if (styleVariantRow != null) {
            for (int i = 0; i < styleVariantRow.getChildCount(); i++) {
                markChip((TextView) styleVariantRow.getChildAt(i), i == style.noteStyle);
            }
        }
        if (paperToneRow != null) {
            for (int i = 0; i < paperToneRow.getChildCount(); i++) {
                markChip((TextView) paperToneRow.getChildAt(i), i == style.paperTone);
            }
        }
        if (alignRow != null && alignRow.getChildCount() >= 2) {
            markChip((TextView) alignRow.getChildAt(0), style.centerAlign);
            markChip((TextView) alignRow.getChildAt(1), !style.centerAlign);
        }
        if (btnShowThoughtToggle != null) {
            btnShowThoughtToggle.setText(style.showThoughtOnCard
                    ? L10n.s(R.string.ui_498)
                    : L10n.s(R.string.ui_494));
            markChip(btnShowThoughtToggle, style.showThoughtOnCard);
        }
        refreshFontRow();
    }

    private void syncPreviewRecord() {
        Record r = buildTransientRecord();
        previewNoteView.bind(r, style, false);
    }

    private Record buildTransientRecord() {
        Record r = existingRecord != null ? existingRecord : new Record();
        r.type = Record.BOOK;
        String title = etTitle != null ? etTitle.getText().toString().trim() : "";
        String sub = etSub != null ? etSub.getText().toString().trim() : "";
        String note = etNote != null ? etNote.getText().toString().trim() : "";
        String thought = etThought != null ? etThought.getText().toString().trim() : "";

        style.thought = thought;

        r.title = title.isEmpty() ? L10n.s(R.string.ui_499) : title;
        r.sub = sub;
        r.date = dateStr;
        if (!note.isEmpty()) {
            r.note = note;
        } else if (!thought.isEmpty()) {
            r.note = thought;
        } else {
            r.note = L10n.s(R.string.ui_500);
        }
        r.c1 = 0xFF6B7A6A;
        r.c2 = 0xFFC9CDB8;
        r.fields = new String[]{style.toJson()};
        return r;
    }

    private void onSave() {
        if (saving) return;
        String title = etTitle.getText().toString().trim();
        String note = etNote.getText().toString().trim();
        String thought = etThought.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, L10n.s(R.string.ui_501), Toast.LENGTH_SHORT).show();
            etTitle.requestFocus();
            return;
        }
        if (note.isEmpty() && thought.isEmpty()) {
            Toast.makeText(this, L10n.s(R.string.ui_502), Toast.LENGTH_SHORT).show();
            etNote.requestFocus();
            return;
        }

        saving = true;
        btnSave.setText(L10n.s(R.string.ui_159));
        btnSave.setAlpha(0.6f);

        Record r = existingRecord != null ? existingRecord : new Record();
        r.type = Record.BOOK;
        r.title = title;
        r.sub = etSub.getText().toString().trim();
        r.date = dateStr;
        r.note = !note.isEmpty() ? note : thought;
        if (note.isEmpty()) {
            style.thought = "";
        } else {
            style.thought = thought;
        }
        if (r.c1 == 0 && r.c2 == 0) {
            r.c1 = Color.parseColor("#6B7A6A");
            r.c2 = Color.parseColor("#C9CDB8");
        }
        r.fields = new String[]{style.toJson()};

        new Repo(this).saveBookRecord(r, newCoverUri, extraPhotoUris, ok -> runOnUiThread(() -> {
            if (!ok) {
                saving = false;
                btnSave.setText(L10n.s(R.string.ui_472));
                btnSave.setAlpha(1f);
                Toast.makeText(this, L10n.s(R.string.ui_503), Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, L10n.s(R.string.ui_504), Toast.LENGTH_SHORT).show();
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
            et.setMinLines(3);
            et.setGravity(Gravity.TOP | Gravity.START);
            et.setLineSpacing(0f, 1.45f);
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
