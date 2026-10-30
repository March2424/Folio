package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字体管理器:提供内置字体风格与用户从本地上传的自定义字体(.ttf / .otf)。
 * 上传的字体持久化保存在 getFilesDir()/fonts/ 目录下。
 */
public final class FontManager {
    public static final String FONT_SKETCH = "sketch";   // 随手字迹(默认,模仿 editexp1 宽字距手写感)
    public static final String FONT_SERIF = "serif";     // 衬线手记
    public static final String FONT_MONO = "mono";       // 打字机
    public static final String FONT_ITALIC = "italic";   // 斜体书信

    private static final Map<String, Typeface> CACHE = new HashMap<>();

    private FontManager() {}

    public static class FontOption {
        public final String id;
        public final String label;
        public FontOption(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static List<FontOption> listOptions(Context c) {
        List<FontOption> list = new ArrayList<>();
        list.add(new FontOption(FONT_SKETCH, L10n.s(R.string.ui_001)));
        list.add(new FontOption(FONT_SERIF, L10n.s(R.string.ui_002)));
        list.add(new FontOption(FONT_MONO, L10n.s(R.string.ui_003)));
        list.add(new FontOption(FONT_ITALIC, L10n.s(R.string.ui_004)));

        File dir = new File(c.getFilesDir(), "fonts");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".TTF") || name.endsWith(".OTF")) {
                    String label = name;
                    int dot = label.lastIndexOf('.');
                    if (dot > 0) label = label.substring(0, dot);
                    if (label.length() > 10) label = label.substring(0, 10) + "…";
                    list.add(new FontOption("file:" + name, label));
                }
            }
        }
        return list;
    }

    public static Typeface resolve(Context c, String fontId) {
        if (fontId == null || fontId.isEmpty()) fontId = FONT_SKETCH;
        Typeface cached = CACHE.get(fontId);
        if (cached != null) return cached;

        if (fontId.startsWith("file:")) {
            String fileName = fontId.substring(5);
            File f = new File(new File(c.getFilesDir(), "fonts"), fileName);
            if (f.exists()) {
                try {
                    Typeface tf = Typeface.createFromFile(f);
                    if (tf != null) {
                        CACHE.put(fontId, tf);
                        return tf;
                    }
                } catch (Exception ignored) {}
            }
        }

        Typeface tf;
        switch (fontId) {
            case FONT_SERIF:
                tf = AppFonts.regular();
                break;
            case FONT_MONO:
                tf = AppFonts.regular();
                break;
            case FONT_ITALIC:
                tf = Typeface.create(AppFonts.regular(), Typeface.ITALIC);
                break;
            case FONT_SKETCH:
            default:
                tf = Typeface.create(AppFonts.regular(), Typeface.ITALIC);
                break;
        }
        CACHE.put(fontId, tf);
        return tf;
    }

    /** 从本地 Uri 导入 .ttf/.otf 字体文件,成功返回字体 ID ("file:xxx.ttf"),失败返回 null */
    public static String importFont(Context c, Uri uri) {
        try {
            String displayName = queryName(c, uri);
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "custom_" + System.currentTimeMillis() + ".ttf";
            }
            displayName = displayName.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
            String lower = displayName.toLowerCase();
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
                displayName = displayName + ".ttf";
            }

            File dir = new File(c.getFilesDir(), "fonts");
            if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, displayName);
            try (InputStream in = c.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dst)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            // 校验是否能被 Android Typeface 正常解析
            Typeface tf = Typeface.createFromFile(dst);
            if (tf == null || tf == AppFonts.regular()) {
                dst.delete();
                return null;
            }
            String id = "file:" + dst.getName();
            CACHE.put(id, tf);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    private static String queryName(Context c, Uri uri) {
        try (Cursor cursor = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
