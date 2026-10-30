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
import androidx.appcompat.app.AlertDialog;
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
 * 电影票根与便签/海报编辑器 (模仿 example/layoutexp3.jpg):
 * - 正面可选择:
 *   1. 便签文字排版 (标题与其余正文字体、大小不同，支持穿插式/右侧交叠式/暗夜斜签式/姜黄横幅式)
 *   2. 竖排电影海报
 *   3. 横排电影海报
 * - 容器边框可选择: 随机纹路 / 细密齿痕 / 侧边手撕 / 四周撕裂 / 复古便签+NEW贴
 * - 背面为电影票根: 支持直接拍照扫描或相册上传票根原件(带高对比热敏票扫描增强滤镜)，并提供 5 套电影院票根模版套用
 */
public class TicketEditActivity extends AppCompatActivity {

    private String recordId;
    private Record existingRecord;
    private String dateStr;
    private boolean saving = false;

    private final TicketStyle style = new TicketStyle();
    private Uri newCoverUri;
    private Uri newTicketScanUri;
    private Uri pendingCameraUri;
    private int cameraTarget = 0; // 0 = 扫描/拍摄票根(背面), 1 = 拍摄海报(正面)
    private int fontTarget = 0;   // 0 = 标题字体, 1 = 正文字体

    private TicketCardView previewCard;
    private TextView tvFlipState, tvDate, btnSave;
    private LinearLayout frontModeRow, edgeStyleRow, noteLayoutRow, paperToneRow;
    private LinearLayout titleFontRow, bodyFontRow, ticketTplRow;
    private LinearLayout noteOptionsSection, posterUploadSection;
    private TextView tvTitleSizeVal, tvBodySizeVal, btnScanFilterToggle, btnNewBadgeToggle;
    private TextView tvCoverStatus, tvScanStatus;
    private SeekBar sbTitleSize, sbBodySize;

    private EditText etTitle, etSub, etNote, etRating;
    private EditText etCinema, etHall, etSeat, etVersion, etPrice, etTime;

    private ActivityResultLauncher<PickVisualMediaRequest> pickCoverLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickTicketScanLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String[]> pickFontLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        recordId = getIntent().getStringExtra("id");
        dateStr = new SimpleDateFormat("yyyy.MM.dd", Locale.US).format(new Date());
        style.serialCode = String.format(Locale.US, "ID #%06d", (int) (System.currentTimeMillis() % 900000 + 100000));

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
                previewCard.setPreviewCoverUri(uri);
                previewCard.setShowingBack(false);
                updateFlipButtonText();
                tvCoverStatus.setText(L10n.s(R.string.ui_529));
            }
        });
        pickTicketScanLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                newTicketScanUri = uri;
                previewCard.setPreviewTicketScanUri(uri);
                previewCard.setShowingBack(true);
                updateFlipButtonText();
                tvScanStatus.setText(L10n.s(R.string.ui_530));
            }
        });
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && pendingCameraUri != null) {
                if (cameraTarget == 1) {
                    newCoverUri = pendingCameraUri;
                    previewCard.setPreviewCoverUri(pendingCameraUri);
                    previewCard.setShowingBack(false);
                    updateFlipButtonText();
                    tvCoverStatus.setText(L10n.s(R.string.ui_531));
                } else {
                    newTicketScanUri = pendingCameraUri;
                    previewCard.setPreviewTicketScanUri(pendingCameraUri);
                    previewCard.setShowingBack(true);
                    updateFlipButtonText();
                    tvScanStatus.setText(L10n.s(R.string.ui_532));
                }
            }
        });
        pickFontLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            String fontId = FontManager.importFont(this, uri);
            if (fontId == null) {
                Toast.makeText(this, L10n.s(R.string.ui_467), Toast.LENGTH_LONG).show();
                return;
            }
            if (fontTarget == 0) {
                style.titleFontId = fontId;
            } else {
                style.bodyFontId = fontId;
            }
            refreshFontRows();
            syncPreviewRecord();
            Toast.makeText(this, L10n.s(R.string.ui_533), Toast.LENGTH_SHORT).show();
        });
    }

    private void launchCameraFor(int target) {
        cameraTarget = target;
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "ticket_" + System.currentTimeMillis() + ".jpg");
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
        if (r.rating != null) etRating.setText(r.rating);

        if (r.fields != null) {
            if (r.fields.length > 0) etCinema.setText(r.fields[0]);
            if (r.fields.length > 1) etHall.setText(r.fields[1]);
            if (r.fields.length > 2) etSeat.setText(r.fields[2]);
            if (r.fields.length > 3) etVersion.setText(r.fields[3]);
            if (r.fields.length > 4) etPrice.setText(r.fields[4]);
            if (r.fields.length > 5) etTime.setText(r.fields[5]);
        }

        TicketStyle loaded = TicketStyle.fromRecord(r);
        style.frontMode = loaded.frontMode;
        style.noteLayout = loaded.noteLayout;
        style.edgeStyle = loaded.edgeStyle;
        style.paperTone = loaded.paperTone;
        style.ticketTemplate = loaded.ticketTemplate;
        style.scanEnhance = loaded.scanEnhance;
        style.showNewBadge = loaded.showNewBadge;
        style.titleFontId = loaded.titleFontId;
        style.bodyFontId = loaded.bodyFontId;
        style.titleSizeSp = loaded.titleSizeSp;
        style.bodySizeSp = loaded.bodySizeSp;
        style.serialCode = loaded.serialCode;

        sbTitleSize.setProgress(Math.max(0, Math.min(18, Math.round(style.titleSizeSp - 20f))));
        sbBodySize.setProgress(Math.max(0, Math.min(8, Math.round((style.bodySizeSp - 9.5f) * 2f))));
        tvTitleSizeVal.setText(Math.round(style.titleSizeSp) + "sp");
        tvBodySizeVal.setText(String.format(Locale.US, "%.1fsp", style.bodySizeSp));

        if (MediaIndex.first(r.id, Media.COVER) != null) {
            tvCoverStatus.setText(L10n.s(R.string.ui_534));
        }
        if (MediaIndex.first(r.id, Media.ORIGINAL) != null) {
            tvScanStatus.setText(L10n.s(R.string.ui_535));
        }

        refreshAllSelectors();
        syncPreviewRecord();
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(true);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        sv.setBackgroundColor(0xFF191A19); // 与 layoutexp3.jpg 暗调黑灰底色一致

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        col.setPadding(pad, Cards.dp(this, 10), pad, Cards.dp(this, 44));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));

        // 1. 顶部栏
        buildTopBar(col);

        // 2. 顶部实时正反面预览区 (点击可翻转查看正面便签/海报 与 背面电影票根)
        buildLivePreviewSection(col);

        // 3. 正面样式与容器边框设置卡片 (便签排版 vs 横竖海报 + 齿痕/撕裂纹路 + 标题/正文不同字体与大小)
        buildFrontStyleCard(col);

        // 4. 电影内容输入卡片 (片名标题 + 导演备注 + 便签感想文字)
        buildMovieContentCard(col);

        // 5. 背面电影票根扫描上传与影院票根模版卡片
        buildTicketBackCard(col);

        // 6. 底部保存按钮
        btnSave = Cards.text(this, L10n.s(R.string.ui_536), 15, 0xFF191A19,
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

        TextView head = Cards.text(this, L10n.s(R.string.ui_537), 15, 0xFFF2EFE9,
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

        // 顶部切换正反面提示条
        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        tvFlipState = Cards.text(this, L10n.s(R.string.ui_538), 12, 0xFFD8C690, AppFonts.regular());
        switchRow.addView(tvFlipState, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView flipBtn = Cards.text(this, L10n.s(R.string.ui_539), 12, Color.WHITE, AppFonts.regular());
        flipBtn.setPadding(Cards.dp(this, 10), Cards.dp(this, 5), Cards.dp(this, 10), Cards.dp(this, 5));
        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(0xFF323534);
        fbg.setCornerRadius(Cards.dp(this, 12));
        flipBtn.setBackground(fbg);
        flipBtn.setOnClickListener(v -> {
            previewCard.setShowingBack(!previewCard.isShowingBack());
            updateFlipButtonText();
        });
        switchRow.addView(flipBtn);
        box.addView(switchRow, new LinearLayout.LayoutParams(-1, -2));

        previewCard = new TicketCardView(this);
        previewCard.setOnClickListener(v -> {
            previewCard.setShowingBack(!previewCard.isShowingBack());
            updateFlipButtonText();
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(EditorUi.previewWidth(this, 216), -2);
        clp.topMargin = Cards.dp(this, 12);
        clp.bottomMargin = Cards.dp(this, 4);
        box.addView(previewCard, clp);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = Cards.dp(this, 14);
        col.addView(box, blp);
    }

    private void updateFlipButtonText() {
        if (previewCard.isShowingBack()) {
            tvFlipState.setText(L10n.s(R.string.ui_540));
        } else {
            tvFlipState.setText(L10n.s(R.string.ui_541) + TicketStyle.FRONT_MODE_NAMES()[style.frontMode] + ")");
        }
    }

    private void buildFrontStyleCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_542));

        // 1. 正面模式选择 (便签文字排版 / 竖排电影海报 / 横排电影海报)
        card.addView(sectionLabel(L10n.s(R.string.ui_543)));
        frontModeRow = buildHorizontalChipRow(card);

        // 2. 容器边框纹路选择 (随机纹路 / 细密齿痕 / 侧边手撕 / 四周撕裂 / 复古便签+NEW贴)
        card.addView(sectionLabel(L10n.s(R.string.ui_544)));
        edgeStyleRow = buildHorizontalChipRow(card);

        // 3. 便签文字排版专属设置区
        noteOptionsSection = new LinearLayout(this);
        noteOptionsSection.setOrientation(LinearLayout.VERTICAL);

        noteOptionsSection.addView(sectionLabel(L10n.s(R.string.ui_545)));
        noteLayoutRow = buildHorizontalChipRow(noteOptionsSection);

        noteOptionsSection.addView(sectionLabel(L10n.s(R.string.ui_546)));
        paperToneRow = buildHorizontalChipRow(noteOptionsSection);

        // NEW 角标贴纸开关
        btnNewBadgeToggle = Cards.text(this, L10n.s(R.string.ui_547), 12, 0xFFD8C690, AppFonts.regular());
        btnNewBadgeToggle.setPadding(Cards.dp(this, 12), Cards.dp(this, 7), Cards.dp(this, 12), Cards.dp(this, 7));
        btnNewBadgeToggle.setOnClickListener(v -> {
            style.showNewBadge = !style.showNewBadge;
            refreshNewBadgeToggle();
            syncPreviewRecord();
        });
        LinearLayout.LayoutParams nblp = new LinearLayout.LayoutParams(-2, -2);
        nblp.topMargin = Cards.dp(this, 6);
        nblp.bottomMargin = Cards.dp(this, 6);
        noteOptionsSection.addView(btnNewBadgeToggle, nblp);

        // 标题字体与字号 vs 正文字体与字号
        noteOptionsSection.addView(sectionLabel(L10n.s(R.string.ui_548)));
        titleFontRow = buildHorizontalChipRow(noteOptionsSection);

        LinearLayout tSizeRow = new LinearLayout(this);
        tSizeRow.setGravity(Gravity.CENTER_VERTICAL);
        tSizeRow.addView(Cards.text(this, L10n.s(R.string.ui_549), 12, 0xFFB5B0A8, AppFonts.regular()));
        sbTitleSize = new SeekBar(this);
        sbTitleSize.setMax(18); // 20sp .. 38sp
        sbTitleSize.setProgress(8);
        sbTitleSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                style.titleSizeSp = 20f + progress;
                tvTitleSizeVal.setText(Math.round(style.titleSizeSp) + "sp");
                syncPreviewRecord();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        tSizeRow.addView(sbTitleSize, new LinearLayout.LayoutParams(0, -2, 1f));
        tvTitleSizeVal = Cards.text(this, "28sp", 12, 0xFFD8C690, AppFonts.regular());
        tSizeRow.addView(tvTitleSizeVal);
        noteOptionsSection.addView(tSizeRow, new LinearLayout.LayoutParams(-1, -2));

        noteOptionsSection.addView(sectionLabel(L10n.s(R.string.ui_550)));
        bodyFontRow = buildHorizontalChipRow(noteOptionsSection);

        LinearLayout bSizeRow = new LinearLayout(this);
        bSizeRow.setGravity(Gravity.CENTER_VERTICAL);
        bSizeRow.addView(Cards.text(this, L10n.s(R.string.ui_551), 12, 0xFFB5B0A8, AppFonts.regular()));
        sbBodySize = new SeekBar(this);
        sbBodySize.setMax(8); // 9.5sp .. 13.5sp
        sbBodySize.setProgress(4);
        sbBodySize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                style.bodySizeSp = 9.5f + progress * 0.5f;
                tvBodySizeVal.setText(String.format(Locale.US, "%.1fsp", style.bodySizeSp));
                syncPreviewRecord();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        bSizeRow.addView(sbBodySize, new LinearLayout.LayoutParams(0, -2, 1f));
        tvBodySizeVal = Cards.text(this, "11.5sp", 12, 0xFFD8C690, AppFonts.regular());
        bSizeRow.addView(tvBodySizeVal);
        noteOptionsSection.addView(bSizeRow, new LinearLayout.LayoutParams(-1, -2));

        card.addView(noteOptionsSection, new LinearLayout.LayoutParams(-1, -2));

        // 4. 电影海报上传区 (当选择竖排/横排海报时突出显示，便签模式下也可上传备用)
        posterUploadSection = new LinearLayout(this);
        posterUploadSection.setOrientation(LinearLayout.VERTICAL);
        posterUploadSection.setPadding(0, Cards.dp(this, 8), 0, 0);

        tvCoverStatus = Cards.text(this, L10n.s(R.string.ui_552), 11, 0xFF9E988E, AppFonts.regular());
        posterUploadSection.addView(tvCoverStatus);

        LinearLayout posterBtnRow = new LinearLayout(this);
        posterBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        posterBtnRow.setPadding(0, Cards.dp(this, 6), 0, 0);

        TextView btnPickPoster = actionButton(L10n.s(R.string.ui_553), 0xFF2E3330, 0xFFE8E4DC);
        btnPickPoster.setOnClickListener(v -> pickCoverLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1f);
        p1.rightMargin = Cards.dp(this, 8);
        posterBtnRow.addView(btnPickPoster, p1);

        TextView btnShootPoster = actionButton(L10n.s(R.string.ui_554), 0xFF2E3330, 0xFFE8E4DC);
        btnShootPoster.setOnClickListener(v -> launchCameraFor(1));
        posterBtnRow.addView(btnShootPoster, new LinearLayout.LayoutParams(0, -2, 1f));

        posterUploadSection.addView(posterBtnRow, new LinearLayout.LayoutParams(-1, -2));
        card.addView(posterUploadSection, new LinearLayout.LayoutParams(-1, -2));

        col.addView(card, sectionCardMargin());
    }

    private void buildMovieContentCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_555));

        etTitle = addStyledEdit(card, L10n.s(R.string.ui_556), 60, true);
        etSub = addStyledEdit(card, L10n.s(R.string.ui_557), 80, true);
        etNote = addStyledEdit(card, L10n.s(R.string.ui_558), 600, false);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { syncPreviewRecord(); }
        };
        etTitle.addTextChangedListener(watcher);
        etSub.addTextChangedListener(watcher);
        etNote.addTextChangedListener(watcher);

        col.addView(card, sectionCardMargin());
    }

    private void buildTicketBackCard(LinearLayout col) {
        LinearLayout card = createSectionCard(L10n.s(R.string.ui_559));

        tvScanStatus = Cards.text(this, L10n.s(R.string.ui_560), 11, 0xFF9E988E, AppFonts.regular());
        card.addView(tvScanStatus);

        LinearLayout scanBtnRow = new LinearLayout(this);
        scanBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        scanBtnRow.setPadding(0, Cards.dp(this, 8), 0, Cards.dp(this, 8));

        TextView btnCameraScan = actionButton(L10n.s(R.string.ui_561), 0xFF7A2E25, Color.WHITE);
        btnCameraScan.setOnClickListener(v -> launchCameraFor(0));
        LinearLayout.LayoutParams s1 = new LinearLayout.LayoutParams(0, -2, 1f);
        s1.rightMargin = Cards.dp(this, 8);
        scanBtnRow.addView(btnCameraScan, s1);

        TextView btnGalleryScan = actionButton(L10n.s(R.string.ui_562), 0xFF2E3330, 0xFFE8E4DC);
        btnGalleryScan.setOnClickListener(v -> pickTicketScanLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        scanBtnRow.addView(btnGalleryScan, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(scanBtnRow, new LinearLayout.LayoutParams(-1, -2));

        // 扫描高对比滤镜开关
        btnScanFilterToggle = Cards.text(this, L10n.s(R.string.ui_563), 12, 0xFFD8C690, AppFonts.regular());
        btnScanFilterToggle.setPadding(Cards.dp(this, 12), Cards.dp(this, 7), Cards.dp(this, 12), Cards.dp(this, 7));
        btnScanFilterToggle.setOnClickListener(v -> {
            style.scanEnhance = !style.scanEnhance;
            refreshScanFilterToggle();
            previewCard.setShowingBack(true);
            updateFlipButtonText();
            syncPreviewRecord();
        });
        card.addView(btnScanFilterToggle);

        // 5 套电影院票根模版选择
        card.addView(sectionLabel(L10n.s(R.string.ui_564)));
        ticketTplRow = buildHorizontalChipRow(card);

        // 票面具体信息双列输入
        card.addView(sectionLabel(L10n.s(R.string.ui_565)));

        LinearLayout row1 = new LinearLayout(this);
        etCinema = addCompactEdit(row1, L10n.s(R.string.ui_566), L10n.s(R.string.ui_514));
        etHall = addCompactEdit(row1, L10n.s(R.string.ui_567), L10n.s(R.string.ui_515));
        card.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(this);
        etSeat = addCompactEdit(row2, L10n.s(R.string.ui_568), L10n.s(R.string.ui_516));
        etVersion = addCompactEdit(row2, L10n.s(R.string.ui_569), L10n.s(R.string.ui_517));
        card.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row3 = new LinearLayout(this);
        etPrice = addCompactEdit(row3, L10n.s(R.string.ui_570), "¥55");
        etTime = addCompactEdit(row3, L10n.s(R.string.ui_571), "19:40");
        etRating = addCompactEdit(row3, L10n.s(R.string.ui_572), "4.8");
        card.addView(row3, new LinearLayout.LayoutParams(-1, -2));

        TextWatcher ticketWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { syncPreviewRecord(); }
        };
        etCinema.addTextChangedListener(ticketWatcher);
        etHall.addTextChangedListener(ticketWatcher);
        etSeat.addTextChangedListener(ticketWatcher);
        etVersion.addTextChangedListener(ticketWatcher);
        etPrice.addTextChangedListener(ticketWatcher);
        etTime.addTextChangedListener(ticketWatcher);
        etRating.addTextChangedListener(ticketWatcher);

        col.addView(card, sectionCardMargin());
    }

    private void refreshAllSelectors() {
        // 1. 正面模式
        frontModeRow.removeAllViews();
        for (int i = 0; i < TicketStyle.FRONT_MODE_NAMES().length; i++) {
            final int mode = i;
            boolean sel = (style.frontMode == mode);
            TextView chip = makeChip(TicketStyle.FRONT_MODE_NAMES()[i], sel, null);
            chip.setOnClickListener(v -> {
                style.frontMode = mode;
                previewCard.setShowingBack(false);
                updateFlipButtonText();
                refreshAllSelectors();
                syncPreviewRecord();
            });
            frontModeRow.addView(chip, chipMargin());
        }

        // 2. 容器齿痕/撕裂边框样式
        edgeStyleRow.removeAllViews();
        for (int i = 0; i < TicketStyle.EDGE_STYLE_NAMES().length; i++) {
            final int es = i;
            boolean sel = (style.edgeStyle == es);
            TextView chip = makeChip(TicketStyle.EDGE_STYLE_NAMES()[i], sel, null);
            chip.setOnClickListener(v -> {
                style.edgeStyle = es;
                if (es == TicketStyle.EDGE_CLEAN_TAPE) {
                    style.showNewBadge = true;
                }
                previewCard.setShowingBack(false);
                updateFlipButtonText();
                refreshAllSelectors();
                syncPreviewRecord();
            });
            edgeStyleRow.addView(chip, chipMargin());
        }

        // 3. 便签内部构图
        noteLayoutRow.removeAllViews();
        for (int i = 0; i < TicketStyle.NOTE_LAYOUT_NAMES().length; i++) {
            final int nl = i;
            boolean sel = (style.noteLayout == nl);
            TextView chip = makeChip(TicketStyle.NOTE_LAYOUT_NAMES()[i], sel, null);
            chip.setOnClickListener(v -> {
                style.noteLayout = nl;
                if (nl == TicketStyle.LAYOUT_TOP_TILTED) style.paperTone = TicketStyle.TONE_CHARCOAL;
                else if (nl == TicketStyle.LAYOUT_BANNER_STRIP) style.paperTone = TicketStyle.TONE_OCHRE;
                previewCard.setShowingBack(false);
                updateFlipButtonText();
                refreshAllSelectors();
                syncPreviewRecord();
            });
            noteLayoutRow.addView(chip, chipMargin());
        }

        // 4. 便签纸张底色
        paperToneRow.removeAllViews();
        for (int i = 0; i < TicketStyle.PAPER_TONE_NAMES().length; i++) {
            final int pt = i;
            boolean sel = (style.paperTone == pt);
            TextView chip = makeChip(TicketStyle.PAPER_TONE_NAMES()[i], sel, null);
            chip.setOnClickListener(v -> {
                style.paperTone = pt;
                previewCard.setShowingBack(false);
                updateFlipButtonText();
                refreshAllSelectors();
                syncPreviewRecord();
            });
            paperToneRow.addView(chip, chipMargin());
        }

        // 5. 背面电影院票根模版
        ticketTplRow.removeAllViews();
        for (int i = 0; i < TicketStyle.TICKET_TEMPLATE_NAMES().length; i++) {
            final int tt = i;
            boolean sel = (style.ticketTemplate == tt);
            TextView chip = makeChip(TicketStyle.TICKET_TEMPLATE_NAMES()[i], sel, null);
            chip.setOnClickListener(v -> {
                style.ticketTemplate = tt;
                previewCard.setShowingBack(true);
                updateFlipButtonText();
                refreshAllSelectors();
                syncPreviewRecord();
            });
            ticketTplRow.addView(chip, chipMargin());
        }

        noteOptionsSection.setVisibility(style.frontMode == TicketStyle.FRONT_TEXT_NOTE ? View.VISIBLE : View.GONE);
        refreshNewBadgeToggle();
        refreshScanFilterToggle();
        refreshFontRows();
    }

    private void refreshFontRows() {
        List<FontManager.FontOption> opts = FontManager.listOptions(this);

        titleFontRow.removeAllViews();
        for (FontManager.FontOption opt : opts) {
            boolean sel = opt.id.equals(style.titleFontId);
            TextView chip = makeChip(opt.label, sel, FontManager.resolve(this, opt.id));
            chip.setOnClickListener(v -> {
                style.titleFontId = opt.id;
                refreshFontRows();
                syncPreviewRecord();
            });
            titleFontRow.addView(chip, chipMargin());
        }
        TextView upTitleFont = makeChip(L10n.s(R.string.ui_573), false, AppFonts.regular());
        upTitleFont.setTextColor(0xFFD8C690);
        upTitleFont.setOnClickListener(v -> {
            fontTarget = 0;
            pickFontLauncher.launch(new String[]{"*/*"});
        });
        titleFontRow.addView(upTitleFont, chipMargin());

        bodyFontRow.removeAllViews();
        for (FontManager.FontOption opt : opts) {
            boolean sel = opt.id.equals(style.bodyFontId);
            TextView chip = makeChip(opt.label, sel, FontManager.resolve(this, opt.id));
            chip.setOnClickListener(v -> {
                style.bodyFontId = opt.id;
                refreshFontRows();
                syncPreviewRecord();
            });
            bodyFontRow.addView(chip, chipMargin());
        }
        TextView upBodyFont = makeChip(L10n.s(R.string.ui_573), false, AppFonts.regular());
        upBodyFont.setTextColor(0xFFD8C690);
        upBodyFont.setOnClickListener(v -> {
            fontTarget = 1;
            pickFontLauncher.launch(new String[]{"*/*"});
        });
        bodyFontRow.addView(upBodyFont, chipMargin());
    }

    private void refreshNewBadgeToggle() {
        btnNewBadgeToggle.setText(style.showNewBadge
                ? L10n.s(R.string.ui_574)
                : L10n.s(R.string.ui_547));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Cards.dp(this, 10));
        bg.setColor(style.showNewBadge ? 0xFF3A3622 : 0xFF262827);
        bg.setStroke(Cards.dp(this, 1), style.showNewBadge ? 0xFFC5B438 : 0xFF3D403E);
        btnNewBadgeToggle.setBackground(bg);
    }

    private void refreshScanFilterToggle() {
        btnScanFilterToggle.setText(style.scanEnhance
                ? L10n.s(R.string.ui_575)
                : L10n.s(R.string.ui_576));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Cards.dp(this, 10));
        bg.setColor(style.scanEnhance ? 0xFF332D24 : 0xFF262827);
        bg.setStroke(Cards.dp(this, 1), style.scanEnhance ? 0xFFD8C690 : 0xFF3D403E);
        btnScanFilterToggle.setBackground(bg);
    }

    private void syncPreviewRecord() {
        Record temp = buildRecordObject();
        previewCard.bind(temp, style, previewCard.isShowingBack(), false);
        if (newCoverUri != null) previewCard.setPreviewCoverUri(newCoverUri);
        if (newTicketScanUri != null) previewCard.setPreviewTicketScanUri(newTicketScanUri);
    }

    private Record buildRecordObject() {
        String title = textOf(etTitle);
        String sub = textOf(etSub);
        String note = textOf(etNote);
        String rating = textOf(etRating);
        if (title.isEmpty()) title = L10n.s(R.string.ui_577);
        if (note.isEmpty()) {
            note = L10n.s(R.string.ui_578);
        }
        if (rating.isEmpty()) rating = "4.8";

        String[] fields = new String[]{
                textOf(etCinema).isEmpty() ? L10n.s(R.string.ui_514) : textOf(etCinema),
                textOf(etHall).isEmpty() ? L10n.s(R.string.ui_515) : textOf(etHall),
                textOf(etSeat).isEmpty() ? L10n.s(R.string.ui_516) : textOf(etSeat),
                textOf(etVersion).isEmpty() ? L10n.s(R.string.ui_517) : textOf(etVersion),
                textOf(etPrice).isEmpty() ? "¥55" : textOf(etPrice),
                textOf(etTime).isEmpty() ? "19:40" : textOf(etTime),
                style.toJson()
        };

        Record r = new Record(Record.TICKET, title, sub, dateStr, rating, note, "#2E5E8C", "#A3322B", fields);
        if (existingRecord != null) {
            r.id = existingRecord.id;
            r.createdAt = existingRecord.createdAt;
            r.c1 = existingRecord.c1;
            r.c2 = existingRecord.c2;
        }
        return r;
    }

    private void onSave() {
        if (saving) return;
        saving = true;
        btnSave.setText(L10n.s(R.string.ui_159));
        btnSave.setAlpha(0.6f);

        Record r = buildRecordObject();
        new Repo(this).saveTicketRecord(r, newCoverUri, newTicketScanUri, ok -> runOnUiThread(() -> {
            if (!ok) {
                saving = false;
                btnSave.setText(L10n.s(R.string.ui_536));
                btnSave.setAlpha(1f);
                Toast.makeText(this, L10n.s(R.string.ui_579), Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, L10n.s(R.string.ui_580), Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private LinearLayout createSectionCard(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF222423);
        bg.setCornerRadius(Cards.dp(this, 16));
        bg.setStroke(Cards.dp(this, 1), 0xFF333634);
        card.setBackground(bg);
        int p = Cards.dp(this, 12);
        card.setPadding(p, p, p, p);

        TextView head = Cards.text(this, title, 13, 0xFFF2EFE9,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        head.setPadding(0, 0, 0, Cards.dp(this, 6));
        card.addView(head);
        return card;
    }

    private LinearLayout.LayoutParams sectionCardMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Cards.dp(this, 16);
        return lp;
    }

    private TextView sectionLabel(String s) {
        TextView tv = Cards.text(this, s, 11, 0xFF9E988E, AppFonts.regular());
        tv.setPadding(0, Cards.dp(this, 8), 0, Cards.dp(this, 6));
        return tv;
    }

    private LinearLayout buildHorizontalChipRow(LinearLayout parent) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(row);
        parent.addView(hsv, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView makeChip(String label, boolean selected, Typeface tf) {
        TextView tv = Cards.text(this, label, 12,
                selected ? 0xFF191A19 : 0xFFD5D0C8,
                tf != null ? tf : AppFonts.regular());
        tv.setPadding(Cards.dp(this, 12), Cards.dp(this, 6), Cards.dp(this, 12), Cards.dp(this, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Cards.dp(this, 14));
        bg.setColor(selected ? 0xFFD8C690 : 0xFF2D302E);
        if (!selected) bg.setStroke(Cards.dp(this, 1), 0xFF434745);
        tv.setBackground(bg);
        return tv;
    }

    private LinearLayout.LayoutParams chipMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Cards.dp(this, 8);
        return lp;
    }

    private TextView actionButton(String text, int bgColor, int textColor) {
        TextView tv = Cards.text(this, text, 12, textColor,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Cards.dp(this, 10), Cards.dp(this, 10), Cards.dp(this, 10), Cards.dp(this, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(Cards.dp(this, 10));
        bg.setStroke(Cards.dp(this, 1), 0xFF474B49);
        tv.setBackground(bg);
        return tv;
    }

    private EditText addStyledEdit(LinearLayout parent, String hint, int maxLen, boolean singleLine) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(0xFF78746D);
        et.setTextColor(0xFFF2EFE9);
        et.setTextSize(14);
        et.setTypeface(AppFonts.regular());
        et.setSingleLine(singleLine);
        if (!singleLine) {
            et.setMinLines(4);
            et.setGravity(Gravity.TOP | Gravity.START);
            et.setLineSpacing(0f, 1.35f);
        }
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF181A19);
        bg.setCornerRadius(Cards.dp(this, 10));
        bg.setStroke(Cards.dp(this, 1), 0xFF373A38);
        et.setBackground(bg);
        int p = Cards.dp(this, 12);
        et.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Cards.dp(this, 8);
        parent.addView(et, lp);
        EditorUi.input(et,singleLine);
        return et;
    }

    private EditText addCompactEdit(LinearLayout row, String hint, String defaultVal) {
        EditText et = new EditText(this);
        et.setText(defaultVal);
        et.setHint(hint);
        et.setHintTextColor(0xFF78746D);
        et.setTextColor(0xFFE8E4DC);
        et.setTextSize(12);
        et.setTypeface(AppFonts.regular());
        et.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF181A19);
        bg.setCornerRadius(Cards.dp(this, 8));
        bg.setStroke(Cards.dp(this, 1), 0xFF373A38);
        et.setBackground(bg);
        int p = Cards.dp(this, 9);
        et.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = Cards.dp(this, 6);
        lp.topMargin = Cards.dp(this, 6);
        row.addView(et, lp);
        return et;
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            dateStr = String.format(Locale.US, "%04d.%02d.%02d", y, m + 1, d);
            tvDate.setText(dateStr);
            syncPreviewRecord();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static String textOf(EditText et) {
        return et == null ? "" : et.getText().toString().trim();
    }
}
