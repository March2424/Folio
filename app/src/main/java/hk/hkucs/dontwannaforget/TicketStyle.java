package hk.hkucs.dontwannaforget;

import org.json.JSONObject;

/**
 * 电影票根(Record.TICKET)的正反面排版与纸张边缘样式配置。
 * 保存在 Record.fields[6] 的 JSON 字符串中，兼容旧版仅 6 个字段的示例数据。
 */
public class TicketStyle {
    // 正面呈现模式 (Front Mode)
    public static final int FRONT_TEXT_NOTE = 0;        // 模仿 layoutexp3.jpg 的便签文字排版(标题与正文字体/大小不同)
    public static final int FRONT_POSTER_PORTRAIT = 1;  // 竖排电影海报
    public static final int FRONT_POSTER_LANDSCAPE = 2; // 横排电影海报/剧照

    public static String[] FRONT_MODE_NAMES() { return new String[] {
            L10n.s(R.string.ui_438), L10n.s(R.string.ui_439), L10n.s(R.string.ui_440)
    }; }

    // 便签文字排版的内部版式 (对应 layoutexp3.jpg 中的 4 种典型便签构图)
    public static final int LAYOUT_CENTER_INSERT = 0; // 居中大标题穿插式 (仿「筆記 / 陽台 / 詞彙」)
    public static final int LAYOUT_RIGHT_OVERLAP = 1; // 右侧交叠大标题式 (仿「麻雀 / 弓箭」)
    public static final int LAYOUT_TOP_TILTED = 2;    // 顶部大标题斜签式 (仿「親愛的男孩」)
    public static final int LAYOUT_BANNER_STRIP = 3;  // 琥珀窄幅横签式 (仿「About」)

    public static String[] NOTE_LAYOUT_NAMES() { return new String[] {
            L10n.s(R.string.ui_441),
            L10n.s(R.string.ui_442),
            L10n.s(R.string.ui_443),
            L10n.s(R.string.ui_444)
    }; }

    // 便签容器边框纹路 (Edge Style)
    public static final int EDGE_RANDOM = 0;     // 随机齿痕/不规则撕裂
    public static final int EDGE_TEETH = 1;      // 细密齿痕边框 (邮票/票据锯齿)
    public static final int EDGE_TORN_SIDE = 2;  // 侧边不规则手撕纹路 (仿「弓箭/About」)
    public static final int EDGE_TORN_ALL = 3;   // 四周不规则撕裂毛边
    public static final int EDGE_CLEAN_TAPE = 4; // 复古平切便签 + NEW 贴纸 (仿「麻雀」)

    public static String[] EDGE_STYLE_NAMES() { return new String[] {
            L10n.s(R.string.ui_445), L10n.s(R.string.ui_446), L10n.s(R.string.ui_447), L10n.s(R.string.ui_448), L10n.s(R.string.ui_449)
    }; }

    // 便签纸张底色 (Paper Tone)
    public static final int TONE_MIST_GREY = 0;  // 冷雾灰白便签 (layoutexp3 主色调)
    public static final int TONE_CHARCOAL = 1;   // 暗夜炭黑半透便签 (右上「親愛的男孩」)
    public static final int TONE_OCHRE = 2;      // 复古姜黄便签 (「About」)
    public static final int TONE_WARM_CREAM = 3; // 暖米白纸签

    public static String[] PAPER_TONE_NAMES() { return new String[] {
            L10n.s(R.string.ui_450), L10n.s(R.string.ui_451), L10n.s(R.string.ui_452), L10n.s(R.string.ui_453)
    }; }

    // 背面电影院票根模版 (Back Cinema Ticket Template)
    public static final int TPL_THERMAL_RED = 0;    // 经典红白热敏电影票
    public static final int TPL_BROADWAY_RETRO = 1; // 百老汇复古胶片票根
    public static final int TPL_IMAX_DARK = 2;      // IMAX 黑金珍藏票根
    public static final int TPL_ART_HOUSE = 3;      // 艺术影展手写便签票
    public static final int TPL_FULL_SCAN = 4;      // 扫描票根原件全幅展示

    public static String[] TICKET_TEMPLATE_NAMES() { return new String[] {
            L10n.s(R.string.ui_454), L10n.s(R.string.ui_455), L10n.s(R.string.ui_456), L10n.s(R.string.ui_457), L10n.s(R.string.ui_458)
    }; }

    public int frontMode = FRONT_TEXT_NOTE;
    public int noteLayout = LAYOUT_CENTER_INSERT;
    public int edgeStyle = EDGE_RANDOM;
    public int paperTone = TONE_MIST_GREY;
    public int ticketTemplate = TPL_THERMAL_RED;
    public boolean scanEnhance = true; // 票根照片是否启用高对比扫描滤镜
    public boolean showNewBadge = false;
    public String titleFontId = FontManager.FONT_SERIF;
    public String bodyFontId = FontManager.FONT_SERIF;
    public float titleSizeSp = 28f;
    public float bodySizeSp = 11.5f;
    public String serialCode = "ID #004234";

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("fm", frontMode);
            o.put("nl", noteLayout);
            o.put("es", edgeStyle);
            o.put("pt", paperTone);
            o.put("tt", ticketTemplate);
            o.put("se", scanEnhance);
            o.put("nb", showNewBadge);
            o.put("tf", titleFontId != null ? titleFontId : FontManager.FONT_SERIF);
            o.put("bf", bodyFontId != null ? bodyFontId : FontManager.FONT_SERIF);
            o.put("tsz", titleSizeSp);
            o.put("bsz", bodySizeSp);
            o.put("sc", serialCode != null ? serialCode : "ID #004234");
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static TicketStyle fromRecord(Record r) {
        if (r != null && r.fields != null && r.fields.length > 6 && r.fields[6] != null && r.fields[6].trim().startsWith("{")) {
            try {
                JSONObject o = new JSONObject(r.fields[6]);
                TicketStyle s = new TicketStyle();
                s.frontMode = o.optInt("fm", FRONT_TEXT_NOTE);
                s.noteLayout = o.optInt("nl", LAYOUT_CENTER_INSERT);
                s.edgeStyle = o.optInt("es", EDGE_RANDOM);
                s.paperTone = o.optInt("pt", TONE_MIST_GREY);
                s.ticketTemplate = o.optInt("tt", TPL_THERMAL_RED);
                s.scanEnhance = o.optBoolean("se", true);
                s.showNewBadge = o.optBoolean("nb", false);
                s.titleFontId = o.optString("tf", FontManager.FONT_SERIF);
                s.bodyFontId = o.optString("bf", FontManager.FONT_SERIF);
                s.titleSizeSp = (float) o.optDouble("tsz", 28.0);
                s.bodySizeSp = (float) o.optDouble("bsz", 11.5);
                s.serialCode = o.optString("sc", defaultSerial(r));
                return s;
            } catch (Exception ignored) {}
        }

        // 为未设置 fields[6] 的示例/旧记录自动生成还原 layoutexp3.jpg 的多样化便签样式
        TicketStyle s = new TicketStyle();
        int h = r != null && r.title != null ? Math.abs(r.title.hashCode()) : 0;
        boolean hasCover = r != null && MediaIndex.first(r.id, Media.COVER) != null;
        if (hasCover) {
            s.frontMode = (h % 2 == 0) ? FRONT_POSTER_PORTRAIT : FRONT_POSTER_LANDSCAPE;
        } else {
            s.frontMode = FRONT_TEXT_NOTE;
        }
        int variant = h % 5;
        if (variant == 0) {
            s.noteLayout = LAYOUT_CENTER_INSERT;
            s.paperTone = TONE_MIST_GREY;
            s.edgeStyle = EDGE_CLEAN_TAPE;
            s.showNewBadge = false;
        } else if (variant == 1) {
            s.noteLayout = LAYOUT_RIGHT_OVERLAP;
            s.paperTone = TONE_MIST_GREY;
            s.edgeStyle = EDGE_CLEAN_TAPE;
            s.showNewBadge = true;
        } else if (variant == 2) {
            s.noteLayout = LAYOUT_TOP_TILTED;
            s.paperTone = TONE_CHARCOAL;
            s.edgeStyle = EDGE_TEETH;
            s.showNewBadge = false;
        } else if (variant == 3) {
            s.noteLayout = LAYOUT_RIGHT_OVERLAP;
            s.paperTone = TONE_MIST_GREY;
            s.edgeStyle = EDGE_TORN_SIDE;
            s.showNewBadge = false;
        } else {
            s.noteLayout = LAYOUT_BANNER_STRIP;
            s.paperTone = TONE_OCHRE;
            s.edgeStyle = EDGE_TORN_SIDE;
            s.showNewBadge = false;
        }
        s.ticketTemplate = h % 4;
        s.serialCode = defaultSerial(r);
        return s;
    }

    public static String defaultSerial(Record r) {
        int code = r != null && r.id != null ? (Math.abs(r.id.hashCode()) % 900000 + 100000) : 4234;
        return String.format(java.util.Locale.US, "ID #%06d", code);
    }

    /** 解析最终生效的边缘样式 (若为 EDGE_RANDOM 则按种子固定决定一种齿痕或撕裂纹路) */
    public int resolvedEdgeStyle(int seed) {
        if (edgeStyle != EDGE_RANDOM) return edgeStyle;
        int pick = Math.abs(seed) % 3;
        if (pick == 0) return EDGE_TEETH;
        if (pick == 1) return EDGE_TORN_SIDE;
        return EDGE_TORN_ALL;
    }
}
