package hk.hkucs.dontwannaforget;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * “全部”页点开某一天后的两层展开画布(模仿 exa2.jpg 与 exa3.jpg):
 * - 层级 1(Overview, 仿 exa2.jpg): 顶部日期问候,中间散开展示当天的【音乐】【足迹/手记】【电影票根/书籍】三组卡片,带入场展开动画。
 * - 层级 2(Category Focus, 仿 exa3.jpg): 点开某一类后,顶部显示该类标题(如 Footprints / Movies / Music),
 *   中间错落散开该类下的所有拍立得/票根/音乐卡片;再点具体某一张卡,进入 DetailActivity 对应详情页。
 */
public class DaySpreadActivity extends AppCompatActivity {
    private String dateStr;
    private final List<Record> dayRecords = new ArrayList<>();
    private FrameLayout stage;
    private TextView tvHeader, tvSubHeader, tvBottomHint;
    private int focusedGroup = -1; // -1 = 层级1(exa2 全景展开); >=0 = 层级2(exa3 某类别展开)

    // 三个类别组: 0=音乐(MUSIC), 1=足迹与日常(FOOTPRINT/LIFE), 2=电影与书(TICKET/BOOK)
    private static final int GROUP_MUSIC = 0;
    private static final int GROUP_FOOTPRINT = 1;
    private static final int GROUP_MOVIE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        dateStr = getIntent().getStringExtra("date");
        if (dateStr == null) { finish(); return; }

        buildShell();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (focusedGroup != -1) {
                    showOverview(true);
                } else {
                    finish();
                }
            }
        });

        Repo repo = new Repo(this);
        repo.observeActive().observe(this, all -> {
            dayRecords.clear();
            if (all != null) {
                for (Record r : all) {
                    if (dateStr.equals(r.date)) dayRecords.add(r);
                }
            }
            if (dayRecords.isEmpty()) { finish(); return; }
            if (focusedGroup == -1) showOverview(true);
            else showCategoryDetail(focusedGroup);
        });
        repo.observeMedia().observe(this, list -> {
            MediaIndex.set(list);
            if (!dayRecords.isEmpty()) {
                if (focusedGroup == -1) showOverview(false);
                else showCategoryDetail(focusedGroup);
            }
        });
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.desk));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Cards.dp(this, 24);
        col.setPadding(pad, Cards.dp(this, 16), pad, Cards.dp(this, 96));

        // 顶部返回与操作栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Cards.text(this, L10n.s(R.string.ui_060), 13, getColor(R.color.ink_sub), AppFonts.regular());
        back.setOnClickListener(v -> {
            if (focusedGroup != -1) showOverview(true);
            else finish();
        });
        HomeNavigation.bind(back);
        topBar.addView(back, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView dateBadge = Cards.text(this, dateStr, 12, getColor(R.color.wine), AppFonts.regular());
        topBar.addView(dateBadge);
        col.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        // 大号标题(仿 exa2 "Good Afternoon" / exa3 "Photos")
        tvHeader = Cards.text(this, "", 22, getColor(R.color.ink),
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setPadding(0, Cards.dp(this, 18), 0, Cards.dp(this, 4));
        col.addView(tvHeader);

        tvSubHeader = Cards.text(this, "", 13, getColor(R.color.ink_sub), AppFonts.regular());
        tvSubHeader.setGravity(Gravity.CENTER);
        tvSubHeader.setPadding(0, 0, 0, Cards.dp(this, 16));
        col.addView(tvSubHeader);

        LinearLayout floatingTitle = new LinearLayout(this);
        floatingTitle.setOrientation(LinearLayout.VERTICAL);
        col.removeView(topBar);col.removeView(tvHeader);col.removeView(tvSubHeader);
        floatingTitle.addView(topBar);floatingTitle.addView(tvHeader);floatingTitle.addView(tvSubHeader);
        col.addView(floatingTitle,0);FloatingHeader.pin(floatingTitle);

        // 中央自由散落画布
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        col.addView(stage, new LinearLayout.LayoutParams(-1, Cards.dp(this, 480)));

        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        // 底部圆形收起/返回按钮(仿 exa2 / exa3 底部中央圆钮)
        FrameLayout bottomBar = new FrameLayout(this);
        bottomBar.setPadding(pad, Cards.dp(this, 12), pad, Cards.dp(this, 24));
        tvBottomHint = Cards.text(this, "✦", 20, Color.WHITE, AppFonts.regular());
        tvBottomHint.setGravity(Gravity.CENTER);
        GradientDrawable orb = new GradientDrawable();
        orb.setShape(GradientDrawable.OVAL);
        orb.setColor(getColor(R.color.wine));
        orb.setStroke(Cards.dp(this, 2), 0x55FFFFFF);
        tvBottomHint.setBackground(orb);
        tvBottomHint.setElevation(Cards.dp(this, 6));
        tvBottomHint.setOnClickListener(v -> {
            v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                if (focusedGroup != -1) showOverview(true);
                else finish();
            }).start();
        });
        bottomBar.addView(tvBottomHint, new FrameLayout.LayoutParams(
                Cards.dp(this, 48), Cards.dp(this, 48), Gravity.CENTER));
        root.addView(bottomBar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
    }

    private List<Record> recordsForGroup(int group) {
        List<Record> out = new ArrayList<>();
        for (Record r : dayRecords) {
            if (group == GROUP_MUSIC && (r.type == Record.MUSIC || r.type == Record.VOICE)) out.add(r);
            else if (group == GROUP_FOOTPRINT && (r.type == Record.FOOTPRINT || r.type == Record.LETTER)) out.add(r);
            else if (group == GROUP_MOVIE && (r.type == Record.TICKET || r.type == Record.BOOK)) out.add(r);
        }
        return out;
    }

    private void transitionStage(Runnable populate) {
        if (stage.getChildCount() == 0) {
            populate.run();
            return;
        }
        stage.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(150)
                .withEndAction(() -> {
                    populate.run();
                    stage.setScaleX(1f);
                    stage.setScaleY(1f);
                    stage.setAlpha(1f);
                })
                .start();
    }

    /**
     * 层级 1(仿 exa2.jpg):
     * 展示当天展开的三个区域——左上【音乐与声音】、右侧【足迹/信件】、左下【电影票根/书】,从中心散开动画。
     */
    private void showOverview(boolean animate) {
        focusedGroup = -1;
        tvHeader.setText(formatDayGreeting(dateStr));
        tvSubHeader.setText(L10n.s(R.string.ui_162) + dayRecords.size() + L10n.s(R.string.ui_163));
        tvBottomHint.setText("∨");

        transitionStage(() -> {
            stage.removeAllViews();
            stage.getLayoutParams().height = Cards.dp(this, 490);
            stage.requestLayout();

            List<Record> musics = recordsForGroup(GROUP_MUSIC);
            List<Record> footprints = recordsForGroup(GROUP_FOOTPRINT);
            List<Record> movies = recordsForGroup(GROUP_MOVIE);

            int slot = 0;
            // 1. 左上: 音乐与声音组
            if (!musics.isEmpty()) {
                View v = buildGroupCluster(GROUP_MUSIC, musics);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
                lp.leftMargin = Cards.dp(this, 4);
                lp.topMargin = Cards.dp(this, 8);
                stage.addView(v, lp);
                animateSpread(v, -4f, animate, slot++);
            }

            // 2. 右中: 足迹与信件组(像 exa2 右侧叠放的拍立得照片)
            if (!footprints.isEmpty()) {
                View v = buildGroupCluster(GROUP_FOOTPRINT, footprints);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
                lp.rightMargin = Cards.dp(this, 4);
                lp.topMargin = Cards.dp(this, musics.isEmpty() ? 24 : 96);
                stage.addView(v, lp);
                animateSpread(v, 5f, animate, slot++);
            }

            // 3. 左下: 电影票根 / 书籍组
            if (!movies.isEmpty()) {
                View v = buildGroupCluster(GROUP_MOVIE, movies);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
                lp.leftMargin = Cards.dp(this, 10);
                lp.bottomMargin = Cards.dp(this, 12);
                stage.addView(v, lp);
                animateSpread(v, -3f, animate, slot);
            }
        });
    }

    /** 构建 exa2 中的单个类别卡组(带类别角标与叠放效果) */
    private View buildGroupCluster(int group, List<Record> list) {
        FrameLayout cluster = new FrameLayout(this);
        cluster.setClipChildren(false);
        cluster.setClipToPadding(false);

        Record primary = list.get(0);
        // 若该组有多条,在底下垫一张微斜的副卡模拟散开叠放(如 exa2 右侧两张拍立得)
        if (list.size() > 1) {
            Record second = list.get(1);
            View backCard = createClusterCard(group, second, true);
            FrameLayout.LayoutParams blp = (FrameLayout.LayoutParams) backCard.getLayoutParams();
            blp.gravity = Gravity.CENTER;
            backCard.setRotation(-8f);
            backCard.setTranslationX(-Cards.dp(this, 14));
            backCard.setTranslationY(Cards.dp(this, 6));
            cluster.addView(backCard, blp);
        }

        View frontCard = createClusterCard(group, primary, false);
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) frontCard.getLayoutParams();
        flp.gravity = Gravity.CENTER;
        cluster.addView(frontCard, flp);

        // 顶部类别胶囊标签(音乐与声音 / 足迹 / 电影)
        String label = group == GROUP_MUSIC ? L10n.s(R.string.ui_164) + list.size()
                : group == GROUP_FOOTPRINT ? L10n.s(R.string.ui_165) + list.size()
                : L10n.s(R.string.ui_166) + list.size();
        TextView tag = Cards.text(this, label, 11, Color.WHITE, AppFonts.regular());
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(getColor(R.color.wine));
        tbg.setCornerRadius(Cards.dp(this, 12));
        tag.setBackground(tbg);
        tag.setElevation(Cards.dp(this, 8));
        tag.setPadding(Cards.dp(this, 10), Cards.dp(this, 4), Cards.dp(this, 10), Cards.dp(this, 4));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tlp.leftMargin = Cards.dp(this, 6);
        tlp.topMargin = -Cards.dp(this, 8);
        cluster.addView(tag, tlp);

        cluster.setPadding(Cards.dp(this, 12), Cards.dp(this, 12), Cards.dp(this, 12), Cards.dp(this, 12));
        cluster.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                showCategoryDetail(group);
            }).start();
        });
        return cluster;
    }

    private View createClusterCard(int group, Record r, boolean back) {
        if (group == GROUP_MUSIC) {
            if (r.type == Record.VOICE) {
                return Cards.voiceCard(this, r, back ? 160 : 176, true);
            }
            return Cards.musicCard(this, r, back ? 148 : 162, true);
        } else if (group == GROUP_FOOTPRINT) {
            if (r.type == Record.LETTER) {
                return Cards.envelopeCard(this, r, back ? 156 : 172);
            }
            return Cards.polaroidCard(this, r, back ? 148 : 164, back ? 182 : 200);
        } else {
            return r.type == Record.BOOK
                    ? Cards.bookCard(this, r, back ? 128 : 142)
                    : Cards.ticketStamp(this, r, back ? 138 : 152, back ? 192 : 212);
        }
    }

    /**
     * 层级 2(仿 exa3.jpg):
     * 点击某个类别后,展开该类别在当天的所有细节卡片(如足迹的多张拍立得、多张电影票根、多首音乐/声音磁带),
     * 再点击具体卡片进入 DetailActivity。
     */
    private void showCategoryDetail(int group) {
        focusedGroup = group;
        List<Record> list = recordsForGroup(group);
        if (list.isEmpty()) { showOverview(false); return; }

        String title = group == GROUP_MUSIC ? "Music & Voice"
                : group == GROUP_FOOTPRINT ? "Footprints & Letters" : "Movies & Books";
        String sub = group == GROUP_MUSIC ? L10n.s(R.string.ui_167)
                : group == GROUP_FOOTPRINT ? L10n.s(R.string.ui_168)
                : L10n.s(R.string.ui_169);
        tvHeader.setText(title);
        tvSubHeader.setText(sub);
        tvBottomHint.setText("‹");

        transitionStage(() -> {
            stage.removeAllViews();

            List<View> cards = new ArrayList<>();
            if (group == GROUP_FOOTPRINT) {
                for (Record r : list) {
                    final Record target = r;
                    if (r.type == Record.FOOTPRINT) {
                        int pageCount = Math.max(1, r.fields != null ? r.fields.length : 1);
                        for (int i = 0; i < pageCount; i++) {
                            View pv = Cards.footprintPaperCard(this, r, i, 182);
                            pv.setOnClickListener(v -> animateCardClick(v, target));
                            cards.add(pv);
                        }
                    } else if (r.type == Record.LETTER) {
                        View ev = Cards.envelopeCard(this, r, 196);
                        ev.setOnClickListener(v -> animateCardClick(v, target));
                        cards.add(ev);
                    } else {
                        View pv = Cards.polaroidCard(this, r, 174, 214);
                        pv.setOnClickListener(v -> animateCardClick(v, target));
                        cards.add(pv);
                    }
                }
            } else if (group == GROUP_MUSIC) {
                for (Record r : list) {
                    final Record target = r;
                    View mv = r.type == Record.VOICE
                            ? Cards.voiceCard(this, r, 210, false)
                            : Cards.musicCard(this, r, 180, false);
                    mv.setOnClickListener(v -> animateCardClick(v, target));
                    cards.add(mv);
                }
            } else {
                for (Record r : list) {
                    final Record target = r;
                    View tv = r.type == Record.BOOK
                            ? Cards.bookCard(this, r, 156)
                            : Cards.ticketStamp(this, r, 168, 234);
                    tv.setOnClickListener(v -> animateCardClick(v, target));
                    cards.add(tv);
                }
            }

            // 将卡片按 exa3.jpg 的左右交错方式散落排布在画布上
            float[] tilts = {-4f, 4.5f, -2.5f, 3.5f, -3f};
            int stepY = Cards.dp(this, 155);
            int totalH = Math.max(Cards.dp(this, 460), Cards.dp(this, 240) + (cards.size() - 1) * stepY);
            stage.getLayoutParams().height = totalH;
            stage.requestLayout();

            for (int i = 0; i < cards.size(); i++) {
                View c = cards.get(i);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) c.getLayoutParams();
                if (cards.size() == 1) {
                    lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                    lp.topMargin = Cards.dp(this, 48);
                } else {
                    boolean leftSide = (i % 2 == 0);
                    lp.gravity = (leftSide ? Gravity.START : Gravity.END) | Gravity.TOP;
                    lp.leftMargin = leftSide ? Cards.dp(this, 12) : 0;
                    lp.rightMargin = leftSide ? 0 : Cards.dp(this, 12);
                    lp.topMargin = Cards.dp(this, 12) + i * stepY;
                }
                stage.addView(c, lp);
                animateSpread(c, tilts[i % tilts.length], true, i);
            }
        });
    }

    private void animateCardClick(View v, Record r) {
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction(() -> {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            openDetail(r);
        }).start();
    }

    private void openDetail(Record r) {
        Intent i = new Intent(this, r.type == Record.LETTER ? LetterActivity.class : DetailActivity.class);
        i.putExtra("id", r.id);
        startActivity(i);
    }

    private boolean reduceMotion() {
        return Settings.Global.getFloat(getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    /** 从画布中心向外散开的弹簧展开动画 */
    private void animateSpread(View v, float targetRot, boolean animate, int index) {
        if (!animate || reduceMotion()) {
            v.setRotation(targetRot);
            return;
        }
        v.setScaleX(0.28f);
        v.setScaleY(0.28f);
        v.setAlpha(0f);
        v.setRotation(0f);
        v.setTranslationY(Cards.dp(this, 28));
        v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .rotation(targetRot)
                .translationY(0f)
                .setStartDelay(index * 75L)
                .setDuration(440)
                .setInterpolator(new OvershootInterpolator(1.22f))
                .start();
    }

    private static String formatDayGreeting(String date) {
        // 如 "2026.10.06" -> "Oct 06"
        String[] mNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        try {
            int m = Integer.parseInt(date.substring(5, 7));
            String d = date.substring(8, 10);
            return mNames[Math.max(0, Math.min(11, m - 1))] + " " + d;
        } catch (Exception e) {
            return date;
        }
    }
}
