package hk.hkucs.dontwannaforget;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 足迹图文纸质画布编辑器(1:1 还原 editexp1.jpg 与 editexp2.png):
 * - 图片四周带一圈可编辑的类纸质背景与 4 套画框模板(随手记 / OOTD / 松弛小记 / PO长文)
 * - 支持在画布任意位置添加、拖动、删除可移动文本框(选中带虚线框与右上角 ×)
 * - 支持上传本地图片、无级缩放(50%~200%)与一键复位
 * - 支持从手机本地上传自定义字体(.ttf/.otf)、调节字号滑块、选择文字颜色
 * - 支持一键「保存图片」到系统相册或「发布笔记」保存到记忆册
 */
public class FootprintEditActivity extends AppCompatActivity {
    private static final int[] TEXT_COLORS = {
            0xFF2B2927, // 墨黑
            0xFFF2EDE2, // 奶油米白(适合叠在深色照片上)
            0xFF8E3B36, // 酒红
            0xFF4A5D4E, // 松石绿
            0xFF3C4A66, // 黛蓝
            0xFF7A5230  // 焦糖棕
    };

    private static class PageModel {
        FootprintCanvasView.State state;
        Uri newUri;
        Media existingMedia;
    }

    private final List<PageModel> pages = new ArrayList<>();
    private int currentPage = 0;

    private String recordId;
    private Record existingRecord;
    private String dateStr;
    private int c1 = 0xFF6B7A6A, c2 = 0xFFC9CDB8;
    private boolean saving = false;
    private boolean suppressTextWatcher = false;

    private EditText etTitle, etSub, etBoxText;
    private TextView tvDate, tvScalePct, tvBoxLabel, tvCharCount, btnPublish;
    private SeekBar sbScale, sbFontSize;
    private FootprintCanvasView canvasView;
    private LinearLayout pageTabsLayout, fontRow, colorRow;

    private Uri pendingCameraUri;
    private ActivityResultLauncher<PickVisualMediaRequest> pickPhoto;
    private ActivityResultLauncher<Uri> takePicture;
    private ActivityResultLauncher<String[]> pickFontFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        recordId = getIntent().getStringExtra("id");
        dateStr = new SimpleDateFormat("yyyy.MM.dd", Locale.US).format(new Date());

        registerLaunchers();
        buildUi();

        if (recordId != null) {
            new Repo(this).get(recordId, r -> runOnUiThread(() -> {
                if (r != null) loadFromRecord(r);
            }));
        } else {
            PageModel p = new PageModel();
            p.state = FootprintCanvasView.State.createDefault(L10n.s(R.string.ui_005), L10n.s(R.string.ui_006), dateStr);
            pages.add(p);
            bindCurrentPage();
        }
    }

    private void registerLaunchers() {
        pickPhoto = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) onPhotoSelected(uri);
        });
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && pendingCameraUri != null) {
                onPhotoSelected(pendingCameraUri);
            }
        });
        pickFontFile = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            String fontId = FontManager.importFont(this, uri);
            if (fontId == null) {
                Toast.makeText(this, L10n.s(R.string.ui_007), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, L10n.s(R.string.ui_008), Toast.LENGTH_SHORT).show();
            FootprintCanvasView.TextBox box = canvasView.getSelectedBox();
            if (box != null) {
                box.fontId = fontId;
                canvasView.invalidate();
                applyBoxStyleToInput(box);
            }
            refreshFontRow();
        });
    }

    private void onPhotoSelected(Uri uri) {
        PageModel pm = pages.get(currentPage);
        pm.newUri = uri;
        canvasView.setPhotoUri(uri, true);
    }

    private void choosePhotoSource() {
        String[] items = {L10n.s(R.string.ui_009), L10n.s(R.string.ui_010)};
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        pickPhoto.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    } else {
                        try {
                            File dir = new File(getCacheDir(), "camera");
                            if (!dir.exists()) dir.mkdirs();
                            File f = new File(dir, "fp_" + System.currentTimeMillis() + ".jpg");
                            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                            takePicture.launch(pendingCameraUri);
                        } catch (Exception e) {
                            Toast.makeText(this, L10n.s(R.string.ui_011), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void loadFromRecord(Record r) {
        existingRecord = r;
        dateStr = r.date;
        c1 = r.c1;
        c2 = r.c2;
        etTitle.setText(r.title);
        etSub.setText(r.sub);
        tvDate.setText(dateStr);

        pages.clear();
        List<Media> medias = MediaIndex.list(r.id, Media.PHOTO);
        int count = Math.max(1, r.fields != null ? r.fields.length : 1);
        for (int i = 0; i < count; i++) {
            PageModel pm = new PageModel();
            String raw = (r.fields != null && i < r.fields.length) ? r.fields[i] : "";
            pm.state = FootprintCanvasView.State.fromField(raw, r, i);
            if (i < medias.size()) pm.existingMedia = medias.get(i);
            pages.add(pm);
        }
        currentPage = 0;
        bindCurrentPage();
    }

    private void bindCurrentPage() {
        if (pages.isEmpty()) return;
        PageModel pm = pages.get(currentPage);
        canvasView.setFallbackColors(c1, c2);
        canvasView.setState(pm.state);
        if (pm.newUri != null) {
            canvasView.setPhotoUri(pm.newUri);
        } else if (pm.existingMedia != null) {
            canvasView.setPhotoMedia(pm.existingMedia);
        } else {
            canvasView.setPhotoBitmapDirect(null);
        }
        updateScaleUi(pm.state.photoScale);
        refreshPageTabs();
        syncEditorWithSelectedBox();
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(true);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        sv.setBackgroundColor(0xFFEDEBE4);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 16);
        col.setPadding(pad, Cards.dp(this, 10), pad, Cards.dp(this, 40));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));

        // 1. 顶部返回与地点/日期栏
        buildTopHeader(col);

        // 2. 多页足迹切换条(支持单页或多页手账)
        HorizontalScrollView pageScroll = new HorizontalScrollView(this);
        pageScroll.setHorizontalScrollBarEnabled(false);
        pageTabsLayout = new LinearLayout(this);
        pageTabsLayout.setOrientation(LinearLayout.HORIZONTAL);
        pageTabsLayout.setGravity(Gravity.CENTER_VERTICAL);
        pageScroll.addView(pageTabsLayout);
        LinearLayout.LayoutParams pslp = new LinearLayout.LayoutParams(-1, -2);
        pslp.bottomMargin = Cards.dp(this, 10);
        col.addView(pageScroll, pslp);

        // 3. 白色纹理纸张画布核心区域 (FootprintCanvasView: 无画框线条，图片可自由拖动与双指缩放)
        canvasView = new FootprintCanvasView(this);
        canvasView.setEditable(true);
        canvasView.setElevation(Cards.dp(this, 3));
        canvasView.setListener(new FootprintCanvasView.Listener() {
            @Override
            public void onBoxSelected(int index, FootprintCanvasView.TextBox box) {
                syncEditorWithSelectedBox();
            }

            @Override
            public void onBoxesChanged() {
                syncEditorWithSelectedBox();
            }

            @Override
            public void onPhotoTransformChanged(float scale) {
                updateScaleUi(scale);
            }
        });
        LinearLayout.LayoutParams cvLp = new LinearLayout.LayoutParams(-1, -2);
        cvLp.bottomMargin = Cards.dp(this, 14);
        col.addView(canvasView, cvLp);

        // 4. 上传图片 + 缩放滑块 + 旋转 + 复位按钮
        buildImageControlCard(col);

        // 5. 文字编辑卡片 (文本 N / +添加文本 / 删除 / 输入框 / 字体与上传字体 / 字号滑块 / 颜色圆圈)
        buildTextEditCard(col);

        // 6. 底部「保存图片」与「发布笔记」双按钮
        buildBottomButtons(col);

        setContentView(sv);
        ViewCompat.setOnApplyWindowInsetsListener(sv, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(sv);
    }

    private void buildTopHeader(LinearLayout col) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, Cards.dp(this, 4), 0, Cards.dp(this, 10));

        // Draw the arrow independently of font metrics, which can clip a text chevron.
        android.widget.ImageView back=new android.widget.ImageView(this);
        back.setImageDrawable(new AgentLineIcon("back",0xFFFAF8F3));
        back.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        back.setPadding(Cards.dp(this,9),Cards.dp(this,9),Cards.dp(this,9),Cards.dp(this,9));
        back.setContentDescription(L10n.s(R.string.ui_060));back.setFocusable(true);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(0x6623211E);
        back.setBackground(backBg);
        int bs = Cards.dp(this, 40);
        back.setOnClickListener(v -> finish());
        HomeNavigation.bind(back);
        top.addView(back, new LinearLayout.LayoutParams(bs, bs));

        // 中间地点与副标题轻量化输入
        LinearLayout metaCol = new LinearLayout(this);
        metaCol.setOrientation(LinearLayout.VERTICAL);
        metaCol.setPadding(Cards.dp(this, 12), 0, Cards.dp(this, 8), 0);

        etTitle = new EditText(this);
        etTitle.setHint(L10n.s(R.string.ui_012));
        etTitle.setHintTextColor(0xFF8C867B);
        etTitle.setTextColor(getColor(R.color.ink));
        etTitle.setTextSize(15);
        etTitle.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        etTitle.setSingleLine(true);
        etTitle.setIncludeFontPadding(false);
        etTitle.setBackground(null);
        etTitle.setPadding(0, 0, 0, Cards.dp(this, 2));
        metaCol.addView(etTitle);

        etSub = new EditText(this);
        etSub.setHint(L10n.s(R.string.ui_013));
        etSub.setHintTextColor(0xFFA09A8E);
        etSub.setTextColor(getColor(R.color.ink_sub));
        etSub.setTextSize(12);
        etSub.setTypeface(AppFonts.regular());
        etSub.setSingleLine(true);
        etSub.setIncludeFontPadding(false);
        etSub.setBackground(null);
        etSub.setPadding(0, 0, 0, 0);
        metaCol.addView(etSub);

        top.addView(metaCol, new LinearLayout.LayoutParams(0, -2, 1f));

        tvDate = Cards.text(this, dateStr, 12, getColor(R.color.wine), AppFonts.regular());
        tvDate.setIncludeFontPadding(false);tvDate.setGravity(Gravity.CENTER);
        tvDate.setPadding(Cards.dp(this, 10), Cards.dp(this, 6), Cards.dp(this, 10), Cards.dp(this, 6));
        GradientDrawable dateBg = new GradientDrawable();
        dateBg.setColor(0xFFFAF8F3);
        dateBg.setCornerRadius(Cards.dp(this, 14));
        dateBg.setStroke(Cards.dp(this, 1), 0xFFD8D2C5);
        tvDate.setBackground(dateBg);
        tvDate.setOnClickListener(v -> pickDate());
        top.addView(tvDate);

        col.addView(top);
        FloatingHeader.pin(top);
    }

    private void refreshPageTabs() {
        pageTabsLayout.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            boolean sel = (i == currentPage);
            TextView tab = Cards.text(this, L10n.s(R.string.ui_014) + (i + 1), 12,
                    sel ? Color.WHITE : getColor(R.color.ink), AppFonts.regular());
            tab.setPadding(Cards.dp(this, 12), Cards.dp(this, 5), Cards.dp(this, 12), Cards.dp(this, 5));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Cards.dp(this, 14));
            bg.setColor(sel ? 0xFF2B2927 : 0xFFFAF8F3);
            if (!sel) bg.setStroke(Cards.dp(this, 1), 0xFFD5CFC3);
            tab.setBackground(bg);
            tab.setOnClickListener(v -> {
                currentPage = idx;
                bindCurrentPage();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Cards.dp(this, 8);
            pageTabsLayout.addView(tab, lp);
        }

        if (pages.size() < 9) {
            TextView addPage = Cards.text(this, L10n.s(R.string.ui_015), 12, getColor(R.color.wine), AppFonts.regular());
            addPage.setPadding(Cards.dp(this, 12), Cards.dp(this, 5), Cards.dp(this, 12), Cards.dp(this, 5));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Cards.dp(this, 14));
            bg.setColor(0xFFFAF8F3);
            bg.setStroke(Cards.dp(this, 1), 0xFFD5CFC3);
            addPage.setBackground(bg);
            addPage.setOnClickListener(v -> {
                PageModel pm = new PageModel();
                pm.state = FootprintCanvasView.State.createDefault(
                        textOf(etTitle).isEmpty() ? L10n.s(R.string.ui_005) : textOf(etTitle),
                        L10n.s(R.string.ui_016), dateStr);
                pages.add(pm);
                currentPage = pages.size() - 1;
                bindCurrentPage();
            });
            pageTabsLayout.addView(addPage);
        }

        if (pages.size() > 1) {
            TextView delPage = Cards.text(this, L10n.s(R.string.ui_017), 11, 0xFFB84A39, AppFonts.regular());
            delPage.setPadding(Cards.dp(this, 10), Cards.dp(this, 5), Cards.dp(this, 6), Cards.dp(this, 5));
            delPage.setOnClickListener(v -> {
                pages.remove(currentPage);
                currentPage = Math.max(0, currentPage - 1);
                bindCurrentPage();
            });
            pageTabsLayout.addView(delPage);
        }
    }

    private void buildImageControlCard(LinearLayout col) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFAF8F5);
        bg.setCornerRadius(Cards.dp(this, 16));
        bar.setBackground(bg);
        int p = Cards.dp(this, 12);
        bar.setPadding(p, Cards.dp(this, 10), p, Cards.dp(this, 10));

        // 左侧「上传图片」深色按钮
        TextView btnUpload = Cards.text(this, L10n.s(R.string.ui_018), 13, Color.WHITE,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        btnUpload.setPadding(Cards.dp(this, 14), Cards.dp(this, 9), Cards.dp(this, 14), Cards.dp(this, 9));
        GradientDrawable ubg = new GradientDrawable();
        ubg.setColor(0xFF262626);
        ubg.setCornerRadius(Cards.dp(this, 10));
        btnUpload.setBackground(ubg);
        btnUpload.setOnClickListener(v -> choosePhotoSource());
        bar.addView(btnUpload);

        // 中间「缩\n放」+ SeekBar + 百分比
        TextView lblZoom = Cards.text(this, L10n.s(R.string.ui_019), 11, 0xFF55524D, AppFonts.regular());
        lblZoom.setPadding(Cards.dp(this, 10), 0, Cards.dp(this, 4), 0);
        bar.addView(lblZoom);

        sbScale = new SeekBar(this);
        sbScale.setMax(160); // 0..160 对应 40%..200%
        sbScale.setProgress(60);
        sbScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float scale = (40 + progress) / 100f;
                canvasView.getState().photoScale = scale;
                tvScalePct.setText((40 + progress) + "%");
                canvasView.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        bar.addView(sbScale, new LinearLayout.LayoutParams(0, -2, 1f));

        tvScalePct = Cards.text(this, "100%", 12, 0xFF6E6A63, AppFonts.regular());
        tvScalePct.setPadding(Cards.dp(this, 4), 0, Cards.dp(this, 8), 0);
        bar.addView(tvScalePct);

        // 「旋转」按钮(90° 顺时针旋转图片)
        TextView btnRotate = Cards.text(this, L10n.s(R.string.ui_020), 12, 0xFF3A3733, AppFonts.regular());
        btnRotate.setPadding(Cards.dp(this, 9), Cards.dp(this, 7), Cards.dp(this, 9), Cards.dp(this, 7));
        GradientDrawable rotBg = new GradientDrawable();
        rotBg.setColor(Color.WHITE);
        rotBg.setCornerRadius(Cards.dp(this, 8));
        rotBg.setStroke(Cards.dp(this, 1), 0xFFD8D4CC);
        btnRotate.setBackground(rotBg);
        btnRotate.setOnClickListener(v -> {
            FootprintCanvasView.State st = canvasView.getState();
            st.photoRotation = (st.photoRotation + 90) % 360;
            canvasView.invalidate();
        });
        LinearLayout.LayoutParams rotLp = new LinearLayout.LayoutParams(-2, -2);
        rotLp.rightMargin = Cards.dp(this, 6);
        bar.addView(btnRotate, rotLp);

        // 右侧「复位」按钮
        TextView btnReset = Cards.text(this, L10n.s(R.string.ui_021), 12, 0xFF3B82F6, AppFonts.regular());
        btnReset.setPadding(Cards.dp(this, 11), Cards.dp(this, 7), Cards.dp(this, 11), Cards.dp(this, 7));
        GradientDrawable rbg = new GradientDrawable();
        rbg.setColor(Color.WHITE);
        rbg.setCornerRadius(Cards.dp(this, 8));
        rbg.setStroke(Cards.dp(this, 1), 0xFFD8D4CC);
        btnReset.setBackground(rbg);
        btnReset.setOnClickListener(v -> {
            FootprintCanvasView.State st = canvasView.getState();
            st.photoScale = 1.0f;
            st.photoPanX = -0.08f;
            st.photoPanY = -0.15f;
            st.photoRotation = 0;
            updateScaleUi(1.0f);
            canvasView.invalidate();
        });
        bar.addView(btnReset);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Cards.dp(this, 14);
        col.addView(bar, lp);
    }

    private void updateScaleUi(float scale) {
        int pct = Math.round(scale * 100f);
        pct = Math.max(40, Math.min(200, pct));
        sbScale.setProgress(pct - 40);
        tvScalePct.setText(pct + "%");
    }

    private void buildTextEditCard(LinearLayout col) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFAF8F5);
        bg.setCornerRadius(Cards.dp(this, 18));
        card.setBackground(bg);
        int p = Cards.dp(this, 16);
        card.setPadding(p, p, p, p);

        // 顶部行: 左侧「文字编辑 / 文本 1(点击可切换)」, 右侧「+ 添加文本」「删除」
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.addView(Cards.text(this, L10n.s(R.string.ui_022), 11, 0xFF7A756C, AppFonts.regular()));
        tvBoxLabel = Cards.text(this, L10n.s(R.string.ui_023), 14, 0xFF1F1E1C,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        tvBoxLabel.setPadding(0, Cards.dp(this, 2), 0, 0);
        tvBoxLabel.setOnClickListener(v -> {
            int total = canvasView.getState().boxes.size();
            if (total > 1) {
                canvasView.selectBox((canvasView.getSelectedIndex() + 1) % total);
            }
        });
        leftCol.addView(tvBoxLabel);
        head.addView(leftCol, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView btnAddText = Cards.text(this, L10n.s(R.string.ui_024), 13, 0xFF2563EB, AppFonts.regular());
        btnAddText.setPadding(Cards.dp(this, 12), Cards.dp(this, 7), Cards.dp(this, 12), Cards.dp(this, 7));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(Color.WHITE);
        addBg.setCornerRadius(Cards.dp(this, 8));
        addBg.setStroke(Cards.dp(this, 1), 0xFFD8D4CC);
        btnAddText.setBackground(addBg);
        btnAddText.setOnClickListener(v -> addNewTextBox());
        head.addView(btnAddText);

        TextView btnDelText = Cards.text(this, L10n.s(R.string.ui_025), 13, 0xFFB84A39, AppFonts.regular());
        btnDelText.setPadding(Cards.dp(this, 12), Cards.dp(this, 7), Cards.dp(this, 12), Cards.dp(this, 7));
        GradientDrawable delBg = new GradientDrawable();
        delBg.setColor(Color.WHITE);
        delBg.setCornerRadius(Cards.dp(this, 8));
        delBg.setStroke(Cards.dp(this, 1), 0xFFD8D4CC);
        btnDelText.setBackground(delBg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = Cards.dp(this, 8);
        btnDelText.setOnClickListener(v -> deleteSelectedTextBox());
        head.addView(btnDelText, dlp);

        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // 文本输入框 (米色圆角背景, 还原 editexp2.png)
        etBoxText = new EditText(this);
        etBoxText.setHint(L10n.s(R.string.ui_026));
        etBoxText.setHintTextColor(0xFF9E988E);
        etBoxText.setTextColor(0xFF2B2927);
        etBoxText.setTextSize(16);
        etBoxText.setMinLines(3);
        etBoxText.setGravity(Gravity.TOP | Gravity.START);
        etBoxText.setLineSpacing(0f, 1.35f);
        etBoxText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(180)});
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFFF2EFE9);
        etBg.setCornerRadius(Cards.dp(this, 12));
        etBg.setStroke(Cards.dp(this, 1), 0xFFDCD6CA);
        etBoxText.setBackground(etBg);
        int ep = Cards.dp(this, 14);
        etBoxText.setPadding(ep, ep, ep, ep);
        EditorUi.input(etBoxText,false);
        etBoxText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String str = s.toString();
                tvCharCount.setText(str.length() + " / 180");
                if (suppressTextWatcher) return;
                FootprintCanvasView.TextBox box = canvasView.getSelectedBox();
                if (box != null) {
                    box.text = str;
                    canvasView.invalidate();
                }
            }
        });
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(-1, -2);
        etLp.topMargin = Cards.dp(this, 12);
        card.addView(etBoxText, etLp);

        tvCharCount = Cards.text(this, "0 / 180", 12, 0xFF8C867C, AppFonts.regular());
        tvCharCount.setPadding(Cards.dp(this, 4), Cards.dp(this, 8), 0, Cards.dp(this, 10));
        card.addView(tvCharCount);

        // 分割细线
        View div1 = new View(this);
        div1.setBackgroundColor(0xFFEAE5DC);
        card.addView(div1, new LinearLayout.LayoutParams(-1, Cards.dp(this, 1)));

        // 字体选择与「+ 上传字体」行
        LinearLayout fontBar = new LinearLayout(this);
        fontBar.setOrientation(LinearLayout.HORIZONTAL);
        fontBar.setGravity(Gravity.CENTER_VERTICAL);
        fontBar.setPadding(0, Cards.dp(this, 12), 0, Cards.dp(this, 10));
        fontBar.addView(Cards.text(this, L10n.s(R.string.ui_027), 13, 0xFF55524D, AppFonts.regular()));

        HorizontalScrollView fontScroll = new HorizontalScrollView(this);
        fontScroll.setHorizontalScrollBarEnabled(false);
        fontRow = new LinearLayout(this);
        fontRow.setOrientation(LinearLayout.HORIZONTAL);
        fontRow.setPadding(Cards.dp(this, 10), 0, 0, 0);
        fontScroll.addView(fontRow);
        fontBar.addView(fontScroll, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(fontBar);

        // 字号滑块 + 颜色圆圈选择行 (还原 editexp2.png 底部控制栏)
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        styleRow.setGravity(Gravity.CENTER_VERTICAL);
        styleRow.setPadding(0, Cards.dp(this, 4), 0, 0);

        styleRow.addView(Cards.text(this, L10n.s(R.string.ui_028), 13, 0xFF55524D, AppFonts.regular()));

        sbFontSize = new SeekBar(this);
        sbFontSize.setMax(20); // 9sp .. 29sp
        sbFontSize.setProgress(4);
        sbFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                FootprintCanvasView.TextBox box = canvasView.getSelectedBox();
                if (box != null) {
                    box.sizeSp = 9f + progress;
                    canvasView.invalidate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        styleRow.addView(sbFontSize, new LinearLayout.LayoutParams(0, -2, 1f));

        colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        styleRow.addView(colorRow);

        card.addView(styleRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = Cards.dp(this, 16);
        col.addView(card, cardLp);
    }

    private void refreshFontRow() {
        fontRow.removeAllViews();
        FootprintCanvasView.TextBox cur = canvasView.getSelectedBox();
        String curFont = cur != null ? cur.fontId : FontManager.FONT_SKETCH;

        List<FontManager.FontOption> opts = FontManager.listOptions(this);
        for (FontManager.FontOption opt : opts) {
            boolean sel = opt.id.equals(curFont);
            TextView chip = Cards.text(this, opt.label, 12,
                    sel ? Color.WHITE : 0xFF3A3733, FontManager.resolve(this, opt.id));
            chip.setPadding(Cards.dp(this, 11), Cards.dp(this, 5), Cards.dp(this, 11), Cards.dp(this, 5));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Cards.dp(this, 14));
            bg.setColor(sel ? 0xFF2B2927 : 0xFFF2EFE9);
            if (!sel) bg.setStroke(Cards.dp(this, 1), 0xFFD8D2C5);
            chip.setBackground(bg);
            chip.setOnClickListener(v -> {
                FootprintCanvasView.TextBox box = canvasView.getSelectedBox();
                if (box != null) {
                    box.fontId = opt.id;
                    canvasView.invalidate();
                    applyBoxStyleToInput(box);
                    refreshFontRow();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Cards.dp(this, 8);
            fontRow.addView(chip, lp);
        }

        // 「+ 上传字体」按钮
        TextView uploadBtn = Cards.text(this, L10n.s(R.string.ui_029), 12, 0xFF2563EB, AppFonts.regular());
        uploadBtn.setPadding(Cards.dp(this, 11), Cards.dp(this, 5), Cards.dp(this, 11), Cards.dp(this, 5));
        GradientDrawable ubg = new GradientDrawable();
        ubg.setCornerRadius(Cards.dp(this, 14));
        ubg.setColor(Color.WHITE);
        ubg.setStroke(Cards.dp(this, 1), 0xFF93C5FD);
        uploadBtn.setBackground(ubg);
        uploadBtn.setOnClickListener(v -> pickFontFile.launch(new String[]{"*/*"}));
        fontRow.addView(uploadBtn);
    }

    private void refreshColorRow() {
        colorRow.removeAllViews();
        FootprintCanvasView.TextBox cur = canvasView.getSelectedBox();
        int curColor = cur != null ? cur.color : TEXT_COLORS[0];

        int outerSize = Cards.dp(this, 28);
        for (int color : TEXT_COLORS) {
            final int c = color;
            boolean sel = (c == curColor);
            View swatch = new View(this);

            GradientDrawable inner = new GradientDrawable();
            inner.setShape(GradientDrawable.OVAL);
            inner.setColor(c);
            inner.setStroke(Cards.dp(this, 1), 0x33000000);

            if (sel) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(Color.TRANSPARENT);
                ring.setStroke(Cards.dp(this, 2), 0xFF2B2927);
                LayerDrawable ld = new LayerDrawable(new GradientDrawable[]{ring, inner});
                int inset = Cards.dp(this, 4);
                ld.setLayerInset(1, inset, inset, inset, inset);
                swatch.setBackground(ld);
            } else {
                swatch.setBackground(inner);
            }

            swatch.setOnClickListener(v -> {
                FootprintCanvasView.TextBox box = canvasView.getSelectedBox();
                if (box != null) {
                    box.color = c;
                    canvasView.invalidate();
                    refreshColorRow();
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(outerSize, outerSize);
            lp.leftMargin = Cards.dp(this, 6);
            colorRow.addView(swatch, lp);
        }
    }

    private void syncEditorWithSelectedBox() {
        int idx = canvasView.getSelectedIndex();
        FootprintCanvasView.TextBox box = canvasView.getSelectedBox();
        int total = canvasView.getState().boxes.size();
        tvBoxLabel.setText(L10n.s(R.string.ui_030) + (idx + 1) + (total > 1 ? " / " + total + " ▾" : ""));

        suppressTextWatcher = true;
        if (box != null) {
            etBoxText.setText(box.text);
            etBoxText.setSelection(etBoxText.getText().length());
            applyBoxStyleToInput(box);
            int p = Math.round(box.sizeSp - 9f);
            sbFontSize.setProgress(Math.max(0, Math.min(20, p)));
        } else {
            etBoxText.setText("");
        }
        suppressTextWatcher = false;

        refreshFontRow();
        refreshColorRow();
    }

    private void applyBoxStyleToInput(FootprintCanvasView.TextBox box) {
        if (box == null) return;
        etBoxText.setTypeface(FontManager.resolve(this, box.fontId));
        etBoxText.setLetterSpacing(FontManager.FONT_SKETCH.equals(box.fontId) ? 0.12f : 0.03f);
    }

    private void addNewTextBox() {
        List<FootprintCanvasView.TextBox> list = canvasView.getState().boxes;
        if (list.size() >= 10) {
            Toast.makeText(this, L10n.s(R.string.ui_031), Toast.LENGTH_SHORT).show();
            return;
        }
        float nx = 0.12f + (list.size() % 3) * 0.08f;
        float ny = 0.14f + (list.size() % 4) * 0.16f;
        FootprintCanvasView.TextBox b = new FootprintCanvasView.TextBox(
                "NEW MEMORY NOTE", nx, ny, 13f, TEXT_COLORS[0], FontManager.FONT_SKETCH);
        list.add(b);
        canvasView.selectBox(list.size() - 1);
    }

    private void deleteSelectedTextBox() {
        List<FootprintCanvasView.TextBox> list = canvasView.getState().boxes;
        int idx = canvasView.getSelectedIndex();
        if (idx >= 0 && idx < list.size()) {
            if (list.size() > 1) {
                list.remove(idx);
                canvasView.selectBox(Math.max(0, idx - 1));
            } else {
                list.get(0).text = "";
                canvasView.invalidate();
                syncEditorWithSelectedBox();
            }
        }
    }

    private void buildBottomButtons(LinearLayout col) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // 左侧「保存图片」白底黑框圆角按钮
        TextView btnExport = Cards.text(this, L10n.s(R.string.ui_032), 15, 0xFF1F1E1C,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        btnExport.setGravity(Gravity.CENTER);
        btnExport.setPadding(0, Cards.dp(this, 14), 0, Cards.dp(this, 14));
        GradientDrawable ebg = new GradientDrawable();
        ebg.setColor(0xFFFAF8F5);
        ebg.setCornerRadius(Cards.dp(this, 14));
        ebg.setStroke(Cards.dp(this, 1), 0xFF2B2927);
        btnExport.setBackground(ebg);
        EditorUi.action(btnExport);
        btnExport.setOnClickListener(v -> saveCanvasToSystemGallery());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, -2, 1f);
        elp.rightMargin = Cards.dp(this, 12);
        row.addView(btnExport, elp);

        // 右侧「发布笔记」深色实心圆角按钮
        btnPublish = Cards.text(this, L10n.s(R.string.ui_033), 15, Color.WHITE,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        btnPublish.setGravity(Gravity.CENTER);
        btnPublish.setPadding(0, Cards.dp(this, 14), 0, Cards.dp(this, 14));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xFF23211E);
        pbg.setCornerRadius(Cards.dp(this, 14));
        btnPublish.setBackground(pbg);
        EditorUi.action(btnPublish);
        btnPublish.setOnClickListener(v -> onPublish());
        row.addView(btnPublish, new LinearLayout.LayoutParams(0, -2, 1f));

        col.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void saveCanvasToSystemGallery() {
        try {
            Bitmap bm = canvasView.renderToBitmap(1200);
            String fileName = "Footprint_" + System.currentTimeMillis() + ".png";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DontWannaForget");
            }
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    bm.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                Toast.makeText(this, L10n.s(R.string.ui_034), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, L10n.s(R.string.ui_035) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onPublish() {
        if (saving) return;
        String title = textOf(etTitle);
        String sub = textOf(etSub);

        StringBuilder summary = new StringBuilder();
        String[] fields = new String[pages.size()];
        List<Uri> newUris = new ArrayList<>();
        List<Media> existingMedias = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            PageModel pm = pages.get(i);
            fields[i] = pm.state.toJson();
            newUris.add(pm.newUri);
            existingMedias.add(pm.existingMedia);
            for (FootprintCanvasView.TextBox b : pm.state.boxes) {
                if (b.text != null && !b.text.trim().isEmpty()) {
                    if (summary.length() == 0) summary.append(b.text.trim().replace("\n", " "));
                }
            }
        }

        if (title.isEmpty()) {
            title = summary.length() > 0
                    ? summary.substring(0, Math.min(12, summary.length()))
                    : L10n.s(R.string.ui_036);
        }
        if (sub.isEmpty()) sub = L10n.s(R.string.ui_037);
        String note = summary.length() > 0 ? summary.toString() : title;

        saving = true;
        btnPublish.setText(L10n.s(R.string.ui_038));
        btnPublish.setAlpha(0.6f);

        Record r = new Record(Record.FOOTPRINT, title, sub, dateStr, null, note,
                String.format("#%06X", (0xFFFFFF & c1)),
                String.format("#%06X", (0xFFFFFF & c2)),
                fields);
        if (existingRecord != null) {
            r.id = existingRecord.id;
            r.createdAt = existingRecord.createdAt;
        }

        new Repo(this).saveFootprintRecord(r, newUris, existingMedias, ok -> runOnUiThread(() -> {
            if (!ok) {
                saving = false;
                btnPublish.setText(L10n.s(R.string.ui_033));
                btnPublish.setAlpha(1f);
                Toast.makeText(this, L10n.s(R.string.ui_039), Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, L10n.s(R.string.ui_040), Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            dateStr = String.format(Locale.US, "%04d.%02d.%02d", y, m + 1, d);
            tvDate.setText(dateStr);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static String textOf(EditText et) {
        return et == null ? "" : et.getText().toString().trim();
    }
}
