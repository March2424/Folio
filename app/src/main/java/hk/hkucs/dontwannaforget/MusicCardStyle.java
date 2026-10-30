package hk.hkucs.dontwannaforget;

import org.json.JSONObject;

/**
 * 音乐歌词卡片 (Record.MUSIC) 的视觉样式配置与附加字段。
 * 序列化保存在 Record.fields[0] 的 JSON 字符串中，兼容旧版无 fields 的示例记录。
 *
 * 模仿 example/IMG_3870.jpg:
 * - 背景采用上传的图片并进行高斯/柔焦模糊 + 暗调压暗遮罩；
 * - 左上角展示清晰圆角方形小封面 + 歌名（白粗体）+ 歌手名（浅灰白）；
 * - 中部大号歌词排版，支持切换内置字体与本地导入自定义字体 (.ttf / .otf)；
 * - 移除底部 Spotify 标志，保持纯粹的音乐记忆卡片质感。
 */
public class MusicCardStyle {

    // 歌词字形样式 (Text Weight / Style)
    public static final int WEIGHT_BOLD_ITALIC = 0; // 粗斜体 (仿 IMG_3870.jpg 标志性斜体粗字)
    public static final int WEIGHT_BOLD = 1;        // 醒目粗体
    public static final int WEIGHT_NORMAL = 2;      // 文艺常规体

    public static String[] WEIGHT_NAMES() { return new String[] {
            L10n.s(R.string.ui_522),
            L10n.s(R.string.ui_523),
            L10n.s(R.string.ui_524)
    }; }

    // 背景图片模糊程度 (Blur Level)
    public static final int BLUR_SUBTLE = 0;   // 轻度柔焦模糊 (仿 IMG_3870.jpg 隐约可见背景画面细节)
    public static final int BLUR_MEDIUM = 1;   // 中度氛围模糊
    public static final int BLUR_DEEP = 2;     // 深度朦胧模糊
    public static final int BLUR_CLEAR = 3;    // 清晰原图压暗

    public static String[] BLUR_NAMES() { return new String[] {
            L10n.s(R.string.ui_525),
            L10n.s(R.string.ui_526),
            L10n.s(R.string.ui_527),
            L10n.s(R.string.ui_528)
    }; }

    public String fontId = FontManager.FONT_SERIF;
    public int textWeight = WEIGHT_BOLD_ITALIC;
    public float lyricSizeSp = 21.5f;
    public int blurLevel = BLUR_SUBTLE;
    public String thought = ""; // 听歌时的心情随记（在详情页卡片下方展示）
    public String serialCode = "MUSIC #0516";

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("fid", fontId != null ? fontId : FontManager.FONT_SERIF);
            o.put("tw", textWeight);
            o.put("lsz", lyricSizeSp);
            o.put("bl", blurLevel);
            o.put("th", thought != null ? thought : "");
            o.put("sc", serialCode != null ? serialCode : "MUSIC #0516");
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static MusicCardStyle fromRecord(Record r) {
        if (r != null && r.fields != null) {
            for (String f : r.fields) {
                if (f != null && f.trim().startsWith("{")) {
                    try {
                        JSONObject o = new JSONObject(f.trim());
                        MusicCardStyle s = new MusicCardStyle();
                        s.fontId = o.optString("fid", FontManager.FONT_SERIF);
                        s.textWeight = Math.max(0, Math.min(2, o.optInt("tw", WEIGHT_BOLD_ITALIC)));
                        s.lyricSizeSp = (float) o.optDouble("lsz", 21.5);
                        s.blurLevel = Math.max(0, Math.min(3, o.optInt("bl", BLUR_SUBTLE)));
                        s.thought = o.optString("th", "");
                        s.serialCode = o.optString("sc", defaultSerial(r));
                        return s;
                    } catch (Exception ignored) {}
                }
            }
        }

        MusicCardStyle s = new MusicCardStyle();
        s.textWeight = WEIGHT_BOLD_ITALIC;
        s.blurLevel = BLUR_SUBTLE;
        s.serialCode = defaultSerial(r);
        return s;
    }

    public static String defaultSerial(Record r) {
        int code = r != null && r.id != null ? (Math.abs(r.id.hashCode()) % 9000 + 1000) : 5160;
        return String.format(java.util.Locale.US, "MUSIC #%04d", code);
    }
}
