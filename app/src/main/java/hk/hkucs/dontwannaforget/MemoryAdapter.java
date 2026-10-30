package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Typeface;
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
        } else if (filter == Record.TICKET || filter == Record.VOICE || filter == Record.BOOK || filter == Record.MUSIC || filter == Record.FOOTPRINT) {
            // 模仿 example/layoutexp3.jpg、IMG_3860.jpg、bookexp1/2.jpg 与 IMG_3870.jpg: 采用错落穿插的散落式拼贴布局
            if (random) {
                Collections.shuffle(list, new Random(seed));
            }
            int i = 0;
            int step = 0;
            while (i < list.size()) {
                // 交替使用「不对称双卡错落行」与「单卡留白+暗底编号行」，还原散落式节奏
                if ((filter == Record.FOOTPRINT || step % 2 == 0) && i + 1 < list.size()) {
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
     * 模仿 example/layoutexp3.jpg、声音页、书籍便签页与音乐歌词页的散落式拼贴布局:
     * - 打破齐整两列网格，采用安全边距、高低错位、微倾旋转与暗底编号留白点缀
     */
    private void bindPair(VH h, Context c, Record[] pair, int pos) {
        int available=availableWidthDp(h,c);
        int pairWidth=Math.max(80,(available-58)/2);
        FrameLayout stage = new FrameLayout(c);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        stage.setPadding(0, Cards.dp(c, 10), 0, Cards.dp(c, 14));

        Record r0 = pair[0];
        Record r1 = pair.length > 1 ? pair[1] : null;
        Random rnd = new Random((random ? seed : 17L) * 31 + pos * 97L + (r0 != null ? r0.title.hashCode() : 0));

        if (r1 == null && r0 != null) {
            // 单张主卡片错落行
            boolean alignLeft = (pos % 3 != 1);
            int cardW = (r0.type == Record.VOICE) ? 236
                    : r0.type == Record.FOOTPRINT ? Math.min(180,pairWidth)
                    : (r0.type == Record.BOOK || r0.type == Record.MUSIC) ? 242
                    : (coverMode ? 210 : 256);
            cardW=Math.min(cardW,available-48);
            View card = buildScatteredCard(c, r0, cardW, false);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            lp.gravity = alignLeft ? (Gravity.START | Gravity.TOP) : (Gravity.END | Gravity.TOP);
            lp.topMargin = Cards.dp(c, 18);
            if (alignLeft) {
                lp.leftMargin = Cards.dp(c, 24);
            } else {
                lp.rightMargin = Cards.dp(c, 24);
            }
            card.setRotation((rnd.nextFloat() * 4.4f - 2.2f));
            card.setOnClickListener(v -> onOpen.open(r0));
            stage.addView(card, lp);

            // 在对侧暗色背景空白处点缀微缩编号与日期
            String serial;
            if (r0.type == Record.VOICE) {
                serial = "VOICE #" + String.format(java.util.Locale.US, "%04d", Math.abs(r0.title.hashCode()) % 9000 + 1000);
            } else if (r0.type == Record.BOOK) {
                serial = BookNoteStyle.fromRecord(r0).serialCode;
            } else if (r0.type == Record.MUSIC) {
                serial = MusicCardStyle.fromRecord(r0).serialCode;
            } else {
                serial = TicketStyle.defaultSerial(r0);
            }
            TextView idTag = Cards.text(c, serial, 8.5f, 0xFF9C8E4E, AppFonts.regular());
            idTag.setRotation(-4f);
            FrameLayout.LayoutParams idLp = new FrameLayout.LayoutParams(-2, -2,
                    alignLeft ? (Gravity.END | Gravity.TOP) : (Gravity.START | Gravity.TOP));
            idLp.topMargin = Cards.dp(c, 4);
            if (alignLeft) idLp.rightMargin = Cards.dp(c, 28);
            else idLp.leftMargin = Cards.dp(c, 32);
            stage.addView(idTag, idLp);

        } else if (r0 != null && r1 != null) {
            // 不对称双卡错落行
            int pattern = pos % 3;

            // 左卡: 可轻微向左出血或偏下错位
            int w0 = (r0.type == Record.VOICE || r0.type == Record.BOOK || r0.type == Record.MUSIC)
                    ? (pattern == 0 ? 186 : 176)
                    : (coverMode ? 168 : (pattern == 0 ? 196 : 184));
            w0=Math.min(w0,pairWidth);
            View card0 = buildScatteredCard(c, r0, w0, true);
            FrameLayout.LayoutParams lp0 = (FrameLayout.LayoutParams) card0.getLayoutParams();
            lp0.gravity = Gravity.START | Gravity.TOP;
            lp0.leftMargin = Cards.dp(c, 20);
            lp0.topMargin = Cards.dp(c, pattern == 1 ? 30 : 8);
            card0.setRotation(pattern == 0 ? -1.4f : (rnd.nextFloat() * 4.8f - 2.6f));
            card0.setOnClickListener(v -> onOpen.open(r0));
            stage.addView(card0, lp0);

            // 右卡: 与左卡形成高低差与倾角对比
            int w1 = (r1.type == Record.VOICE || r1.type == Record.BOOK || r1.type == Record.MUSIC)
                    ? (pattern == 1 ? 178 : 184)
                    : (coverMode ? 168 : (pattern == 1 ? 186 : 194));
            w1=Math.min(w1,pairWidth);
            View card1 = buildScatteredCard(c, r1, w1, true);
            FrameLayout.LayoutParams lp1 = (FrameLayout.LayoutParams) card1.getLayoutParams();
            lp1.gravity = Gravity.END | Gravity.TOP;
            lp1.rightMargin = Cards.dp(c, 20);
            lp1.topMargin = Cards.dp(c, pattern == 1 ? 8 : 30);
            card1.setRotation(pattern == 1 ? -2.4f : (1.2f + rnd.nextFloat() * 1.2f));
            card1.setOnClickListener(v -> onOpen.open(r1));
            stage.addView(card1, lp1);

            // 背景暗处点缀微小日期与 ID
            TextView bgDate = Cards.text(c, r0.date.replace('.', '-'), 8f, 0xFF6E6B64, AppFonts.regular());
            bgDate.setRotation(-5f);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            dlp.topMargin = Cards.dp(c, 2);
            dlp.leftMargin = Cards.dp(c, 18);
            stage.addView(bgDate, dlp);
        }

        h.box.addView(stage, new FrameLayout.LayoutParams(-1, -2));
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

        TextView week = Cards.text(c, parts[1], 12, 0xFFB5AFA6, AppFonts.regular());
        week.setPadding(0, Cards.dp(c, 2), 0, 0);
        dateCol.addView(week);

        TextView monthYear = Cards.text(c, parts[2], 12, 0xFF9E988E, AppFonts.regular());
        monthYear.setPadding(0, Cards.dp(c, 1), 0, 0);
        dateCol.addView(monthYear);

        // 日期下方的精巧点缀线与微缩展开提示
        View dash = new View(c);
        dash.setBackgroundColor(0x33F2D398);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Cards.dp(c, 24), Cards.dp(c, 1.5f));
        dlp.topMargin = Cards.dp(c, 6);
        dlp.bottomMargin = Cards.dp(c, 4);
        dateCol.addView(dash, dlp);

        TextView hint = Cards.text(c, L10n.s(R.string.ui_055), 10.5f, 0xFF7A756D, AppFonts.regular());
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

    /** 声音记忆卡(仿 example/IMG_3860.jpg: 上圆下平图片 + 简单日期与标题) */
    private View wrapVoice(Context c, Record r, int wDp) {
        return Cards.voiceCard(c, r, wDp, wDp < 200);
    }
}
