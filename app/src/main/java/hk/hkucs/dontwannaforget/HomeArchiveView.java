package hk.hkucs.dontwannaforget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 暗调档案感主页与开场动画视图(1:1 还原 example/482ca37e3ef7ddfb77364d9d2f0c4281.jpg 比例):
 * - 左栏宽度 = 右栏宽度 = 41.06% 屏幕宽, 左右边距 = 中间栏距 = 6.0% 屏幕宽;
 * - 横幅卡高度 = 0.282 * 栏宽; 圆窗卡高度 = 0.619 * 栏宽; 竖幅海报卡高度 = 1.500 * 栏宽;
 * - 垂直间距 = 右栏顶部下沉量 = 0.1935 * 栏宽 (使右栏第一张「城市」底边与左栏第二张「自處」顶边精确齐平,且整页一屏完整呈现并留有底部呼吸感留白)。
 */
public class HomeArchiveView extends FrameLayout {

    public static final int STYLE_BANNER = 0; // 横幅文字卡(如: 年輪 / 城市 / 跨年)
    public static final int STYLE_CIRCLE = 1; // 圆形视窗封面卡(如: 自處)
    public static final int STYLE_POSTER = 2; // 竖幅海报封面卡(如: 左下长幅海报)

    public interface HostCallbacks {
        void onSelectModule(int filterType);
        void onRequestCreateNew();
        void onRequestAgent();
        void onRequestPickCover(String boxKey);
    }

    public static class BoxConfig {
        public String key;
        public int filterType;     // -1=全部, TICKET, VOICE, FOOTPRINT, MUSIC, BOOK, LETTER
        public String moduleName;  // 模块名(全部/声音/足迹/票根/音乐/书/写信)
        public String tag;         // 左上或竖排副标题(如 "20260914 全部記憶")
        public String title;       // 主标题(如 "年輪" / "自處" / "城市")
        public String serialId;    // 左下编号(如 "ID #004234")
        public String dateLabel;   // 右下日期(如 "2026.09.14")
        public int style;          // STYLE_BANNER / STYLE_CIRCLE / STYLE_POSTER
        public int column;         // 0=左栏, 1=右栏
        public String coverPath;   // 自定义封面图相对 filesDir 路径,可空

        public BoxConfig(String key, int filterType, String moduleName, String tag,
                         String title, String serialId, String dateLabel, int style, int column) {
            this.key = key;
            this.filterType = filterType;
            this.moduleName = moduleName;
            this.tag = tag;
            this.title = title;
            this.serialId = serialId;
            this.dateLabel = dateLabel;
            this.style = style;
            this.column = column;
            this.coverPath = "";
        }
    }

    private static final String PREF_NAME = "home_archive_config_v2";
    private static final String KEY_BOXES = "boxes_json";
    private static final String KEY_PRESET = "layout_preset";

    private final HostCallbacks callbacks;
    private final List<BoxConfig> boxes = new ArrayList<>();
    private final Map<String, Bitmap> coverCache = new HashMap<>();
    private Bitmap defaultCircleBmp;
    private Bitmap defaultPosterBmp;

    private boolean editMode = false;
    private int currentPreset = 0;

    private LinearLayout leftCol, rightCol, editBanner;
    private TextView btnEdit;
    private FrameLayout splashOverlay;

    public HomeArchiveView(Context context, HostCallbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        loadConfigs();
        buildUi();
    }

    private Bitmap getDefaultCircleBitmap() {
        if (defaultCircleBmp == null || defaultCircleBmp.isRecycled()) {
            defaultCircleBmp = BitmapFactory.decodeResource(getResources(), R.drawable.default_cover_circle);
        }
        return defaultCircleBmp;
    }

    private Bitmap getDefaultPosterBitmap() {
        if (defaultPosterBmp == null || defaultPosterBmp.isRecycled()) {
            defaultPosterBmp = BitmapFactory.decodeResource(getResources(), R.drawable.default_cover_poster);
        }
        return defaultPosterBmp;
    }

    // ---- 配置持久化与预设 ----
    private void loadConfigs() {
        SharedPreferences sp = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentPreset = sp.getInt(KEY_PRESET, 0);
        String raw = sp.getString(KEY_BOXES, null);
        boxes.clear();
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    BoxConfig b = new BoxConfig(
                            o.optString("key"),
                            o.optInt("filterType", -1),
                            o.optString("moduleName", "记忆"),
                            o.optString("tag", ""),
                            o.optString("title", ""),
                            o.optString("serialId", "ID #004234"),
                            o.optString("dateLabel", "2026.09.14"),
                            o.optInt("style", STYLE_BANNER),
                            o.optInt("column", 0)
                    );
                    b.coverPath = o.optString("coverPath", "");
                    boxes.add(b);
                }
            } catch (Exception ignored) {}
        }
        if (boxes.isEmpty()) {
            resetToDefaultBoxes(false);
        }
    }

    private void saveConfigs() {
        try {
            JSONArray arr = new JSONArray();
            for (BoxConfig b : boxes) {
                JSONObject o = new JSONObject();
                o.put("key", b.key);
                o.put("filterType", b.filterType);
                o.put("moduleName", b.moduleName);
                o.put("tag", b.tag);
                o.put("title", b.title);
                o.put("serialId", b.serialId);
                o.put("dateLabel", b.dateLabel);
                o.put("style", b.style);
                o.put("column", b.column);
                o.put("coverPath", b.coverPath == null ? "" : b.coverPath);
                arr.put(o);
            }
            getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_BOXES, arr.toString())
                    .putInt(KEY_PRESET, currentPreset)
                    .apply();
        } catch (Exception ignored) {}
    }

    private void resetToDefaultBoxes(boolean clearCovers) {
        Map<String, String> oldCovers = new HashMap<>();
        if (!clearCovers) {
            for (BoxConfig b : boxes) {
                if (b.coverPath != null && !b.coverPath.isEmpty()) {
                    oldCovers.put(b.key, b.coverPath);
                }
            }
        }
        boxes.clear();
        // 左栏: 1.横幅(全部-年輪) + 2.圆形视窗(声音-自處) + 3.竖幅海报(足迹)
        boxes.add(new BoxConfig("all", -1, "全部记忆",
                "20260914 全部記憶", "年輪", "ID #004234", "2026.09.14", STYLE_BANNER, 0));
        boxes.add(new BoxConfig("voice", Record.VOICE, "声音",
                "留聲 · 聲音記憶", "自處", "ID #004234", "2026.09.14", STYLE_CIRCLE, 0));
        boxes.add(new BoxConfig("footprint", Record.FOOTPRINT, "足迹",
                "20260711重慶哦啦音樂節", "足跡", "ID #004234", "2026-07-11", STYLE_POSTER, 0));

        // 右栏: 4张错落横幅文字卡(票根 / 音乐 / 书 / 写信)
        boxes.add(new BoxConfig("ticket", Record.TICKET, "票根",
                "20260619 電影票根", "城市", "ID #004234", "2026.06.19", STYLE_BANNER, 1));
        boxes.add(new BoxConfig("music", Record.MUSIC, "音乐",
                "20260417 此刻在聽", "希望能讓一場", "ID #004234", "2026.04.17", STYLE_BANNER, 1));
        boxes.add(new BoxConfig("book", Record.BOOK, "书",
                "20251231 讀過的書", "跨年", "ID #004234", "2025.12.31", STYLE_BANNER, 1));
        boxes.add(new BoxConfig("letter", Record.LETTER, "写信",
                "20220131 寫信便籤", "內心平安", "ID #004234", "2022.01.31", STYLE_BANNER, 1));

        for (BoxConfig b : boxes) {
            if (oldCovers.containsKey(b.key)) b.coverPath = oldCovers.get(b.key);
        }
        currentPreset = 0;
        saveConfigs();
    }

    private void applyLayoutPreset(int preset) {
        currentPreset = preset;
        if (preset == 0) {
            resetToDefaultBoxes(false);
        } else if (preset == 1) {
            for (int i = 0; i < boxes.size(); i++) {
                BoxConfig b = boxes.get(i);
                b.column = (i % 2);
                if (i == 0 || i == 5) b.style = STYLE_CIRCLE;
                else if (i == 2 || i == 3) b.style = STYLE_POSTER;
                else b.style = STYLE_BANNER;
            }
            saveConfigs();
        } else if (preset == 2) {
            for (int i = 0; i < boxes.size(); i++) {
                BoxConfig b = boxes.get(i);
                b.column = i < 3 ? 0 : 1;
                b.style = STYLE_BANNER;
            }
            saveConfigs();
        }
        rebuildGrid();
    }

    // ---- UI 构建 (严格按照 482ca37e3ef7ddfb77364d9d2f0c4281.jpg 的几何比例) ----
    private void buildUi() {
        removeAllViews();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        if (screenW <= 0) screenW = Cards.dp(getContext(), 392);
        // 参考图中: 左边距 = 右边距 = 中间栏距 = 72 / 1208 = 5.96% 屏幕宽
        int padH = Math.round(screenW * 0.0596f);
        int colGap = Math.round(screenW * 0.0596f);

        // 1. 底层背景图 (example/background.jpg -> R.drawable.bg_archive)
        ImageView bg = new ImageView(getContext());
        bg.setImageResource(R.drawable.bg_archive);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(bg, new LayoutParams(-1, -1));

        // 柔和渐变层,还原参考图上深下灰的朦胧暗调质感
        View vignette = new View(getContext());
        GradientDrawable vg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x700A0A0C, 0x281C1D20, 0x48222428});
        vignette.setBackground(vg);
        addView(vignette, new LayoutParams(-1, -1));

        // 2. 主页滚动容器
        ScrollView sv = new ScrollView(getContext());
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padH, Cards.dp(getContext(), 8), padH, Cards.dp(getContext(), 56));

        // 2.1 顶部栏: 左侧原版艺术签名 + 右侧紧凑排列的 [EDIT] [LAYOUT] [(記) MEMORY +]
        LinearLayout topBar = new LinearLayout(getContext());
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, Cards.dp(getContext(), 2), 0, Math.round(screenW * 0.075f));

        ImageView sig = new ImageView(getContext());
        sig.setImageResource(R.drawable.sig_logo);
        sig.setScaleType(ImageView.ScaleType.FIT_START);
        sig.setOnClickListener(v -> playEntranceAnimation());
        LinearLayout.LayoutParams sigLp = new LinearLayout.LayoutParams(
                Cards.dp(getContext(), 84), Cards.dp(getContext(), 46));
        sigLp.leftMargin = -Cards.dp(getContext(), 6);
        topBar.addView(sig, sigLp);

        View spacer = new View(getContext());
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        btnEdit = topOutlineButton("EDIT", false);
        btnEdit.setOnClickListener(v -> toggleEditMode());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-2, -2);
        elp.rightMargin = Cards.dp(getContext(), 6);
        topBar.addView(btnEdit, elp);

        TextView btnLayout = topOutlineButton("LAYOUT", false);
        btnLayout.setOnClickListener(v -> showLayoutPresetDialog());
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.rightMargin = Cards.dp(getContext(), 6);
        topBar.addView(btnLayout, llp);

        LinearLayout btnNew = buildProfileStyleNewButton();
        btnNew.setOnClickListener(v -> {
            if (callbacks != null) callbacks.onRequestCreateNew();
        });
        topBar.addView(btnNew, new LinearLayout.LayoutParams(-2, -2));

        content.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        // 2.2 编辑模式提示横幅(默认隐藏,点击 EDIT 后展开)
        editBanner = buildEditBanner();
        editBanner.setVisibility(GONE);
        content.addView(editBanner, new LinearLayout.LayoutParams(-1, -2));

        // 2.3 双栏不对称档案网格
        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setBaselineAligned(false);

        leftCol = new LinearLayout(getContext());
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lColLp = new LinearLayout.LayoutParams(0, -2, 1f);
        lColLp.rightMargin = colGap / 2;
        grid.addView(leftCol, lColLp);

        rightCol = new LinearLayout(getContext());
        rightCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rColLp = new LinearLayout.LayoutParams(0, -2, 1f);
        rColLp.leftMargin = colGap / 2;
        grid.addView(rightCol, rColLp);

        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        TextView agentEntry = Cards.text(getContext(), "记忆对话  ↗", 13, 0xFFD9D5CC, AppFonts.regular());
        agentEntry.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        agentEntry.setMinHeight(Cards.dp(getContext(), 48));
        agentEntry.setContentDescription("打开记忆对话，上传文字和查看关于我的线索");
        agentEntry.setOnClickListener(v -> { if (callbacks != null) callbacks.onRequestAgent(); });
        content.addView(agentEntry, new LinearLayout.LayoutParams(-1, -2));

        sv.addView(content, new LayoutParams(-1, -2));
        addView(sv, new LayoutParams(-1, -1));

        rebuildGrid();
    }

    private TextView topOutlineButton(String label, boolean active) {
        TextView tv = Cards.text(getContext(), label, 9.5f,
                active ? 0xFFF2D398 : 0xFFC8C6C0, AppFonts.regular());
        tv.setLetterSpacing(0.10f);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        int ph = Cards.dp(getContext(), 8);
        int pv = Cards.dp(getContext(), 4.5f);
        tv.setPadding(ph, pv, ph, pv);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(active ? 0xCC3A2820 : 0x88141517);
        gd.setCornerRadius(Cards.dp(getContext(), 1.5f));
        gd.setStroke(1, active ? 0xCCF2D398 : 0x2EFFFFFF);
        tv.setBackground(gd);
        return tv;
    }

    /** 模仿参考图右上角 "(袁) MY PROFILE +" 样式的紧凑按钮 */
    private LinearLayout buildProfileStyleNewButton() {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int ph = Cards.dp(c, 7);
        int pv = Cards.dp(c, 4f);
        box.setPadding(ph, pv, ph, pv);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0x88141517);
        gd.setCornerRadius(Cards.dp(c, 1.5f));
        gd.setStroke(1, 0x2EFFFFFF);
        box.setBackground(gd);

        TextView seal = Cards.text(c, "記", 7.5f, 0xFFE5E2DC, AppFonts.regular());
        seal.setGravity(Gravity.CENTER);
        GradientDrawable sg = new GradientDrawable();
        sg.setShape(GradientDrawable.OVAL);
        sg.setColor(0x00000000);
        sg.setStroke(1, 0xAAFFFFFF);
        seal.setBackground(sg);
        int sSize = Cards.dp(c, 14);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(sSize, sSize);
        slp.rightMargin = Cards.dp(c, 4);
        box.addView(seal, slp);

        TextView txt = Cards.text(c, "MEMORY +", 9.5f, 0xFFE5E2DC, AppFonts.regular());
        txt.setSingleLine(true);
        txt.setLetterSpacing(0.08f);
        box.addView(txt);
        return box;
    }

    private LinearLayout buildEditBanner() {
        Context c = getContext();
        LinearLayout banner = new LinearLayout(c);
        banner.setOrientation(LinearLayout.VERTICAL);
        int p = Cards.dp(c, 10);
        banner.setPadding(p, p, p, p);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xD91E1918);
        gd.setCornerRadius(Cards.dp(c, 4));
        gd.setStroke(1, 0x88D9A464);
        banner.setBackground(gd);

        TextView tip = Cards.text(c,
                "✎ 编辑模式：轻点任意模块框自定义【封面照片】、【样式(横幅/圆窗/海报)】、【标题】与【左右栏位置】。",
                11f, 0xFFF2E6D0, AppFonts.regular());
        tip.setLineSpacing(0, 1.25f);
        banner.addView(tip);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = Cards.dp(c, 12);
        banner.setLayoutParams(blp);
        return banner;
    }

    private void toggleEditMode() {
        editMode = !editMode;
        btnEdit.setText(editMode ? "DONE" : "EDIT");
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(editMode ? 0xCC4A2C24 : 0x88141517);
        gd.setCornerRadius(Cards.dp(getContext(), 1.5f));
        gd.setStroke(1, editMode ? 0xFFD9A464 : 0x2EFFFFFF);
        btnEdit.setBackground(gd);
        btnEdit.setTextColor(editMode ? 0xFFF5D799 : 0xFFC8C6C0);
        editBanner.setVisibility(editMode ? VISIBLE : GONE);
        rebuildGrid();
    }

    public void rebuildGrid() {
        if (leftCol == null || rightCol == null) return;
        leftCol.removeAllViews();
        rightCol.removeAllViews();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        if (screenW <= 0) screenW = Cards.dp(getContext(), 392);
        // 精确计算单栏像素宽度 (496 / 1208 = 41.06% 屏幕宽)
        float colW = screenW * 0.4106f;

        // 1:1 还原参考图的卡片高度与间距比例:
        // 横幅卡高度 = 140 / 496 * colW
        // 圆窗卡高度 = 307 / 496 * colW
        // 海报卡高度 = 744 / 496 * colW
        // 卡片垂直间距 = 右栏顶部下沉 = 96 / 496 * colW
        int bannerH = Math.round(colW * (140f / 496f));
        int circleH = Math.round(colW * (307f / 496f));
        int posterH = Math.round(colW * (744f / 496f));
        int cardGap = Math.round(colW * (96f / 496f));

        rightCol.setPadding(0, cardGap, 0, 0);

        for (BoxConfig b : boxes) {
            ArchiveBoxView view = new ArchiveBoxView(getContext(), b);
            int hPx = b.style == STYLE_POSTER ? posterH
                    : (b.style == STYLE_CIRCLE ? circleH : bannerH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, hPx);
            lp.bottomMargin = cardGap;
            view.setOnClickListener(v -> {
                if (editMode) {
                    showBoxEditDialog(b);
                } else if (callbacks != null) {
                    callbacks.onSelectModule(b.filterType);
                }
            });
            view.setOnLongClickListener(v -> {
                showBoxEditDialog(b);
                return true;
            });
            if (b.column == 0) {
                leftCol.addView(view, lp);
            } else {
                rightCol.addView(view, lp);
            }
        }
    }

    // ---- 編輯弹窗与自定义封面照片 ----
    private void showLayoutPresetDialog() {
        String[] items = {
                "预设 A：经典左图右文 (1:1 还原参考图布局)",
                "预设 B：错落双栏画廊 (多封面交错)",
                "预设 C：极简暗调档案 (全横幅文字)",
                "恢复默认封面与文字"
        };
        new AlertDialog.Builder(getContext())
                .setTitle("选择主页整体布局")
                .setItems(items, (d, which) -> {
                    if (which == 3) {
                        coverCache.clear();
                        resetToDefaultBoxes(true);
                        rebuildGrid();
                        Toast.makeText(getContext(), "已恢复默认主页布局与封面", Toast.LENGTH_SHORT).show();
                    } else {
                        applyLayoutPreset(which);
                        Toast.makeText(getContext(), "已切换主页布局", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showBoxEditDialog(BoxConfig b) {
        Context c = getContext();
        ScrollView sv = new ScrollView(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(c, 20);
        col.setPadding(pad, Cards.dp(c, 12), pad, Cards.dp(c, 8));
        sv.addView(col);

        // 1. 更换/清除封面图按钮
        LinearLayout coverRow = new LinearLayout(c);
        coverRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnPickPhoto = dialogActionBtn("📷 从相册选择封面照片", 0xFF7A1F2B, Color.WHITE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -2, 1f);
        plp.rightMargin = Cards.dp(c, 8);
        coverRow.addView(btnPickPhoto, plp);

        TextView btnClearPhoto = dialogActionBtn("恢复默认封面", 0xFFEAE5DC, 0xFF2B2623);
        coverRow.addView(btnClearPhoto, new LinearLayout.LayoutParams(-2, -2));
        col.addView(coverRow, new LinearLayout.LayoutParams(-1, -2));

        // 2. 卡片样式选择
        TextView styleLabel = Cards.text(c, "卡片布局样式", 12, 0xFF666666, AppFonts.regular());
        styleLabel.setPadding(0, Cards.dp(c, 14), 0, Cards.dp(c, 6));
        col.addView(styleLabel);

        String[] styleNames = {"横幅文字卡 (如: 年輪/城市)", "圆形视窗封面 (如: 自處)", "竖幅海报封面 (如: 左下长图)"};
        final int[] chosenStyle = {b.style};
        LinearLayout styleCol = new LinearLayout(c);
        styleCol.setOrientation(LinearLayout.VERTICAL);
        final TextView[] styleBtns = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView sb = Cards.text(c, (b.style == i ? "● " : "○ ") + styleNames[i], 14,
                    b.style == i ? 0xFF7A1F2B : 0xFF2B2623, AppFonts.regular());
            sb.setPadding(Cards.dp(c, 10), Cards.dp(c, 8), Cards.dp(c, 10), Cards.dp(c, 8));
            sb.setOnClickListener(v -> {
                chosenStyle[0] = idx;
                for (int j = 0; j < 3; j++) {
                    styleBtns[j].setText((j == idx ? "● " : "○ ") + styleNames[j]);
                    styleBtns[j].setTextColor(j == idx ? 0xFF7A1F2B : 0xFF2B2623);
                }
            });
            styleBtns[i] = sb;
            styleCol.addView(sb);
        }
        col.addView(styleCol);

        // 3. 所在列与上下移动
        TextView colLabel = Cards.text(c, "所在栏位与顺序", 12, 0xFF666666, AppFonts.regular());
        colLabel.setPadding(0, Cards.dp(c, 10), 0, Cards.dp(c, 6));
        col.addView(colLabel);

        final int[] chosenCol = {b.column};
        LinearLayout posRow = new LinearLayout(c);
        posRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnLeft = dialogActionBtn(b.column == 0 ? "● 左栏" : "○ 左栏", 0xFFF2EFE9, 0xFF2B2623);
        TextView btnRight = dialogActionBtn(b.column == 1 ? "● 右栏" : "○ 右栏", 0xFFF2EFE9, 0xFF2B2623);
        btnLeft.setOnClickListener(v -> {
            chosenCol[0] = 0;
            btnLeft.setText("● 左栏");
            btnRight.setText("○ 右栏");
        });
        btnRight.setOnClickListener(v -> {
            chosenCol[0] = 1;
            btnLeft.setText("○ 左栏");
            btnRight.setText("● 右栏");
        });
        LinearLayout.LayoutParams clp1 = new LinearLayout.LayoutParams(0, -2, 1f);
        clp1.rightMargin = Cards.dp(c, 6);
        posRow.addView(btnLeft, clp1);
        posRow.addView(btnRight, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(posRow);

        // 4. 标题与副标题输入
        TextView txtLabel = Cards.text(c, "卡片大标题与副标题", 12, 0xFF666666, AppFonts.regular());
        txtLabel.setPadding(0, Cards.dp(c, 12), 0, Cards.dp(c, 4));
        col.addView(txtLabel);

        EditText etTitle = new EditText(c);
        etTitle.setHint("卡片主标题 (如: 年輪 / 自處 / 城市)");
        etTitle.setText(b.title);
        etTitle.setTextSize(15);
        etTitle.setTypeface(AppFonts.regular());
        etTitle.setSingleLine(true);
        etTitle.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        col.addView(etTitle);

        EditText etTag = new EditText(c);
        etTag.setHint("左上/竖排副标题 (如: 20260914 電子報)");
        etTag.setText(b.tag);
        etTag.setTextSize(14);
        etTag.setTypeface(AppFonts.regular());
        etTag.setSingleLine(true);
        etTag.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
        col.addView(etTag);

        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle("编辑入口组件 · 【" + b.moduleName + "】")
                .setView(sv)
                .setPositiveButton("保存修改", (d, w) -> {
                    b.style = chosenStyle[0];
                    b.column = chosenCol[0];
                    String newTitle = etTitle.getText().toString().trim();
                    String newTag = etTag.getText().toString().trim();
                    if (!newTitle.isEmpty()) b.title = newTitle;
                    if (!newTag.isEmpty()) b.tag = newTag;
                    saveConfigs();
                    rebuildGrid();
                })
                .setNeutralButton("↑ 上移排序", (d, w) -> {
                    int idx = boxes.indexOf(b);
                    if (idx > 0) {
                        boxes.remove(idx);
                        boxes.add(idx - 1, b);
                        saveConfigs();
                        rebuildGrid();
                    }
                })
                .setNegativeButton("取消", null)
                .create();

        btnPickPhoto.setOnClickListener(v -> {
            b.style = chosenStyle[0];
            b.column = chosenCol[0];
            String newTitle = etTitle.getText().toString().trim();
            String newTag = etTag.getText().toString().trim();
            if (!newTitle.isEmpty()) b.title = newTitle;
            if (!newTag.isEmpty()) b.tag = newTag;
            saveConfigs();
            dialog.dismiss();
            if (callbacks != null) callbacks.onRequestPickCover(b.key);
        });

        btnClearPhoto.setOnClickListener(v -> {
            b.coverPath = "";
            coverCache.remove(b.key);
            saveConfigs();
            rebuildGrid();
            dialog.dismiss();
            Toast.makeText(c, "已恢复默认封面", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private TextView dialogActionBtn(String text, int bg, int fg) {
        TextView t = Cards.text(getContext(), text, 13, fg, AppFonts.regular());
        t.setGravity(Gravity.CENTER);
        int p = Cards.dp(getContext(), 10);
        t.setPadding(p, p, p, p);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(Cards.dp(getContext(), 6));
        t.setBackground(gd);
        return t;
    }

    /** 当用户从相册选完某张封面照片后由 MainActivity 调用 */
    public void onCoverUriPicked(String boxKey, Uri uri) {
        if (uri == null || boxKey == null) return;
        try {
            File dir = new File(getContext().getFilesDir(), "home_covers");
            if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, "cover_" + boxKey + "_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContext().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dst)) {
                if (in == null) return;
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            for (BoxConfig b : boxes) {
                if (boxKey.equals(b.key)) {
                    b.coverPath = "home_covers/" + dst.getName();
                    break;
                }
            }
            coverCache.remove(boxKey);
            saveConfigs();
            rebuildGrid();
            Toast.makeText(getContext(), "已更新卡片封面照片", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "封面图片导入失败", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap loadCoverBitmap(BoxConfig b) {
        if (b.coverPath == null || b.coverPath.isEmpty()) return null;
        if (coverCache.containsKey(b.key)) return coverCache.get(b.key);
        File f = new File(getContext().getFilesDir(), b.coverPath);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            int sample = 1;
            int maxDim = Math.max(opts.outWidth, opts.outHeight);
            while (maxDim / sample > 1080) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (bmp != null) coverCache.put(b.key, bmp);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 开场动画 (仿 example/loginexp.jpg -> 文艺短句 -> 进入主页) ----
    public void playEntranceAnimation() {
        if (splashOverlay != null) {
            removeView(splashOverlay);
            splashOverlay = null;
        }
        Context c = getContext();
        splashOverlay = new FrameLayout(c);
        splashOverlay.setClickable(true);
        splashOverlay.setFocusable(true);

        ImageView splashBg = new ImageView(c);
        splashBg.setImageResource(R.drawable.bg_archive);
        splashBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splashOverlay.addView(splashBg, new LayoutParams(-1, -1));

        View darkVeil = new View(c);
        darkVeil.setBackgroundColor(0xD8141416);
        splashOverlay.addView(darkVeil, new LayoutParams(-1, -1));

        WelcomeGlowTextView stage1View = new WelcomeGlowTextView(c);
        splashOverlay.addView(stage1View, new LayoutParams(-1, -1));

        LinearLayout stage2Box = new LinearLayout(c);
        stage2Box.setOrientation(LinearLayout.VERTICAL);
        stage2Box.setGravity(Gravity.CENTER);
        stage2Box.setAlpha(0f);
        int pad = Cards.dp(c, 32);
        stage2Box.setPadding(pad, pad, pad, pad);

        TextView quoteCn = Cards.text(c, "「 时间会走远，\n但被记住的瞬间会一直发光。 」",
                19f, 0xFFE6E2D8, Typeface.create(AppFonts.regular(), Typeface.ITALIC));
        quoteCn.setGravity(Gravity.CENTER);
        quoteCn.setLineSpacing(0, 1.55f);
        quoteCn.setShadowLayer(18f, 0f, 0f, 0x88FFFFFF);
        stage2Box.addView(quoteCn);

        TextView quoteEn = Cards.text(c,
                "Time drifts away, yet the moments we keep remain incandescent.",
                11f, 0xFF9A968E, Typeface.create(AppFonts.regular(), Typeface.ITALIC));
        quoteEn.setGravity(Gravity.CENTER);
        quoteEn.setPadding(0, Cards.dp(c, 16), 0, 0);
        stage2Box.addView(quoteEn);

        splashOverlay.addView(stage2Box, new LayoutParams(-1, -1, Gravity.CENTER));

        TextView skipHint = Cards.text(c, "轻点屏幕直接进入 · TAP TO ENTER", 9.5f,
                0x66B0ACA4, AppFonts.regular());
        skipHint.setGravity(Gravity.CENTER);
        LayoutParams slp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.bottomMargin = Cards.dp(c, 36);
        splashOverlay.addView(skipHint, slp);

        addView(splashOverlay, new LayoutParams(-1, -1));

        splashOverlay.setOnClickListener(v -> dismissSplash());

        stage1View.setAlpha(0f);
        stage1View.setScaleX(0.96f);
        stage1View.setScaleY(0.96f);
        stage1View.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(650)
                .withEndAction(() -> {
                    if (splashOverlay == null) return;
                    stage1View.animate()
                            .alpha(0f)
                            .setStartDelay(950)
                            .setDuration(500)
                            .start();
                    stage2Box.setTranslationY(Cards.dp(c, 12));
                    stage2Box.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setStartDelay(1150)
                            .setDuration(700)
                            .withEndAction(() -> {
                                if (splashOverlay == null) return;
                                splashOverlay.postDelayed(this::dismissSplash, 1350);
                            })
                            .start();
                })
                .start();
    }

    private void dismissSplash() {
        if (splashOverlay == null) return;
        final View target = splashOverlay;
        splashOverlay = null;
        target.animate()
                .alpha(0f)
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(520)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeView(target);
                    }
                })
                .start();
    }

    /**
     * 还原 example/loginexp.jpg 的欢迎页文字视图:
     * "Welcome " 为清晰衬线体, "to your memory." 带有向右上方微偏的重影与柔光散射效果。
     */
    private static class WelcomeGlowTextView extends View {
        private final Paint pClean = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pGlow = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pGhost = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);

        WelcomeGlowTextView(Context c) {
            super(c);
            Typeface serifBold = Typeface.create(AppFonts.regular(), Typeface.BOLD);
            Typeface serifReg = Typeface.create(AppFonts.regular(), Typeface.NORMAL);

            pClean.setTypeface(serifReg);
            pClean.setColor(0xFFC8C6C2);

            pGlow.setTypeface(serifBold);
            pGlow.setColor(0xFFDCDAD6);
            pGlow.setShadowLayer(14f, 0f, 0f, 0xAAFFFFFF);

            pGhost.setTypeface(serifBold);
            pGhost.setColor(0x66EAE8E4);
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float d = getResources().getDisplayMetrics().density;
            float textSize = 20.5f * d;
            pClean.setTextSize(textSize);
            pGlow.setTextSize(textSize);
            pGhost.setTextSize(textSize);

            String part1 = "Welcome ";
            String part2 = "to your memory.";
            float w1 = pClean.measureText(part1);
            float w2 = pGlow.measureText(part2);
            float totalW = w1 + w2;

            float startX = (w - totalW) / 2f;
            float baseY = h / 2f + textSize * 0.35f;

            cv.drawText(part1, startX, baseY, pClean);

            float x2 = startX + w1;
            cv.drawText(part2, x2 + 1.8f * d, baseY - 1.2f * d, pGhost);
            cv.drawText(part2, x2 - 1.2f * d, baseY + 0.8f * d, pGhost);
            cv.drawText(part2, x2, baseY, pGlow);
        }
    }

    /**
     * 单个暗调档案框视图,所有字号与内边距均按卡片自身宽高自适应缩放,1:1 还原参考图比例。
     */
    private class ArchiveBoxView extends View {
        private final BoxConfig cfg;
        private final Paint paint = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();
        private final Matrix matrix = new Matrix();

        ArchiveBoxView(Context c, BoxConfig cfg) {
            super(c);
            this.cfg = cfg;
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float d = getResources().getDisplayMetrics().density;
            Bitmap cover = loadCoverBitmap(cfg);

            if (cfg.style == STYLE_CIRCLE) {
                drawCircleStyle(cv, w, h, d, cover);
            } else if (cfg.style == STYLE_POSTER) {
                drawPosterStyle(cv, w, h, d, cover);
            } else {
                drawBannerStyle(cv, w, h, d, cover);
            }

            if (editMode) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xDD7A1F2B);
                float bw = 46f * d;
                float bh = 15f * d;
                rect.set(w - bw - 4f * d, 4f * d, w - 4f * d, 4f * d + bh);
                cv.drawRoundRect(rect, 3f * d, 3f * d, paint);

                paint.setColor(0xFFF5E6C8);
                paint.setTypeface(AppFonts.regular());
                paint.setTextSize(8f * d);
                paint.setTextAlign(Paint.Align.CENTER);
                cv.drawText("✎ 编辑", rect.centerX(), rect.centerY() + 2.8f * d, paint);
            }
        }

        /** 样式 1: 横幅文字卡 (140x496 比例, 还原「年輪」「城市」「希望能讓一場」「跨年」「內心平安」) */
        private void drawBannerStyle(Canvas cv, int w, int h, float d, Bitmap cover) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    0xEE222426, 0xEE181A1C, Shader.TileMode.CLAMP));
            rect.set(0, 0, w, h);
            cv.drawRect(rect, paint);
            paint.setShader(null);

            if (cover != null && !cover.isRecycled()) {
                drawCenterCropBitmap(cv, cover, w, h, 85);
            }

            // 外框黑线与内侧极细微光边线
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * d);
            paint.setColor(0xFF0A0B0C);
            cv.drawRect(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d, paint);

            paint.setStrokeWidth(0.5f * d);
            paint.setColor(0x16FFFFFF);
            cv.drawRect(1.4f * d, 1.4f * d, w - 1.4f * d, h - 1.4f * d, paint);

            // 左上角日期与副标题 (按卡片宽度比例字号, 还原参考图左上角精致小字)
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF7D8184);
            paint.setTypeface(AppFonts.regular());
            paint.setTextSize(w * 0.048f);
            paint.setTextAlign(Paint.Align.LEFT);
            cv.drawText(cfg.tag, w * 0.052f, h * 0.27f, paint);

            // 中央偏右斜体衬线大标题
            paint.setColor(0xFF83878A);
            paint.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD_ITALIC));
            float titleSize = cfg.title.length() >= 5 ? (w * 0.106f)
                    : (cfg.title.length() >= 4 ? (w * 0.118f) : (w * 0.132f));
            paint.setTextSize(titleSize);
            paint.setTextAlign(Paint.Align.CENTER);
            float titleX = cfg.title.length() >= 5 ? (w * 0.62f)
                    : (cfg.title.length() >= 4 ? (w * 0.60f) : (w * 0.54f));
            cv.drawText(cfg.title, titleX, h * 0.58f, paint);

            // 左下角 ID 编号与右下角日期 (极细微字号, 左右留足空白)
            paint.setColor(0xFF4D5154);
            paint.setTypeface(AppFonts.regular());
            paint.setTextSize(w * 0.033f);
            paint.setTextAlign(Paint.Align.LEFT);
            cv.drawText(cfg.serialId, w * 0.052f, h * 0.87f, paint);

            paint.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(cfg.dateLabel, w * 0.948f, h * 0.87f, paint);
        }

        /** 样式 2: 圆形视窗封面卡 (307x496 比例, 还原参考图左中「自處」) */
        private void drawCircleStyle(Canvas cv, int w, int h, float d, Bitmap cover) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF09090A);
            cv.drawRect(0, 0, w, h, paint);

            if (cover != null && !cover.isRecycled()) {
                float cx = w * 0.51f;
                float cy = h * 0.49f;
                float radius = h * 0.41f;
                cv.save();
                clipPath.reset();
                clipPath.addCircle(cx, cy, radius, Path.Direction.CW);
                cv.clipPath(clipPath);
                drawCenterCropBitmap(cv, cover, w, h, 240);
                cv.restore();
            } else {
                // 使用从参考图提取的原版「自處」圆形逆光剪影图
                Bitmap def = getDefaultCircleBitmap();
                if (def != null) {
                    drawCenterCropBitmap(cv, def, w, h, 255);
                }
            }

            // 左上竖排文字 (如 "自處")
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFC4C0B8);
            paint.setTypeface(AppFonts.regular());
            float vSize = w * 0.048f;
            paint.setTextSize(vSize);
            paint.setTextAlign(Paint.Align.LEFT);
            String vert = cfg.title.isEmpty() ? "自處" : cfg.title;
            float vx = w * 0.042f;
            float vy = h * 0.115f;
            for (int i = 0; i < Math.min(vert.length(), 6); i++) {
                cv.drawText(String.valueOf(vert.charAt(i)), vx, vy + i * (vSize * 1.18f), paint);
            }

            // 左下 ID 与右下日期
            paint.setColor(0xFF8C7E68);
            paint.setTypeface(AppFonts.regular());
            paint.setTextSize(w * 0.033f);
            paint.setTextAlign(Paint.Align.LEFT);
            cv.drawText(cfg.serialId, w * 0.042f, h * 0.935f, paint);

            paint.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(cfg.dateLabel, w * 0.958f, h * 0.935f, paint);

            // 细边框
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * d);
            paint.setColor(0xFF050506);
            cv.drawRect(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d, paint);
        }

        /** 样式 3: 竖幅海报封面卡 (744x496 比例, 还原参考图左下长幅海报) */
        private void drawPosterStyle(Canvas cv, int w, int h, float d, Bitmap cover) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF181410);
            cv.drawRect(0, 0, w, h, paint);

            Bitmap toDraw = (cover != null && !cover.isRecycled()) ? cover : getDefaultPosterBitmap();
            if (toDraw != null) {
                drawCenterCropBitmap(cv, toDraw, w, h, 250);
            }

            // 顶部与底部轻微暗角,保证文字清晰
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0x44000000, 0x00000000, 0x77000000},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            // 左上竖排文字: 还原参考图中数字旋转90度或紧凑纵排 + 中文竖排, 总高度控制在卡片上半部 42% 内
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFDCC8A2);
            paint.setTypeface(AppFonts.regular());
            float vSize = w * 0.046f;
            paint.setTextSize(vSize);
            paint.setTextAlign(Paint.Align.CENTER);

            String vertText = (cfg.tag != null && !cfg.tag.isEmpty()) ? cfg.tag.replace(" ", "") : cfg.title;
            float cx = w * 0.062f;
            float curY = h * 0.045f;
            for (int i = 0; i < Math.min(vertText.length(), 16); i++) {
                char ch = vertText.charAt(i);
                if (ch <= 127) {
                    // 英文/数字旋转90度顺排,与参考图左上 "20260711" 完全一致
                    cv.save();
                    cv.translate(cx, curY);
                    cv.rotate(90f);
                    cv.drawText(String.valueOf(ch), 0, vSize * 0.35f, paint);
                    cv.restore();
                    curY += vSize * 0.66f;
                } else {
                    curY += vSize * 0.32f;
                    cv.drawText(String.valueOf(ch), cx, curY, paint);
                    curY += vSize * 0.86f;
                }
            }

            // 左下 ID 与右下日期(暖金色小字)
            paint.setColor(0xFFC4A468);
            paint.setTypeface(AppFonts.regular());
            paint.setTextSize(w * 0.033f);
            paint.setTextAlign(Paint.Align.LEFT);
            cv.drawText(cfg.serialId, w * 0.042f, h * 0.968f, paint);

            paint.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(cfg.dateLabel, w * 0.958f, h * 0.968f, paint);

            // 外框边线
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * d);
            paint.setColor(0xFF0A0A0B);
            cv.drawRect(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d, paint);
        }

        private void drawCenterCropBitmap(Canvas cv, Bitmap bmp, int viewW, int viewH, int alpha) {
            int bw = bmp.getWidth();
            int bh = bmp.getHeight();
            if (bw <= 0 || bh <= 0) return;
            float scale = Math.max((float) viewW / bw, (float) viewH / bh);
            float dx = (viewW - bw * scale) * 0.5f;
            float dy = (viewH - bh * scale) * 0.5f;
            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(dx, dy);
            int oldAlpha = paint.getAlpha();
            paint.setAlpha(alpha);
            cv.drawBitmap(bmp, matrix, paint);
            paint.setAlpha(oldAlpha);
        }
    }
}
