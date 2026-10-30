package hk.hkucs.dontwannaforget;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 记忆编辑器(步骤 3):支持 生活 / 书 / 音乐 / 足迹 四类新建。
 * 支持相册选图与相机拍照,先复制原图进 App 私有目录再提交记录。
 */
public class MemoryEditActivity extends AppCompatActivity {
    private static final String[][] PALETTES = {
            {"#8E4F4B", "#E7A35A"},
            {"#3C4A66", "#E58F6B"},
            {"#5A7D8C", "#E6DCC8"},
            {"#6B7A6A", "#C9CDB8"},
            {"#3E4A45", "#C9C2AE"}
    };

    private int type = Record.BOOK;
    private String dateStr;
    private int paletteIdx = 0;
    private boolean saving = false;

    private Uri coverUri;
    private final List<Uri> photoUris = new ArrayList<>();
    private Uri pendingCameraUri;
    private int pickTarget = 0; // 0=photo, 1=cover, >=100 足迹条目索引+100

    private EditText etTitle, etSub, etNote;
    private TextView tvDate, btnSave;
    private LinearLayout photoStrip, fpContainer;
    private ImageView ivCoverPreview;
    private TextView tvCoverHint;

    private static class FpItem {
        EditText etTime, etText;
        Uri uri;
        ImageView preview;
        TextView hint;
    }
    private final List<FpItem> fpItems = new ArrayList<>();

    private ActivityResultLauncher<PickVisualMediaRequest> pickSingle;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultiple;
    private ActivityResultLauncher<Uri> takePicture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        type = getIntent().getIntExtra("type", Record.BOOK);
        paletteIdx = Math.abs(type) % PALETTES.length;
        dateStr = new SimpleDateFormat("yyyy.MM.dd", Locale.US).format(new Date());

        registerLaunchers();
        buildUi();
    }

    private void registerLaunchers() {
        pickSingle = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) onUriPicked(uri);
        });
        pickMultiple = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(9), uris -> {
            if (uris == null || uris.isEmpty()) return;
            for (Uri u : uris) {
                if (photoUris.size() < 9) photoUris.add(u);
            }
            refreshPhotoStrip();
        });
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && pendingCameraUri != null) {
                onUriPicked(pendingCameraUri);
            }
        });
    }

    private void onUriPicked(Uri uri) {
        if (pickTarget == 1) {
            coverUri = uri;
            if (ivCoverPreview != null) {
                ivCoverPreview.setImageURI(uri);
                ivCoverPreview.setVisibility(View.VISIBLE);
            }
            if (tvCoverHint != null) tvCoverHint.setText(L10n.s(R.string.ui_126));
        } else if (pickTarget >= 100) {
            int idx = pickTarget - 100;
            if (idx >= 0 && idx < fpItems.size()) {
                FpItem item = fpItems.get(idx);
                item.uri = uri;
                item.preview.setImageURI(uri);
                item.preview.setVisibility(View.VISIBLE);
                item.hint.setText(L10n.s(R.string.ui_127));
            }
        } else {
            if (photoUris.size() >= 9) {
                Toast.makeText(this, L10n.s(R.string.ui_128), Toast.LENGTH_SHORT).show();
                return;
            }
            photoUris.add(uri);
            refreshPhotoStrip();
        }
    }

    private void chooseSource(int target, boolean allowMulti) {
        pickTarget = target;
        String[] items = {L10n.s(R.string.ui_129), L10n.s(R.string.ui_010)};
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        PickVisualMediaRequest req = new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build();
                        if (allowMulti && photoUris.size() < 8) {
                            pickMultiple.launch(req);
                        } else {
                            pickSingle.launch(req);
                        }
                    } else {
                        launchCamera();
                    }
                })
                .show();
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "shot_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            takePicture.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, L10n.s(R.string.ui_130), Toast.LENGTH_SHORT).show();
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.desk));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(true);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 24);
        col.setPadding(pad, Cards.dp(this, 16), pad, Cards.dp(this, 160));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        // 顶部栏
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, 0, Cards.dp(this, 16));
        TextView cancel = Cards.text(this, L10n.s(R.string.ui_131), 15, getColor(R.color.ink_sub), AppFonts.regular());
        cancel.setOnClickListener(v -> finish());
        HomeNavigation.bind(cancel);
        top.addView(cancel);

        String typeTitle = type == Record.BOOK ? L10n.s(R.string.ui_132)
                : type == Record.MUSIC ? L10n.s(R.string.ui_133) : L10n.s(R.string.ui_134);
        TextView head = Cards.text(this, typeTitle, 18, getColor(R.color.ink),
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        head.setGravity(Gravity.CENTER);
        top.addView(head, new LinearLayout.LayoutParams(0, -2, 1f));

        tvDate = Cards.text(this, dateStr, 13, getColor(R.color.wine), AppFonts.regular());
        tvDate.setOnClickListener(v -> pickDate());
        top.addView(tvDate);
        col.addView(top);
        FloatingHeader.pin(top);

        // 按类型构建表单
        if (type == Record.BOOK || type == Record.MUSIC) {
            buildCoverBox(col, type == Record.BOOK ? L10n.s(R.string.ui_135) : L10n.s(R.string.ui_136));
        }

        if (type == Record.BOOK) {
            etTitle = addInput(col, L10n.s(R.string.ui_137), 80, true);
            etSub = addInput(col, L10n.s(R.string.ui_138), 80, true);
            etNote = addInput(col, L10n.s(R.string.ui_139), 5000, false);
        } else if (type == Record.MUSIC) {
            etTitle = addInput(col, L10n.s(R.string.ui_140), 80, true);
            etSub = addInput(col, L10n.s(R.string.ui_141), 80, true);
            etNote = addInput(col, L10n.s(R.string.ui_142), 5000, false);
        } else if (type == Record.FOOTPRINT) {
            etTitle = addInput(col, L10n.s(R.string.ui_143), 80, true);
            etSub = addInput(col, L10n.s(R.string.ui_144), 100, true);
            fpContainer = new LinearLayout(this);
            fpContainer.setOrientation(LinearLayout.VERTICAL);
            col.addView(fpContainer);
            addFootprintEntry();

            TextView addStep = Cards.text(this, L10n.s(R.string.ui_145), 14, getColor(R.color.wine), AppFonts.regular());
            addStep.setPadding(0, Cards.dp(this, 12), 0, Cards.dp(this, 8));
            addStep.setOnClickListener(v -> {
                if (fpItems.size() >= 9) {
                    Toast.makeText(this, L10n.s(R.string.ui_146), Toast.LENGTH_SHORT).show();
                    return;
                }
                addFootprintEntry();
            });
            col.addView(addStep);
        }

        // 底部固定保存栏
        FrameLayout bottom = new FrameLayout(this);
        bottom.setPadding(pad, Cards.dp(this, 12), pad, Cards.dp(this, 16));
        bottom.setBackgroundColor(getColor(R.color.desk));
        btnSave = Cards.text(this, L10n.s(R.string.ui_147), 16, Color.WHITE,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        btnSave.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.wine));
        bg.setCornerRadius(Cards.dp(this, 24));
        btnSave.setBackground(bg);
        btnSave.setPadding(0, Cards.dp(this, 14), 0, Cards.dp(this, 14));
        btnSave.setOnClickListener(v -> onSave());
        bottom.addView(btnSave, new FrameLayout.LayoutParams(-1, -2));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
    }

    private void buildCoverBox(LinearLayout col, String hint) {
        FrameLayout box = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(getColor(R.color.paper));
        g.setCornerRadius(Cards.dp(this, 8));
        g.setStroke(Cards.dp(this, 1), 0xFFD5CFC4);
        box.setBackground(g);

        ivCoverPreview = new ImageView(this);
        ivCoverPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivCoverPreview.setVisibility(View.GONE);
        box.addView(ivCoverPreview, new FrameLayout.LayoutParams(-1, -1));

        tvCoverHint = Cards.text(this, hint, 13, getColor(R.color.wine), AppFonts.regular());
        tvCoverHint.setGravity(Gravity.CENTER);
        tvCoverHint.setPadding(Cards.dp(this, 16), 0, Cards.dp(this, 16), 0);
        box.addView(tvCoverHint, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        box.setOnClickListener(v -> chooseSource(1, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Cards.dp(this, 180));
        lp.bottomMargin = Cards.dp(this, 16);
        col.addView(box, lp);
    }

    private void buildPhotoSection(LinearLayout col) {
        TextView label = Cards.text(this, L10n.s(R.string.ui_148), 12,
                getColor(R.color.ink_sub), AppFonts.regular());
        label.setPadding(0, 0, 0, Cards.dp(this, 8));
        col.addView(label);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        photoStrip = new LinearLayout(this);
        photoStrip.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(photoStrip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Cards.dp(this, 16);
        col.addView(hsv, lp);
        refreshPhotoStrip();
    }

    private void refreshPhotoStrip() {
        if (photoStrip == null) return;
        photoStrip.removeAllViews();
        int size = Cards.dp(this, 92);
        for (int i = 0; i < photoUris.size(); i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageURI(photoUris.get(i));
            cell.addView(iv, new FrameLayout.LayoutParams(-1, -1));

            TextView del = Cards.text(this, "✕", 11, Color.WHITE, AppFonts.regular());
            del.setBackgroundColor(0x99000000);
            del.setPadding(Cards.dp(this, 6), Cards.dp(this, 2), Cards.dp(this, 6), Cards.dp(this, 2));
            del.setOnClickListener(v -> {
                photoUris.remove(idx);
                refreshPhotoStrip();
            });
            cell.addView(del, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(size, size);
            clp.rightMargin = Cards.dp(this, 10);
            photoStrip.addView(cell, clp);
        }
        if (photoUris.size() < 9) {
            TextView add = Cards.text(this, L10n.s(R.string.ui_149) + photoUris.size() + "/9)", 12,
                    getColor(R.color.wine), AppFonts.regular());
            add.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(getColor(R.color.paper));
            g.setCornerRadius(Cards.dp(this, 6));
            g.setStroke(Cards.dp(this, 1), 0xFFD5CFC4);
            add.setBackground(g);
            add.setOnClickListener(v -> chooseSource(0, true));
            photoStrip.addView(add, new LinearLayout.LayoutParams(size, size));
        }
    }

    private void addFootprintEntry() {
        final int idx = fpItems.size();
        FpItem item = new FpItem();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(getColor(R.color.paper));
        g.setCornerRadius(Cards.dp(this, 8));
        g.setStroke(Cards.dp(this, 1), 0xFFE2DDD3);
        card.setBackground(g);
        int p = Cards.dp(this, 14);
        card.setPadding(p, p, p, p);

        String nowHm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        item.etTime = new EditText(this);
        item.etTime.setText(nowHm);
        item.etTime.setHint(L10n.s(R.string.ui_150));
        item.etTime.setTextSize(14);
        item.etTime.setTypeface(AppFonts.regular());
        item.etTime.setTextColor(getColor(R.color.ink));
        item.etTime.setBackground(null);
        card.addView(item.etTime);

        FrameLayout pbox = new FrameLayout(this);
        pbox.setBackgroundColor(0xFFEDE8DF);
        item.preview = new ImageView(this);
        item.preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        item.preview.setVisibility(View.GONE);
        pbox.addView(item.preview, new FrameLayout.LayoutParams(-1, -1));

        item.hint = Cards.text(this, L10n.s(R.string.ui_151), 13, getColor(R.color.wine), AppFonts.regular());
        pbox.addView(item.hint, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        pbox.setOnClickListener(v -> chooseSource(100 + idx, false));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, Cards.dp(this, 150));
        plp.topMargin = Cards.dp(this, 8);
        plp.bottomMargin = Cards.dp(this, 8);
        card.addView(pbox, plp);

        item.etText = new EditText(this);
        item.etText.setHint(L10n.s(R.string.ui_152));
        item.etText.setTextSize(15);
        item.etText.setTypeface(AppFonts.regular());
        item.etText.setTextColor(getColor(R.color.ink));
        item.etText.setBackground(null);
        item.etText.setMinLines(2);
        item.etText.setGravity(Gravity.TOP);
        card.addView(item.etText);

        fpItems.add(item);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Cards.dp(this, 12);
        fpContainer.addView(card, clp);
    }

    private EditText addInput(LinearLayout col, String hint, int maxLen, boolean singleLine) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(getColor(R.color.ink_sub));
        et.setTextColor(getColor(R.color.ink));
        et.setTextSize(singleLine ? 16 : 16);
        et.setTypeface(AppFonts.regular());
        et.setSingleLine(singleLine);
        if (!singleLine) {
            et.setMinLines(5);
            et.setGravity(Gravity.TOP);
            et.setLineSpacing(0, 1.5f);
        }
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        GradientDrawable g = new GradientDrawable();
        g.setColor(getColor(R.color.paper));
        g.setCornerRadius(Cards.dp(this, 8));
        g.setStroke(Cards.dp(this, 1), 0xFFE2DDD3);
        et.setBackground(g);
        int p = Cards.dp(this, 14);
        et.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Cards.dp(this, 12);
        col.addView(et, lp);
        EditorUi.input(et,singleLine);
        return et;
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            dateStr = String.format(Locale.US, "%04d.%02d.%02d", y, m + 1, d);
            tvDate.setText(dateStr);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void onSave() {
        if (saving) return;
        String title = textOf(etTitle);
        String sub = textOf(etSub);
        String note = etNote != null ? textOf(etNote) : "";

        List<Uri> finalPhotos = new ArrayList<>();
        String[] fields = new String[0];

        if (type == Record.FOOTPRINT) {
            List<String> flist = new ArrayList<>();
            StringBuilder summary = new StringBuilder();
            for (FpItem it : fpItems) {
                String t = it.etTime.getText().toString().trim();
                String txt = it.etText.getText().toString().trim();
                if (t.isEmpty()) t = "12:00";
                if (!txt.isEmpty() || it.uri != null) {
                    flist.add(t + "|" + txt);
                    if (summary.length() == 0 && !txt.isEmpty()) summary.append(txt);
                    if (it.uri != null) finalPhotos.add(it.uri);
                }
            }
            if (title.isEmpty() && flist.isEmpty()) {
                Toast.makeText(this, L10n.s(R.string.ui_153), Toast.LENGTH_SHORT).show();
                return;
            }
            if (flist.isEmpty()) flist.add("12:00|" + title);
            if (title.isEmpty()) title = L10n.s(R.string.ui_042);
            note = summary.length() > 0 ? summary.toString() : L10n.s(R.string.ui_154);
            fields = flist.toArray(new String[0]);
        } else {
            finalPhotos.addAll(photoUris);
            boolean hasAny = !title.isEmpty() || !note.isEmpty() || coverUri != null || !finalPhotos.isEmpty();
            if (!hasAny) {
                Toast.makeText(this, L10n.s(R.string.ui_155), Toast.LENGTH_SHORT).show();
                return;
            }
            if (title.isEmpty()) {
                if (!note.isEmpty()) {
                    String firstLine = note.split("\n")[0];
                    title = firstLine.length() > 14 ? firstLine.substring(0, 14) : firstLine;
                } else {
                    title = type == Record.BOOK ? L10n.s(R.string.ui_156) : type == Record.MUSIC ? L10n.s(R.string.ui_157) : L10n.s(R.string.ui_158);
                }
            }
            if (type == Record.BOOK && coverUri != null) {
                fields = new String[]{"cover"};
            }
        }

        saving = true;
        btnSave.setText(L10n.s(R.string.ui_159));
        btnSave.setAlpha(0.6f);

        String[] pal = PALETTES[paletteIdx];
        Record r = new Record(type, title, sub, dateStr, null, note, pal[0], pal[1], fields);
        new Repo(this).saveNewRecord(r, coverUri, finalPhotos, ok -> runOnUiThread(() -> {
            if (!ok) {
                saving = false;
                btnSave.setText(L10n.s(R.string.ui_147));
                btnSave.setAlpha(1f);
                Toast.makeText(this, L10n.s(R.string.ui_160), Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, L10n.s(R.string.ui_161), Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private static String textOf(EditText et) {
        return et == null ? "" : et.getText().toString().trim();
    }
}
