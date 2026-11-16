package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 首页"桌面"列表。
 * - 全部(仿 exa1.jpg): 按日期聚合,左侧展示大号日期(日/星期/年月),右侧展示当天的多卡片堆叠簇,点击进入 DaySpreadActivity 展开;
 * - 票根:两列错落,支持"日期/随机"排列与"票根/封面"展示;
 * - 其他类别:单列大卡。
 */
public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.VH> {
    public interface OnOpen { void open(Record r); }
    public interface OnOpenDay { void openDay(String date); }

    private static final int HEADER = 0, SINGLE = 1, PAIR = 2, DAY_BUNDLE = 3;
    private static final float[] TILT = {-3f, 2.5f, -1.5f, 3f, -2.5f};

    private List<Record> all = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();
    private final OnOpen onOpen;
    private final OnOpenDay onOpenDay;

    private int filter = -1;
    private boolean random = false;    // 票根:随机排列
    private boolean coverMode = false; // 票根:仅封面
    private long seed = 1;

    private static class DayGroup {
        final String date;
        final List<Record> records;
        DayGroup(String date, List<Record> records) {
            this.date = date;
            this.records = records;
        }
    }

    public MemoryAdapter(OnOpen onOpen, OnOpenDay onOpenDay) {
        this.onOpen = onOpen;
        this.onOpenDay = onOpenDay;
    }

    /** 数据库内容变化时整体替换 */
    public void setData(List<Record> data) {
        all = data == null ? new ArrayList<>() : data;
        rebuild();
    }

    public void setFilter(int type) { filter = type; rebuild(); }

    public void setTicketArrange(boolean random) {
        this.random = random;
        if (random) seed = System.nanoTime(); // 每次点"随机"重新洗牌
        rebuild();
    }

    public void setTicketCover(boolean cover) { coverMode = cover; rebuild(); }

    private static String monthLabel(Record r) {
        return r.date.substring(0, 4) + " · " + r.date.substring(5, 7);
    }

    private void rebuild() {
        rows.clear();
        List<Record> list = new ArrayList<>();
        for (Record r : all) if (filter == -1 || r.type == filter) list.add(r);

        if (filter == -1) {
            // 按日聚合(保持日期倒序)
            Map<String, List<Record>> byDate = new LinkedHashMap<>();
            for (Record r : list) {
                List<Record> bucket = byDate.get(r.date);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    byDate.put(r.date, bucket);
                }
                bucket.add(r);
            }
            for (Map.Entry<String, List<Record>> e : byDate.entrySet()) {
                rows.add(new DayGroup(e.getKey(), e.getValue()));
            }
        } else if (filter == Record.TICKET || filter == Record.VOICE || filter == Record.BOOK || filter == Record.MUSIC || filter == Record.FOOTPRINT || filter == Record.LETTER) {
            // 模仿 example/layoutexp3.jpg、IMG_3860.jpg、bookexp1/2.jpg 与 IMG_3870.jpg: 采用错落穿插的散落式拼贴布局
            if (random) {
                Collections.shuffle(list, new Random(seed));
            }
            int i = 0;
            int step = 0;
            while (i < list.size()) {
                // 所有分类展陈页统一采用「大画幅单卡特写 + 斜角双卡画廊」交替节奏 (1 -> 2 -> 1 -> 2)
                if (step % 2 == 1 && i + 1 < list.size()) {
                    rows.add(new Record[]{list.get(i), list.get(i + 1)});
                    i += 2;
                } else {
                    rows.add(new Record[]{list.get(i), null});
                    i += 1;
                }
                step++;
            }
        } else {
            String month = "";
            for (Record r : list) {
                if (!monthLabel(r).equals(month)) {
                    month = monthLabel(r);
                    rows.add(month);
                }
                rows.add(r);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int pos) {
        Object o = rows.get(pos);
        if (o instanceof String) return HEADER;
        if (o instanceof Record[]) return PAIR;
        if (o instanceof DayGroup) return DAY_BUNDLE;
        return SINGLE;
    }

    @Override public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final FrameLayout box;
        VH(FrameLayout v) { super(v); box = v; }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout f = new FrameLayout(parent.getContext());
        f.setClipChildren(false);
        f.setClipToPadding(false);
        f.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        return new VH(f);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Context c = h.box.getContext();
        h.box.removeAllViews();
        h.box.setPadding(0, 0, 0, 0);
        Object row = rows.get(pos);
        if (row instanceof String) {
            TextView t = Cards.text(c, (String) row, 12, 0xFFB5AFA6, AppFonts.regular());
            t.setLetterSpacing(0.15f);
            h.box.setPadding(Cards.dp(c, 28), Cards.dp(c, 28), 0, Cards.dp(c, 8));
            h.box.addView(t);
        } else if (row instanceof Record[]) {
            bindPair(h, c, (Record[]) row, pos);
        } else if (row instanceof DayGroup) {
            bindDayBundle(h, c, (DayGroup) row);
        } else {
            bindSingle(h, c, (Record) row, pos);
        }

        // 瀑布流优雅淡入与微上移动效 (Staggered Entrance Animation)
        h.box.setAlpha(0f);
        h.box.setTranslationY(Cards.dp(c, 14));
        h.box.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(340)
                .setStartDelay(Math.min(pos * 28L, 220L))
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }


    private View buildScatteredCard(Context c, Record r, int wDp, boolean compact) {
        if (r.type == Record.FOOTPRINT) {
            return Cards.footprintPaperCard(c, r, 0, wDp);
        }
        if (r.type == Record.VOICE) {
            return Cards.voiceCard(c, r, wDp, compact);
        }
        if (r.type == Record.BOOK) {
            return Cards.bookCard(c, r, wDp, compact);
        }
        if (r.type == Record.MUSIC) {
            return Cards.musicCard(c, r, wDp, compact);
        }
        return Cards.ticketCard(c, r, wDp, coverMode, compact);
    }

    /**
     * 全分类统一画廊展陈引擎：
     * - 单卡特写行：大画幅主展品（多页手账/信封自带叠放质感）+ 对侧档案角标 + 统一美术馆题签；
     * - 双卡斜角行：42dp 对角线高低错位 + 对向微倾角 + 简洁题签。
     */
    private void bindPair(VH h, Context c, Record[] pair, int pos) {
        int available = availableWidthDp(h, c);
        int pairWidth = Math.max(80, (available - 56) / 2);
        FrameLayout stage = new FrameLayout(c);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        stage.setPadding(0, Cards.dp(c, 12), 0, Cards.dp(c, 20));

        Record r0 = pair[0];
        Record r1 = pair.length > 1 ? pair[1] : null;
        Random rnd = new Random((random ? seed : 17L) * 31 + pos * 97L + (r0 != null ? r0.title.hashCode() : 0));

        if (r1 == null && r0 != null) {
            boolean alignLeft = ((pos / 2) % 2 == 0);
            int cardW = soloWidthDp(r0, available);
            float tilt = alignLeft ? (-1.2f - rnd.nextFloat() * 0.8f) : (1.2f + rnd.nextFloat() * 0.8f);
            View exhibit = buildExhibitItem(c, r0, cardW, false, tilt, !alignLeft, true);
            exhibit.setOnClickListener(v -> onOpen.open(r0));

            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | (alignLeft ? Gravity.START : Gravity.END));
            slp.topMargin = Cards.dp(c, 22);
            if (alignLeft) slp.leftMargin = Cards.dp(c, 22);
            else slp.rightMargin = Cards.dp(c, 22);
            stage.addView(exhibit, slp);

            View stamp = buildCornerStamp(c, r0, pos, alignLeft);
            FrameLayout.LayoutParams stLp = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | (alignLeft ? Gravity.END : Gravity.START));
            stLp.topMargin = Cards.dp(c, 4);
            if (alignLeft) stLp.rightMargin = Cards.dp(c, 26);
            else stLp.leftMargin = Cards.dp(c, 26);
            stage.addView(stamp, stLp);
        } else if (r0 != null && r1 != null) {
            int w0 = Math.min(172, pairWidth);
            View leftItem = buildExhibitItem(c, r0, w0, true, -2.5f + (rnd.nextFloat() - 0.5f) * 0.8f, false, false);
            leftItem.setOnClickListener(v -> onOpen.open(r0));
            FrameLayout.LayoutParams lp0 = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP);
            lp0.leftMargin = Cards.dp(c, 20);
            lp0.topMargin = Cards.dp(c, 4);
            stage.addView(leftItem, lp0);

            int w1 = Math.min(172, pairWidth);
            View rightItem = buildExhibitItem(c, r1, w1, true, 2.2f + (rnd.nextFloat() - 0.5f) * 0.8f, true, false);
            rightItem.setOnClickListener(v -> onOpen.open(r1));
            FrameLayout.LayoutParams lp1 = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP);
            lp1.rightMargin = Cards.dp(c, 20);
            lp1.topMargin = Cards.dp(c, 46);
            stage.addView(rightItem, lp1);
        }

        h.box.addView(stage, new FrameLayout.LayoutParams(-1, -2));
    }

    private int soloWidthDp(Record r, int available) {
        int preferred;
        switch (r.type) {
            case Record.TICKET:
                preferred = coverMode ? 210 : 244;
                break;
            case Record.VOICE:
                preferred = 232;
                break;
            case Record.BOOK:
                preferred = 234;
                break;
            case Record.MUSIC:
                preferred = 236;
                break;
            case Record.FOOTPRINT:
                preferred = 228;
                break;
            case Record.LETTER:
            default:
                preferred = 244;
                break;
        }
        return Math.min(preferred, available - 64);
    }

    private View buildExhibitItem(Context c, Record r, int wDp, boolean compact,
                                  float tiltDeg, boolean alignEnd, boolean large) {
        View card;
        if (r.type == Record.FOOTPRINT) {
            card = buildLayeredFootprintPaper(c, r, wDp, tiltDeg);
        } else if (r.type == Record.LETTER) {
            card = buildLayeredEnvelope(c, r, wDp, tiltDeg);
        } else {
            card = buildScatteredCard(c, r, wDp, compact);
            card.setRotation(tiltDeg);
        }

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.addView(card);

        if (r.type == Record.VOICE) {
            // 留声卡已自带金色细线+日期+标题；特写行补充一行静谧地点签名
            if (large && r.sub != null && !r.sub.trim().isEmpty()) {
                TextView place = Cards.text(c, r.sub.trim(), 11.5f, 0xFF9E988E, AppFonts.regular());
                place.setIncludeFontPadding(false);
                place.setSingleLine(true);
                place.setEllipsize(TextUtils.TruncateAt.END);
                place.setGravity(alignEnd ? Gravity.END : Gravity.START);
                place.setPadding(Cards.dp(c, 2), Cards.dp(c, 4), Cards.dp(c, 2), 0);
                col.addView(place, new LinearLayout.LayoutParams(Cards.dp(c, wDp), -2));
            }
            return col;
        }

        String subText = exhibitSubText(r, large);
        int subMaxLines = (large && (r.type == Record.BOOK || r.type == Record.MUSIC || r.type == Record.LETTER)) ? 2 : 1;
        View caption = buildEditorialCaption(c, r.date, r.title, subText,
                Cards.dp(c, wDp), alignEnd, large, subMaxLines);
        ((LinearLayout.LayoutParams) caption.getLayoutParams()).topMargin = Cards.dp(c, large ? 12 : 10);
        col.addView(caption);
        return col;
    }

    private static String exhibitSubText(Record r, boolean large) {
        if (r.type == Record.LETTER) {
            if (large) return extractLetterSnippet(r.note);
            return (r.sub != null && !r.sub.trim().isEmpty()) ? ("To " + r.sub.trim()) : "";
        }
        if (r.type == Record.BOOK) {
            if (large) {
                String thought = BookNoteStyle.fromRecord(r).thought;
                if (thought != null && !thought.trim().isEmpty()) return thought.trim();
            }
            return r.sub != null ? r.sub.trim() : "";
        }
        if (r.type == Record.MUSIC) {
            if (large) {
                String thought = MusicCardStyle.fromRecord(r).thought;
                if (thought != null && !thought.trim().isEmpty()) return thought.trim();
            }
            return r.sub != null ? r.sub.trim() : "";
        }
        if (r.type == Record.TICKET) {
            String dir = r.sub != null ? r.sub.trim() : "";
            String cinema = (large && r.fields != null && r.fields.length > 0 && r.fields[0] != null)
                    ? r.fields[0].trim() : "";
            if (!dir.isEmpty() && !cinema.isEmpty()) return dir + " · " + cinema;
            return !dir.isEmpty() ? dir : cinema;
        }
        return r.sub != null ? r.sub.trim() : "";
    }

    private View buildCornerStamp(Context c, Record r, int pos, boolean alignLeft) {
        LinearLayout stamp = new LinearLayout(c);
        stamp.setOrientation(LinearLayout.VERTICAL);
        stamp.setGravity(alignLeft ? Gravity.END : Gravity.START);

        String primary;
        String secondary = "";
        switch (r.type) {
            case Record.VOICE:
                primary = "VOICE #" + String.format(Locale.US, "%04d", Math.abs(r.title.hashCode()) % 9000 + 1000);
                if (r.fields != null && r.fields.length > 0 && r.fields[0] != null) secondary = r.fields[0].trim();
                break;
            case Record.BOOK:
                primary = BookNoteStyle.fromRecord(r).serialCode;
                if (r.sub != null) secondary = r.sub.trim();
                break;
            case Record.MUSIC:
                primary = MusicCardStyle.fromRecord(r).serialCode;
                if (r.sub != null) secondary = r.sub.trim();
                break;
            case Record.FOOTPRINT:
                primary = String.format(Locale.US, "NO. %02d", pos + 1);
                int pages = Math.max(1, r.fields != null ? r.fields.length : 1);
                if (pages > 1) secondary = pages + "P";
                break;
            case Record.LETTER:
                primary = String.format(Locale.US, "LETTER · %02d", pos + 1);
                if (r.sub != null && !r.sub.trim().isEmpty()) secondary = "TO " + r.sub.trim();
                break;
            case Record.TICKET:
            default:
                primary = TicketStyle.defaultSerial(r);
                if (r.rating != null && !r.rating.trim().isEmpty()) secondary = "★ " + r.rating.trim();
                break;
        }

        TextView tag = Cards.text(c, primary, 8.5f, 0xFFAE9E62, AppFonts.regular());
        tag.setIncludeFontPadding(false);
        tag.setLetterSpacing(0.12f);
        stamp.addView(tag);

        if (!secondary.isEmpty()) {
            TextView sub = Cards.text(c, secondary, 9f, 0xFF969086, AppFonts.regular());
            sub.setIncludeFontPadding(false);
            sub.setLetterSpacing(0.06f);
            sub.setSingleLine(true);
            sub.setMaxWidth(Cards.dp(c, 84));
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setPadding(0, Cards.dp(c, 3), 0, 0);
            stamp.addView(sub);
        }
        return stamp;
    }

    /**
     * 全部页(仿 exa1.jpg):
     * 左侧大号日期(日 + 星期 + 月年),右侧是当天记录叠放在一起的卡片簇;点击展开到 DaySpreadActivity。
     */
    private void bindDayBundle(VH h, Context c, DayGroup g) {
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setClipChildren(false);
        line.setClipToPadding(false);
        line.setPadding(Cards.dp(c, 24), Cards.dp(c, 18), Cards.dp(c, 20), Cards.dp(c, 18));

        // 左侧日期栏: 大号日 + 星期 + 月份年份
        LinearLayout dateCol = new LinearLayout(c);
        dateCol.setOrientation(LinearLayout.VERTICAL);
        String[] parts = parseDateParts(g.date);
        TextView dayNum = Cards.text(c, parts[0], 34, 0xFFF2EFE9,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        dateCol.addView(dayNum);

        TextView week = Cards.text(c, parts[1], 12, 0xFFBEB8AE, AppFonts.regular());
        week.setPadding(0, Cards.dp(c, 2), 0, 0);
        dateCol.addView(week);

        TextView monthYear = Cards.text(c, parts[2], 12, 0xFFA8A296, AppFonts.regular());
        monthYear.setPadding(0, Cards.dp(c, 1), 0, 0);
        dateCol.addView(monthYear);

        // 日期下方的精巧点缀线与微缩展开提示
        View dash = new View(c);
        dash.setBackgroundColor(0x4DF2D398);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Cards.dp(c, 24), Cards.dp(c, 1.5f));
        dlp.topMargin = Cards.dp(c, 6);
        dlp.bottomMargin = Cards.dp(c, 4);
        dateCol.addView(dash, dlp);

        TextView hint = Cards.text(c, L10n.s(R.string.ui_055), 10.5f, 0xFF969086, AppFonts.regular());
        dateCol.addView(hint);

        line.addView(dateCol, new LinearLayout.LayoutParams(Cards.dp(c, 92), -2));

        // 右侧当天卡片堆叠簇
        FrameLayout rightWrap = new FrameLayout(c);
        rightWrap.setClipChildren(false);
        rightWrap.setClipToPadding(false);
        View stack = Cards.dayBundleStack(c, g.records);
        FrameLayout.LayoutParams slp = (FrameLayout.LayoutParams) stack.getLayoutParams();
        slp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        int stackWidth=Cards.dp(c,Math.max(80,availableWidthDp(h,c)-92-44));
        float scale=Math.min(1f,stackWidth/(float)slp.width);
        stack.setPivotX(0);stack.setPivotY(0);stack.setScaleX(scale);stack.setScaleY(scale);
        slp.gravity=Gravity.TOP|Gravity.START;
        rightWrap.addView(stack, slp);
        line.addView(rightWrap, new LinearLayout.LayoutParams(0, Math.round(slp.height*scale), 1f));

        // 触感弹性回弹与点击过渡动效 (Tactile Spring Click Animation)
        line.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.85f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                if (onOpenDay != null) onOpenDay.openDay(g.date);
            }).start();
        });
        h.box.addView(line, new FrameLayout.LayoutParams(-1, -2));
    }

    private static String[] parseDateParts(String dateStr) {
        // 输入 "yyyy.MM.dd" -> {"24", "Saturday", "Oct 2026"}
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy.MM.dd", Locale.US);
            Date d = inFmt.parse(dateStr);
            if (d != null) {
                String day = new SimpleDateFormat("d", Locale.US).format(d);
                String week = new SimpleDateFormat("EEEE", Locale.US).format(d);
                String my = new SimpleDateFormat("MMM yyyy", Locale.US).format(d);
                return new String[]{day, week, my};
            }
        } catch (Exception ignored) {}
        String day = dateStr.length() >= 10 ? dateStr.substring(8, 10) : dateStr;
        String ym = dateStr.length() >= 7 ? dateStr.substring(0, 7) : "";
        return new String[]{day, "", ym};
    }

    // ---- 单类别:单列大卡 ----
    private void bindSingle(VH h, Context c, Record r, int pos) {
        h.box.setPadding(Cards.dp(c, 24), Cards.dp(c, 20), Cards.dp(c, 24), Cards.dp(c, 20));
        View card = r.type==Record.LETTER ? wrapEnvelope(c,r,Math.min(296,availableWidthDp(h,c)-48)) : card(c, r, 0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        h.box.addView(card, lp);
        if (r.type == Record.FOOTPRINT) card.setRotation(TILT[pos % TILT.length] * 0.5f);
        card.setOnClickListener(v -> onOpen.open(r));
    }

    private int availableWidthDp(VH holder,Context context) {
        View parent=(View)holder.box.getParent();
        int width=parent!=null?parent.getWidth():0;
        if(width<=0)width=context.getResources().getDisplayMetrics().widthPixels;
        return Math.round(width/context.getResources().getDisplayMetrics().density);
    }

    /** style 0 = 大卡, 1 = 紧凑 */
    private View card(Context c, Record r, int style) {
        boolean s = style == 1;
        switch (r.type) {
            case Record.TICKET:
                return s ? Cards.ticketStamp(c, r, 120, 168) : Cards.ticketStamp(c, r, 200, 280);
            case Record.MUSIC:
                return Cards.musicCard(c, r, s ? 150 : 250, s);
            case Record.BOOK:
                return Cards.bookCard(c, r, s ? 176 : 246, s);
            case Record.VOICE:
                return wrapVoice(c, r, s ? 184 : 238);
            case Record.FOOTPRINT:
                return Cards.footprintPaperCard(c, r, 0, s ? 180 : 276);
            case Record.LETTER:
                return wrapEnvelope(c, r, s ? 190 : 296);
            default:
                return Cards.noteStamp(c, r, s ? 170 : 230);
        }
    }

    /** 信封卡(写信/便签类): 顶部显示具体月日与信件标题,下方展示邮票信封 */
    private View wrapEnvelope(Context c, Record r, int wDp) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        String dayTag = (r.date.length() >= 10 ? r.date.substring(5) : r.date)
                + "  ·  " + r.title;
        TextView meta = Cards.text(c, dayTag, 12, 0xFFB5AFA6, AppFonts.regular());
        meta.setPadding(Cards.dp(c, 4), 0, Cards.dp(c, 4), Cards.dp(c, 8));
        col.addView(meta);

        col.addView(Cards.envelopeCard(c, r, wDp));
        col.setLayoutParams(new FrameLayout.LayoutParams(Cards.dp(c, wDp), -2));
        return col;
    }

    private View buildLayeredFootprintPaper(Context c, Record r, int wDp, float tiltDeg) {
        FootprintCanvasView paper = Cards.footprintPaperCard(c, r, 0, wDp);
        int pageCount = Math.max(1, r.fields != null ? r.fields.length : 1);
        if (pageCount <= 1) {
            paper.setRotation(tiltDeg);
            return paper;
        }
        ViewGroup.LayoutParams pLp = paper.getLayoutParams();
        int wPx = pLp.width;
        int hPx = pLp.height;

        FrameLayout stack = new FrameLayout(c);
        stack.setClipChildren(false);
        stack.setClipToPadding(false);

        View underSheet = new View(c);
        GradientDrawable ubg = new GradientDrawable();
        ubg.setColor(0xFFE4DFD3);
        ubg.setCornerRadius(Cards.dp(c, 12f * wDp / 340f));
        ubg.setStroke(Cards.dp(c, 1), 0x22000000);
        underSheet.setBackground(ubg);
        underSheet.setElevation(Cards.dp(c, 2));
        underSheet.setRotation(tiltDeg + (tiltDeg < 0 ? 4.6f : -4.6f));
        underSheet.setTranslationX(Cards.dp(c, tiltDeg < 0 ? 6 : -6));
        underSheet.setTranslationY(Cards.dp(c, 5));
        stack.addView(underSheet, new FrameLayout.LayoutParams(wPx, hPx));

        paper.setElevation(Cards.dp(c, 6));
        paper.setRotation(tiltDeg);
        stack.addView(paper, new FrameLayout.LayoutParams(wPx, hPx));
        stack.setLayoutParams(new LinearLayout.LayoutParams(wPx, hPx));
        return stack;
    }

    private View buildLayeredEnvelope(Context c, Record r, int wDp, float tiltDeg) {
        EnvelopeView ev = (EnvelopeView) Cards.envelopeCard(c, r, wDp);
        ViewGroup.LayoutParams lp = ev.getLayoutParams();
        int wPx = lp.width;
        int hPx = lp.height;

        FrameLayout stack = new FrameLayout(c);
        stack.setClipChildren(false);
        stack.setClipToPadding(false);

        // 信封背后微微露出的一角信纸，强化书信实体叠放质感
        View letterPeek = new View(c);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xFFF1ECE0);
        pbg.setCornerRadius(Cards.dp(c, 4));
        pbg.setStroke(Cards.dp(c, 1), 0x22000000);
        letterPeek.setBackground(pbg);
        letterPeek.setElevation(Cards.dp(c, 2));
        letterPeek.setRotation(tiltDeg + (tiltDeg < 0 ? 4.2f : -4.2f));
        letterPeek.setTranslationX(Cards.dp(c, tiltDeg < 0 ? 5 : -5));
        letterPeek.setTranslationY(-Cards.dp(c, 5));
        stack.addView(letterPeek, new FrameLayout.LayoutParams(wPx, hPx));

        ev.setElevation(Cards.dp(c, 6));
        ev.setRotation(tiltDeg);
        stack.addView(ev, new FrameLayout.LayoutParams(wPx, hPx));
        stack.setLayoutParams(new LinearLayout.LayoutParams(wPx, hPx));
        return stack;
    }

    private static String extractLetterSnippet(String note) {
        if (note == null) return "";
        String[] lines = note.replace("\r", "").split("\n");
        StringBuilder out = new StringBuilder();
        boolean skippedGreeting = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (!skippedGreeting && out.length() == 0 && line.length() <= 24
                    && (line.endsWith(",") || line.endsWith("，") || line.endsWith(":") || line.endsWith("："))) {
                skippedGreeting = true;
                continue;
            }
            if (out.length() > 0) out.append(" ");
            out.append(line);
            if (out.length() >= 72) break;
        }
        return out.length() > 0 ? out.toString() : note.trim();
    }

    private View buildEditorialCaption(Context c, String dateText, String titleText, String subText,
                                       int widthPx, boolean alignEnd, boolean large, int subMaxLines) {
        LinearLayout cap = new LinearLayout(c);
        cap.setOrientation(LinearLayout.VERTICAL);
        cap.setGravity(alignEnd ? Gravity.END : Gravity.START);

        LinearLayout metaRow = new LinearLayout(c);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL | (alignEnd ? Gravity.END : Gravity.START));
        metaRow.setPadding(0, 0, 0, Cards.dp(c, 4));

        View rule = new View(c);
        rule.setBackgroundColor(0x77D8C690);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Cards.dp(c, 14), Cards.dp(c, 1.2f));
        TextView date = Cards.text(c, dateText != null ? dateText : "", large ? 10.5f : 9.5f, 0xFFA8A296, AppFonts.regular());
        date.setIncludeFontPadding(false);
        date.setLetterSpacing(0.08f);
        if (alignEnd) {
            rlp.leftMargin = Cards.dp(c, 7);
            metaRow.addView(date);
            metaRow.addView(rule, rlp);
        } else {
            rlp.rightMargin = Cards.dp(c, 7);
            metaRow.addView(rule, rlp);
            metaRow.addView(date);
        }
        cap.addView(metaRow, new LinearLayout.LayoutParams(-1, -2));

        TextView title = Cards.text(c, titleText != null ? titleText : "", large ? 15.5f : 13.5f,
                0xFFF2EFE9, Typeface.create(AppFonts.regular(), Typeface.BOLD));
        title.setIncludeFontPadding(false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(alignEnd ? Gravity.END : Gravity.START);
        cap.addView(title, new LinearLayout.LayoutParams(-1, -2));

        if (subText != null && !subText.trim().isEmpty()) {
            TextView sub = Cards.text(c, subText.trim(), large ? 11.5f : 10.5f, 0xFF9E988E, AppFonts.regular());
            sub.setIncludeFontPadding(false);
            sub.setMaxLines(subMaxLines);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            if (subMaxLines > 1) sub.setLineSpacing(0f, 1.35f);
            sub.setPadding(0, Cards.dp(c, 3), 0, 0);
            sub.setGravity(alignEnd ? Gravity.END : Gravity.START);
            cap.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        }

        cap.setLayoutParams(new LinearLayout.LayoutParams(widthPx, -2));
        return cap;
    }

    /** 声音记忆卡(仿 example/IMG_3860.jpg: 上圆下平图片 + 简单日期与标题) */
    private View wrapVoice(Context c, Record r, int wDp) {
        return Cards.voiceCard(c, r, wDp, wDp < 200);
    }
}
