package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.graphics.DashPathEffect;

/** 代码构建各种卡片视图的工具,M0 原型阶段保持集中,便于一起调样式。 */
public final class Cards {
    private Cards() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    public static TextView badge(Context c, String rating) {
        TextView b = text(c, rating, 14, Color.WHITE, Typeface.create(AppFonts.regular(), Typeface.BOLD));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 14));
        g.setColor(c.getColor(R.color.wine));
        b.setBackground(g);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 4));
        return b;
    }

    /** 电影封面(假数据用渐变+片名代替海报) */
    public static FrameLayout poster(Context c, Record r, boolean big) {
        return poster(c, r, big ? 34 : 24);
    }

    public static FrameLayout poster(Context c, Record r, float titleSp) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{r.c1, r.c2});
        g.setCornerRadius(dp(c, 2));
        f.setBackground(g);

        Media cover = MediaIndex.first(r.id, Media.COVER);
        if (cover != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            ImageLoader.into(iv, cover, 720);
        } else {
            TextView title = text(c, r.title, titleSp, Color.WHITE,
                    Typeface.create(AppFonts.regular(), Typeface.BOLD));
            title.setGravity(Gravity.CENTER);
            title.setPadding(dp(c, 12), 0, dp(c, 12), dp(c, 28));
            f.addView(title, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(c, 12), dp(c, 24), dp(c, 12), dp(c, 12));
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x99000000});
        bottom.setBackground(shade);
        TextView date = text(c, r.date, 12, 0xFFF3EEE4, AppFonts.regular());
        bottom.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));
        if (r.rating != null) bottom.addView(badge(c, r.rating));
        f.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return f;
    }

    /** 电影便签/海报与票根双面卡(模仿 layoutexp3.jpg 齿痕/手撕便签与横竖海报,背面为影院票根) */
    public static TicketCardView ticketCard(Context c, Record r, int wDp, boolean showBack, boolean compact) {
        TicketCardView v = new TicketCardView(c);
        v.setElevation(dp(c, 5));
        TicketStyle st = TicketStyle.fromRecord(r);
        v.bind(r, st, showBack, compact);
        int wPx = dp(c, wDp);
        int hPx = Math.round(wPx * v.computeHeightRatio());
        v.setLayoutParams(new FrameLayout.LayoutParams(wPx, hPx));
        return v;
    }

    /** 邮票/便签卡:电影票根的首页形态 */
    public static View ticketStamp(Context c, Record r, int wDp, int hDp) {
        return ticketCard(c, r, wDp, false, wDp < 160);
    }

    /** 背面票根展示 */
    public static View coverOnly(Context c, Record r, int wDp, int hDp) {
        return ticketCard(c, r, wDp, true, wDp < 160);
    }

    /** 音乐歌词卡片 (模仿 example/IMG_3870.jpg: 模糊背景图 + 左上清晰小封面与曲名歌手 + 大号自定义字体歌词，无底部 Spotify 标志) */
    public static MusicLyricCardView musicCard(Context c, Record r, int wDp, boolean compact) {
        MusicLyricCardView v = new MusicLyricCardView(c);
        v.setElevation(dp(c, 5));
        MusicCardStyle st = MusicCardStyle.fromRecord(r);
        v.bind(r, st, compact);
        int wPx = dp(c, wDp);
        int hPx = Math.round(wPx * v.computeHeightRatio());
        v.setLayoutParams(new FrameLayout.LayoutParams(wPx, hPx));
        return v;
    }

    /** 书籍摘抄便签卡 (模仿 bookexp1.jpg 与 bookexp2.jpg: 默认展示摘抄句子/想法，最底下文字为书名) */
    public static BookNoteView bookCard(Context c, Record r, int wDp, boolean compact) {
        BookNoteView v = new BookNoteView(c);
        v.setElevation(dp(c, 5));
        BookNoteStyle st = BookNoteStyle.fromRecord(r);
        v.bind(r, st, compact);
        int wPx = dp(c, wDp);
        int hPx = Math.round(wPx * v.computeHeightRatio());
        v.setLayoutParams(new FrameLayout.LayoutParams(wPx, hPx));
        return v;
    }

    public static View bookCard(Context c, Record r, int wDp) {
        return bookCard(c, r, wDp, wDp < 190);
    }

    /** 书签/邮戳卡:书与足迹沿用票根语言 */
    public static View noteStamp(Context c, Record r, int wDp) {
        boolean book = r.type == Record.BOOK;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(new StampDrawable(dp(c, 4), dp(c, 3), 0));
        int p = dp(c, 20);
        col.setPadding(p, p, p, p + dp(c, 3));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = text(c, book ? "BOOK" : "FOOTPRINT", 10,
                c.getColor(R.color.wine), AppFonts.regular());
        tag.setLetterSpacing(0.2f);
        head.addView(tag, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(text(c, r.date.substring(5), 11, c.getColor(R.color.ink_sub), AppFonts.regular()));
        col.addView(head);

        View band = new View(c);
        band.setBackgroundColor(c.getColor(R.color.wine));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 2));
        bl.topMargin = dp(c, 10);
        bl.bottomMargin = dp(c, 12);
        col.addView(band, bl);

        col.addView(text(c, r.title, 20, c.getColor(R.color.ink),
                Typeface.create(AppFonts.regular(), Typeface.BOLD)));
        TextView sub = text(c, r.sub, 12, c.getColor(R.color.ink_sub), AppFonts.regular());
        sub.setPadding(0, dp(c, 4), 0, dp(c, 10));
        col.addView(sub);
        TextView quote = text(c, r.note, 13, c.getColor(R.color.ink), AppFonts.regular());
        quote.setLineSpacing(0, 1.4f);
        col.addView(quote);

        col.setLayoutParams(new FrameLayout.LayoutParams(dp(c, wDp), -2));
        return col;
    }

    /** 足迹纸质图文画布卡(只读展示态,还原 editexp1.jpg 纸质背景+手绘框+自定义位置文字) */
    public static FootprintCanvasView footprintPaperCard(Context c, Record r, int pageIdx, int wDp) {
        FootprintCanvasView cv = new FootprintCanvasView(c);
        cv.setEditable(false);
        cv.setElevation(dp(c, 5));
        cv.setFallbackColors(r.c1, r.c2);
        String raw = (r.fields != null && pageIdx < r.fields.length) ? r.fields[pageIdx] : "";
        cv.setState(FootprintCanvasView.State.fromField(raw, r, pageIdx));
        java.util.List<Media> photos = MediaIndex.list(r.id, Media.PHOTO);
        if (pageIdx < photos.size()) {
            cv.setPhotoMedia(photos.get(pageIdx));
        } else if (!photos.isEmpty()) {
            cv.setPhotoMedia(photos.get(0));
        }
        int wPx = dp(c, wDp);
        int hPx = Math.round(wPx * 1.26f);
        cv.setLayoutParams(new FrameLayout.LayoutParams(wPx, hPx));
        return cv;
    }

    /** 声音记忆外部卡片(模仿 IMG_3860.jpg: 上圆角照片 + 右下胶囊角标 + 下方浅灰日期与白色粗体标题) */
    public static VoiceTapeView voiceCard(Context c, Record r, int wDp, boolean compact) {
        VoiceTapeView v = new VoiceTapeView(c);
        v.setElevation(dp(c, 5));
        v.bind(r, compact);
        int wPx = dp(c, wDp);
        int hPx = Math.round(wPx * (compact ? 1.15f : 1.18f));
        v.setLayoutParams(new FrameLayout.LayoutParams(wPx, hPx));
        return v;
    }

    /** 复古信封卡(对应 letterexp1.jpg, 展示收件人 To:、邮票、邮戳与日期) */
    public static EnvelopeView envelopeCard(Context c, Record r, int wDp) {
        EnvelopeView ev = new EnvelopeView(c);
        ev.setElevation(dp(c, 5));
        int theme = 0;
        if (r.fields != null && r.fields.length > 0) {
            try { theme = Integer.parseInt(r.fields[0]); } catch (Exception ignored) {}
        }
        String to = (r.sub != null && !r.sub.isEmpty()) ? r.sub : r.title;
        ev.bind(to, r.date, theme);
        int wPx = dp(c, wDp);
        int hPx = Math.round(wPx * 0.70f);
        ev.setLayoutParams(new FrameLayout.LayoutParams(wPx, hPx));
        return ev;
    }

    /** 拍立得风格照片卡:白边相框 + 照片(或渐变) + 底部手写感地点/标题 */
    public static View polaroidCard(Context c, Record r, int wDp, int hDp) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFCFAF5);
        bg.setCornerRadius(dp(c, 3));
        bg.setStroke(dp(c, 1), 0x22000000);
        card.setBackground(bg);
        card.setElevation(dp(c, 5));
        int p = dp(c, wDp < 130 ? 6 : 10);
        int bottomPad = dp(c, wDp < 130 ? 18 : 28);
        card.setPadding(p, p, p, 0);

        FrameLayout photo = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{r.c1, r.c2});
        photo.setBackground(g);
        Media m = MediaIndex.first(r.id, Media.PHOTO);
        if (m == null) m = MediaIndex.first(r.id, Media.COVER);
        if (m != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            ImageLoader.into(iv, m, 720);
        } else {
            TextView t = text(c, r.title, wDp < 130 ? 13 : 18, 0xEEFFFFFF,
                    Typeface.create(AppFonts.regular(), Typeface.BOLD));
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(c, 6), 0, dp(c, 6), 0);
            photo.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
        int photoH = dp(c, hDp) - p - bottomPad;
        card.addView(photo, new LinearLayout.LayoutParams(-1, Math.max(dp(c, 40), photoH)));

        String cap = !r.title.isEmpty() ? r.title : (!r.sub.isEmpty() ? r.sub : r.date);
        TextView caption = text(c, cap, wDp < 130 ? 10 : 13, c.getColor(R.color.ink),
                Typeface.create(AppFonts.regular(), Typeface.ITALIC));
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        card.addView(caption, new LinearLayout.LayoutParams(-1, bottomPad));

        card.setLayoutParams(new FrameLayout.LayoutParams(dp(c, wDp), dp(c, hDp)));
        return card;
    }

    /** 便签风格小卡(用于按日堆叠簇底层,对应参考图中的 Note / 胶带卡片) */
    public static View miniNoteCard(Context c, Record r, int wDp, int hDp, boolean warmHeader) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFCFAF5);
        bg.setCornerRadius(dp(c, 8));
        bg.setStroke(dp(c, 1), 0x22000000);
        card.setBackground(bg);
        card.setElevation(dp(c, 3));

        TextView head = text(c, warmHeader ? "Music" : "Note", 9,
                warmHeader ? Color.WHITE : c.getColor(R.color.ink), AppFonts.regular());
        head.setGravity(Gravity.CENTER);
        GradientDrawable hbg = new GradientDrawable();
        hbg.setColor(warmHeader ? c.getColor(R.color.wine) : 0xFFEFE397);
        float rTop = dp(c, 8);
        hbg.setCornerRadii(new float[]{rTop, rTop, rTop, rTop, 0, 0, 0, 0});
        head.setBackground(hbg);
        head.setPadding(0, dp(c, 3), 0, dp(c, 3));
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        TextView body = text(c, r.note.isEmpty() ? r.title : r.note, 9, c.getColor(R.color.ink), AppFonts.regular());
        body.setMaxLines(4);
        body.setLineSpacing(0, 1.25f);
        int p = dp(c, 8);
        body.setPadding(p, p, p, p);
        card.addView(body, new LinearLayout.LayoutParams(-1, -1));

        card.setLayoutParams(new FrameLayout.LayoutParams(dp(c, wDp), dp(c, hDp)));
        return card;
    }

    /**
     * 模仿 exa1.jpg 的“按日卡片堆叠簇”:
     * 多分类穿插打磨：智能挑选不同类别(票根/声音/书摘/音乐/手账/信封)错落叠放，
     * 自适应 1 张单卡特写、2 张错落不对称、3+ 张扇形簇拥，带有层次分明的真实桌面光影与微倾角度。
     */
    public static View dayBundleStack(Context c, java.util.List<Record> dayRecords) {
        FrameLayout stack = new FrameLayout(c);
        stack.setClipChildren(false);
        stack.setClipToPadding(false);

        if (dayRecords == null || dayRecords.isEmpty()) {
            stack.setLayoutParams(new FrameLayout.LayoutParams(dp(c, 236), dp(c, 168)));
            return stack;
        }

        // 1. 多分类智能挑选：优先展示当天不同类别的记忆卡片，避免重复
        java.util.List<Record> distinctPicks = new java.util.ArrayList<>();
        for (Record r : dayRecords) {
            if (r.type == Record.TICKET || r.type == Record.FOOTPRINT || r.type == Record.LETTER || r.type == Record.BOOK) {
                distinctPicks.add(r);
                break;
            }
        }
        if (distinctPicks.isEmpty()) distinctPicks.add(dayRecords.get(0));

        for (Record r : dayRecords) {
            if (distinctPicks.size() >= 3) break;
            boolean typeExists = false;
            for (Record p : distinctPicks) {
                if (p.type == r.type) { typeExists = true; break; }
            }
            if (!typeExists) distinctPicks.add(r);
        }
        for (Record r : dayRecords) {
            if (distinctPicks.size() >= 3) break;
            if (!distinctPicks.contains(r)) distinctPicks.add(r);
        }

        int count = distinctPicks.size();

        if (count == 1) {
            // 单张卡片：居中精致展示，自然微倾，展现该分类专属细节
            Record r = distinctPicks.get(0);
            View card = buildDayStackCard(c, r, 138, false);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            lp.gravity = Gravity.CENTER;
            card.setRotation(-1.8f);
            card.setElevation(dp(c, 6));
            stack.addView(card, lp);
        } else if (count == 2) {
            // 双卡错落：左后方微斜垫底，右前方主卡突显，形成强烈透视节奏
            Record r0 = distinctPicks.get(1); // 左后
            Record r1 = distinctPicks.get(0); // 右前

            View leftCard = buildDayStackCard(c, r0, 108, true);
            FrameLayout.LayoutParams lp0 = (FrameLayout.LayoutParams) leftCard.getLayoutParams();
            lp0.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            lp0.leftMargin = dp(c, 12);
            leftCard.setRotation(-7.5f);
            leftCard.setElevation(dp(c, 3));
            stack.addView(leftCard, lp0);

            View rightCard = buildDayStackCard(c, r1, 126, false);
            FrameLayout.LayoutParams lp1 = (FrameLayout.LayoutParams) rightCard.getLayoutParams();
            lp1.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            lp1.rightMargin = dp(c, 10);
            rightCard.setRotation(3.5f);
            rightCard.setElevation(dp(c, 8));
            stack.addView(rightCard, lp1);
        } else {
            // 三卡及以上：左翼(-8.5°)、右翼(+7.5°)、中央主卡(+1.5°)，构建层次丰富的桌面簇拥感
            Record leftRec = distinctPicks.get(1);
            Record rightRec = distinctPicks.get(2);
            Record centerRec = distinctPicks.get(0);

            View leftCard = buildDayStackCard(c, leftRec, 100, true);
            FrameLayout.LayoutParams lpLeft = (FrameLayout.LayoutParams) leftCard.getLayoutParams();
            lpLeft.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            lpLeft.leftMargin = dp(c, 4);
            leftCard.setRotation(-8.5f);
            leftCard.setElevation(dp(c, 2));
            stack.addView(leftCard, lpLeft);

            View rightCard = buildDayStackCard(c, rightRec, 104, true);
            FrameLayout.LayoutParams lpRight = (FrameLayout.LayoutParams) rightCard.getLayoutParams();
            lpRight.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            lpRight.rightMargin = dp(c, 6);
            rightCard.setRotation(7.5f);
            rightCard.setElevation(dp(c, 4));
            stack.addView(rightCard, lpRight);

            View centerCard = buildDayStackCard(c, centerRec, 126, false);
            FrameLayout.LayoutParams lpCenter = (FrameLayout.LayoutParams) centerCard.getLayoutParams();
            lpCenter.gravity = Gravity.CENTER;
            centerCard.setRotation(1.5f);
            centerCard.setElevation(dp(c, 10));
            stack.addView(centerCard, lpCenter);
        }

        // 当日记录总数徽标 (多于 1 条时显示)
        if (dayRecords.size() > 1) {
            TextView countBadge = text(c, dayRecords.size() + L10n.s(R.string.ui_120), 9.5f, 0xFFF2D398, AppFonts.regular());
            GradientDrawable cbg = new GradientDrawable();
            cbg.setColor(0xE61E1A17);
            cbg.setCornerRadius(dp(c, 10));
            cbg.setStroke(dp(c, 1), 0x55F2D398);
            countBadge.setBackground(cbg);
            countBadge.setElevation(dp(c, 12));
            countBadge.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3.5f));
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            clp.rightMargin = dp(c, 8);
            clp.bottomMargin = dp(c, 2);
            stack.addView(countBadge, clp);
        }

        stack.setLayoutParams(new FrameLayout.LayoutParams(dp(c, 236), dp(c, 168)));
        return stack;
    }

    /** 按真实视觉类型构建微缩堆叠卡片 */
    public static View buildDayStackCard(Context c, Record r, int wDp, boolean compact) {
        if (r.type == Record.TICKET) {
            return ticketStamp(c, r, wDp, Math.round(wDp * 1.36f));
        }
        if (r.type == Record.BOOK) {
            return bookCard(c, r, wDp, compact);
        }
        if (r.type == Record.MUSIC) {
            return musicCard(c, r, wDp, compact);
        }
        if (r.type == Record.VOICE) {
            return voiceCard(c, r, wDp, true);
        }
        if (r.type == Record.LETTER) {
            return envelopeCard(c, r, wDp);
        }
        if (r.type == Record.FOOTPRINT) {
            return polaroidCard(c, r, wDp, Math.round(wDp * 1.25f));
        }
        return miniNoteCard(c, r, wDp, Math.round(wDp * 1.25f), compact);
    }

    /** 虚线(票根撕口分隔) */
    public static class DashedLine extends View {
        private final Paint p = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);

        public DashedLine(Context c) {
            super(c);
            p.setColor(0xFFC9C2B6);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(c, 1.2f));
            p.setPathEffect(new DashPathEffect(new float[]{dp(c, 5), dp(c, 4)}, 0));
        }

        @Override
        protected void onDraw(Canvas cv) {
            float y = getHeight() / 2f;
            cv.drawLine(0, y, getWidth(), y, p);
        }
    }
}
