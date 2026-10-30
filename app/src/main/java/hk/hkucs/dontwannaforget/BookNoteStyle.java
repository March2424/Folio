package hk.hkucs.dontwannaforget;

import org.json.JSONObject;

/**
 * 书籍摘抄便签 (Record.BOOK) 的样式配置与附加字段。
 * 序列化保存在 Record.fields[0] 的 JSON 字符串中，兼容旧版仅存 "cover" 或空数组的记录。
 *
 * 模仿 example/bookexp1.jpg 与 example/bookexp2.jpg:
 * - 外部列表默认展示仿便签样式的文字（摘抄句子/想法），最底下展示书名；
 * - 支持 6 种典型便签器物样式（和纸胶带、活页打孔+金色回形针、黄铜圆图钉+卷角、金色燕尾夹+手撕棉纸、拍立得相框便签、牛皮手撕/麻绳吊牌）；
 * - 样式在编辑器中选择；详情页通过统一编辑入口修改。
 */
public class BookNoteStyle {

    // 6 种便签器物样式 (对应 bookexp1.jpg 与 bookexp2.jpg 中的各款便签)
    public static final int STYLE_WASHI_SQUARE = 0;    // 金砂和纸胶带方笺 (仿 bookexp1.jpg & bookexp2 左上)
    public static final int STYLE_GRID_PAPERCLIP = 1;  // 活页打孔方格纸 + 金色回形针 (仿 bookexp2 左中)
    public static final int STYLE_PIN_CURLED = 2;      // 黄铜圆头图钉 + 右下卷角便签 (仿 bookexp2 左下中)
    public static final int STYLE_BINDER_TORN = 3;     // 金色燕尾长尾夹 + 四周手撕棉纸 (仿 bookexp2 右下)
    public static final int STYLE_POLAROID_TAPE = 4;   // 胶带宽边相框便签 (仿 bookexp2 右上)
    public static final int STYLE_KRAFT_TAG = 5;       // 复古牛皮手撕签 / 麻绳打孔签 (仿 bookexp2 左下/右中)

    public static String[] STYLE_NAMES() { return new String[] {
            L10n.s(R.string.ui_586),
            L10n.s(R.string.ui_587),
            L10n.s(R.string.ui_588),
            L10n.s(R.string.ui_589),
            L10n.s(R.string.ui_590),
            L10n.s(R.string.ui_591)
    }; }

    // 5 种纸张底色与材质 (Paper Tone)
    public static final int TONE_RICE_WHITE = 0;   // 宣白手账纸 (仿 bookexp1.jpg 纯净微暖宣白)
    public static final int TONE_WARM_CREAM = 1;   // 暖米古籍纸 (仿 bookexp2.jpg 泛黄暖米色)
    public static final int TONE_KRAFT_BROWN = 2;  // 复古牛皮纸 (仿 bookexp2.jpg 左下暖棕牛皮纸)
    public static final int TONE_MIST_GREY = 3;    // 冷雾灰白纸
    public static final int TONE_INK_DARK = 4;     // 暗夜墨黑纸

    public static String[] PAPER_TONE_NAMES() { return new String[] {
            L10n.s(R.string.ui_592),
            L10n.s(R.string.ui_593),
            L10n.s(R.string.ui_594),
            L10n.s(R.string.ui_595),
            L10n.s(R.string.ui_596)
    }; }

    public int noteStyle = STYLE_WASHI_SQUARE;
    public int paperTone = TONE_RICE_WHITE;
    public String fontId = FontManager.FONT_SKETCH;
    public float textSizeSp = 15.5f;
    public boolean centerAlign = true;
    public String thought = "";              // 用户补充的个人想法/读书随感
    public boolean showThoughtOnCard = false; // 是否将补充想法一并在便签正面展示
    public String serialCode = "BOOK #0108";

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("ns", noteStyle);
            o.put("pt", paperTone);
            o.put("fid", fontId != null ? fontId : FontManager.FONT_SKETCH);
            o.put("tsz", textSizeSp);
            o.put("ca", centerAlign);
            o.put("th", thought != null ? thought : "");
            o.put("stc", showThoughtOnCard);
            o.put("sc", serialCode != null ? serialCode : "BOOK #0108");
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static BookNoteStyle fromRecord(Record r) {
        if (r != null && r.fields != null) {
            for (String f : r.fields) {
                if (f != null && f.trim().startsWith("{")) {
                    try {
                        JSONObject o = new JSONObject(f.trim());
                        BookNoteStyle s = new BookNoteStyle();
                        s.noteStyle = Math.max(0, Math.min(5, o.optInt("ns", STYLE_WASHI_SQUARE)));
                        s.paperTone = Math.max(0, Math.min(4, o.optInt("pt", TONE_RICE_WHITE)));
                        s.fontId = o.optString("fid", FontManager.FONT_SKETCH);
                        s.textSizeSp = (float) o.optDouble("tsz", 15.5);
                        s.centerAlign = o.optBoolean("ca", true);
                        s.thought = o.optString("th", "");
                        s.showThoughtOnCard = o.optBoolean("stc", false);
                        s.serialCode = o.optString("sc", defaultSerial(r));
                        return s;
                    } catch (Exception ignored) {}
                }
            }
        }

        // 为未配置 JSON 的示例或旧版书籍记录分配多样化的 bookexp1/bookexp2 便签样式
        BookNoteStyle s = new BookNoteStyle();
        int h = r != null && r.title != null ? Math.abs(r.title.hashCode()) : 0;
        s.noteStyle = h % 6;
        if (s.noteStyle == STYLE_KRAFT_TAG) {
            s.paperTone = TONE_KRAFT_BROWN;
        } else if (s.noteStyle == STYLE_PIN_CURLED || s.noteStyle == STYLE_POLAROID_TAPE) {
            s.paperTone = TONE_WARM_CREAM;
        } else {
            s.paperTone = TONE_RICE_WHITE;
        }
        s.centerAlign = (s.noteStyle != STYLE_GRID_PAPERCLIP);
        s.serialCode = defaultSerial(r);
        return s;
    }

    public static String defaultSerial(Record r) {
        int code = r != null && r.id != null ? (Math.abs(r.id.hashCode()) % 9000 + 1000) : 1084;
        return String.format(java.util.Locale.US, "BOOK #%04d", code);
    }

    /** 获取便签正面上展示的主文本（优先展示摘抄句子，若为空则展示个人想法；若勾选同步则合并展示） */
    public String resolveCardDisplayText(Record r) {
        String quote = (r != null && r.note != null) ? r.note.trim() : "";
        String myThought = (thought != null) ? thought.trim() : "";
        if (!quote.isEmpty() && !myThought.isEmpty() && showThoughtOnCard) {
            return quote + "\n\n— " + myThought;
        }
        if (!quote.isEmpty()) return quote;
        if (!myThought.isEmpty()) return myThought;
        return L10n.s(R.string.ui_597);
    }
}
