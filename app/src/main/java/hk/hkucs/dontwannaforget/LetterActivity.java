package hk.hkucs.dontwannaforget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 信封拆启与信件阅读页 (1:1 还原 letterexp1.jpg -> letterexp2.jpg -> letterexp3.jpg):
 * - 阶段 1 (letterexp1.jpg): 展示贴有复古邮票与邮戳的信封 (To: Mira / September 14, 2026)
 * - 阶段 2 (letterexp2.jpg): 轻点信封触发顶部撕条向右卷曲撕开动画,下方浮现 "Opening your letter..."
 * - 阶段 3 (letterexp3.jpg): 展开多层叠放的米色信纸卡片,带手写感标题、引号句子暖色高光标记、右上角 × 收起按钮与底部分页箭头 (←)(→)
 */
public class LetterActivity extends AppCompatActivity {
    private static final int CHARS_PER_PAGE = 520;

    private String recordId;
    private Record record;
    private final List<String> letterPages = new ArrayList<>();
    private int currentPage = 0;
    private boolean opening = false;

    private FrameLayout rootStage;
    private LinearLayout envelopeStage, letterStage;
    private EnvelopeView envelopeView;
    private VintageLetterSheetView vintageLetterSheet;
    private TextView tvOpeningHint;
    private ImageView btnPrevPage,btnNextPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        recordId = getIntent().getStringExtra("id");
        if (recordId == null) {
            finish();
            return;
        }
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Repo(this).get(recordId, r -> runOnUiThread(() -> {
            if (r == null) {
                finish();
                return;
            }
            record = r;
            bindRecord(r);
        }));
    }

    private void buildUi() {
        rootStage = new FrameLayout(this);
        rootStage.setBackgroundColor(FloatingHeader.DETAIL_BACKGROUND);

        // 顶部轻量导航栏(返回 & 打字机重编)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Cards.dp(this, 22);
        topBar.setPadding(pad, Cards.dp(this, 14), pad, Cards.dp(this, 8));

        TextView btnBack = Cards.text(this, L10n.s(R.string.ui_060), 15, getColor(R.color.ink_sub), AppFonts.regular());
        btnBack.setOnClickListener(v -> finish());
        HomeNavigation.bind(btnBack);
        topBar.addView(btnBack, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView btnEdit = Cards.text(this, L10n.s(R.string.ui_460), 13, getColor(R.color.wine), AppFonts.regular());
        btnEdit.setOnClickListener(v -> {
            Intent i = new Intent(this, TypewriterActivity.class);
            i.putExtra("id", recordId);
            startActivity(i);
        });
        topBar.addView(btnEdit);
        rootStage.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // 阶段 1 & 2: 信封展示与撕开动画层 (letterexp1.jpg & letterexp2.jpg)
        buildEnvelopeStage();

        // 阶段 3: 展开后的多层信纸阅读层 (letterexp3.jpg)
        buildLetterStage();

        topBar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int top=b-t+Cards.dp(this,4);
            if(letterStage.getPaddingTop()!=top)letterStage.setPadding(
                    letterStage.getPaddingLeft(),top,letterStage.getPaddingRight(),letterStage.getPaddingBottom());
        });
        topBar.bringToFront();
        FloatingHeader.detailRow(topBar,btnBack,false);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(),rootStage).setAppearanceLightStatusBars(false);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(),rootStage).setAppearanceLightNavigationBars(false);
        setContentView(rootStage);
        ViewCompat.setOnApplyWindowInsetsListener(rootStage, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(rootStage);
    }

    private void buildEnvelopeStage() {
        envelopeStage = new LinearLayout(this);
        envelopeStage.setOrientation(LinearLayout.VERTICAL);
        envelopeStage.setGravity(Gravity.CENTER);
        envelopeStage.setClipChildren(false);
        envelopeStage.setClipToPadding(false);
        int pad = Cards.dp(this, 28);
        envelopeStage.setPadding(pad, Cards.dp(this, 48), pad, Cards.dp(this, 48));

        envelopeView = new EnvelopeView(this);
        envelopeView.setElevation(Cards.dp(this, 10));
        envelopeView.setOnClickListener(v -> startOpenEnvelopeAnimation());
        envelopeStage.addView(envelopeView, new LinearLayout.LayoutParams(-1, -2));

        tvOpeningHint = Cards.text(this, L10n.s(R.string.ui_461), 15, 0xFFA7A39B,
                Typeface.create(AppFonts.regular(), Typeface.ITALIC));
        tvOpeningHint.setGravity(Gravity.CENTER);
        tvOpeningHint.setPadding(0, Cards.dp(this, 36), 0, 0);
        envelopeStage.addView(tvOpeningHint);

        rootStage.addView(envelopeStage, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    }

    private void buildLetterStage() {
        letterStage = new LinearLayout(this);
        letterStage.setOrientation(LinearLayout.VERTICAL);
        letterStage.setGravity(Gravity.CENTER_HORIZONTAL);
        letterStage.setClipChildren(false);
        letterStage.setClipToPadding(false);
        int pad = Cards.dp(this, 24);
        letterStage.setPadding(pad, Cards.dp(this, 74), pad, Cards.dp(this, 16));
        letterStage.setVisibility(View.GONE);

        // Match the other pages: small, centered line icons with clear touch bounds.
        FrameLayout closeRow=new FrameLayout(this);
        ImageView btnClosePaper=buildLetterIcon("close",L10n.s(R.string.letter_fold));
        btnClosePaper.setOnClickListener(v->foldBackToEnvelope());
        FrameLayout.LayoutParams clp=new FrameLayout.LayoutParams(Cards.dp(this,40),Cards.dp(this,40),Gravity.END);
        clp.bottomMargin=Cards.dp(this,6);closeRow.addView(btnClosePaper,clp);
        letterStage.addView(closeRow,new LinearLayout.LayoutParams(-1,-2));

        // 多层叠放的米色信纸容器 (还原 letterexp3.jpg 底部错落露出的多页信纸边)
        FrameLayout paperStack = new FrameLayout(this);
        paperStack.setClipChildren(false);
        paperStack.setClipToPadding(false);

        // 底层衬纸 1
        View sheetBack2 = new View(this);
        GradientDrawable sb2 = new GradientDrawable();
        sb2.setColor(0xFFEAE4D7);
        sb2.setStroke(Cards.dp(this, 1), 0x1A000000);
        sheetBack2.setBackground(sb2);
        sheetBack2.setRotation(-1.3f);
        sheetBack2.setTranslationY(Cards.dp(this, 7));
        sheetBack2.setElevation(Cards.dp(this, 3));
        paperStack.addView(sheetBack2, new FrameLayout.LayoutParams(-1, -1));

        // 底层衬纸 2
        View sheetBack1 = new View(this);
        GradientDrawable sb1 = new GradientDrawable();
        sb1.setColor(0xFFF0EBE0);
        sb1.setStroke(Cards.dp(this, 1), 0x1A000000);
        sheetBack1.setBackground(sb1);
        sheetBack1.setRotation(0.9f);
        sheetBack1.setTranslationY(Cards.dp(this, 4));
        sheetBack1.setElevation(Cards.dp(this, 4));
        paperStack.addView(sheetBack1, new FrameLayout.LayoutParams(-1, -1));

        // 顶层主信纸 (1:1 还原 letter2.jpg + 40bfdf8b1v60f132aaf8d6bdb1e2c773.PNG 原木纤维信纸)
        vintageLetterSheet = new VintageLetterSheetView(this);
        vintageLetterSheet.setElevation(Cards.dp(this, 6));
        paperStack.addView(vintageLetterSheet, new FrameLayout.LayoutParams(-1, -1));

        letterStage.addView(paperStack, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 底部分页圆形箭头 (←) (→) (还原 letterexp3.jpg)
        LinearLayout pagerRow = new LinearLayout(this);
        pagerRow.setOrientation(LinearLayout.HORIZONTAL);
        pagerRow.setGravity(Gravity.CENTER);
        pagerRow.setPadding(0, Cards.dp(this, 12), 0, 0);

        btnPrevPage = buildLetterIcon("previous",L10n.s(R.string.ui_305));
        btnPrevPage.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                renderLetterPage();
            }
        });
        pagerRow.addView(btnPrevPage);

        View gap = new View(this);
        pagerRow.addView(gap, new LinearLayout.LayoutParams(Cards.dp(this, 20), 1));

        btnNextPage = buildLetterIcon("next",L10n.s(R.string.letter_next_page));
        btnNextPage.setOnClickListener(v -> {
            if (currentPage < letterPages.size() - 1) {
                currentPage++;
                renderLetterPage();
            }
        });
        pagerRow.addView(btnNextPage);

        letterStage.addView(pagerRow, new LinearLayout.LayoutParams(-1, -2));
        rootStage.addView(letterStage, new FrameLayout.LayoutParams(-1, -1));
    }

    private ImageView buildLetterIcon(String kind,String label){
        ImageView icon=new ImageView(this);
        icon.setImageDrawable(new AgentLineIcon(kind,0xFFE8E4DC));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding=Cards.dp(this,10);icon.setPadding(padding,padding,padding,padding);
        icon.setLayoutParams(new LinearLayout.LayoutParams(Cards.dp(this,40),Cards.dp(this,40)));
        icon.setContentDescription(label);icon.setFocusable(true);
        return icon;
    }

    private void stylePageIcon(ImageView icon,boolean enabled){
        icon.setEnabled(enabled);icon.setAlpha(enabled?1f:.3f);
    }

    private void bindRecord(Record r) {
        int theme = 0;
        if (r.fields != null && r.fields.length > 0) {
            try { theme = Integer.parseInt(r.fields[0]); } catch (Exception ignored) {}
        }
        String to = r.sub != null && !r.sub.isEmpty() ? r.sub : "Mira";
        envelopeView.bind(to, r.date, theme);

        // 将长信按自然段落分页
        letterPages.clear();
        String full = r.note != null ? r.note.trim() : "";
        if (!Pattern.compile("^(?:Dear\\b|To\\b|親愛的|亲爱的|致)",Pattern.CASE_INSENSITIVE).matcher(full).find()) {
            full = "Dear " + to + ",\n\n" + full;
        }
        paginateText(full);
        currentPage = 0;
        renderLetterPage();
    }

    private void paginateText(String full) {
        String[] paras = full.split("\n\n");
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            if (cur.length() > 0 && cur.length() + p.length() > CHARS_PER_PAGE) {
                letterPages.add(cur.toString().trim());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append("\n\n");
            cur.append(p);
        }
        if (cur.length() > 0 || letterPages.isEmpty()) {
            letterPages.add(cur.toString().trim());
        }
    }

    private void renderLetterPage() {
        if (record == null || letterPages.isEmpty() || vintageLetterSheet == null) return;
        String pageText = letterPages.get(currentPage);
        String to = record.sub != null && !record.sub.isEmpty() ? record.sub : "Mira";
        vintageLetterSheet.bindPage(pageText, to, record.date, record.title,
                currentPage == 0, currentPage == letterPages.size() - 1);

        stylePageIcon(btnPrevPage, currentPage > 0);
        stylePageIcon(btnNextPage, currentPage < letterPages.size() - 1);
    }

    /** 阶段 1 -> 阶段 2 -> 阶段 3 拆信封动画 (还原 letterexp2.jpg) */
    private void startOpenEnvelopeAnimation() {
        if (opening) return;
        opening = true;
        tvOpeningHint.setText("Opening your letter...");
        envelopeView.animate().rotation(-1.8f).setDuration(250).start();

        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(1150); // 1.15s 充裕平滑的撕开全过程，让用户清晰看到拉链封条从左一路被撕到右边
        va.setInterpolator(new AccelerateDecelerateInterpolator());
        va.addUpdateListener(anim -> {
            float frac = (float) anim.getAnimatedValue();
            envelopeView.setTearProgress(frac);
            // 物理微震动：在撕开每一个拉链齿时的摩擦微触感
            float microShake = (float) Math.sin(frac * Math.PI * 16) * (1f - frac) * Cards.dp(LetterActivity.this, 0.8f);
            envelopeView.setTranslationX(microShake);
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                envelopeView.setTranslationX(0f);
                envelopeStage.animate()
                        .alpha(0f)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(240)
                        .withEndAction(() -> {
                            envelopeStage.setVisibility(View.GONE);
                            letterStage.setAlpha(0f);
                            letterStage.setTranslationY(Cards.dp(LetterActivity.this, 28));
                            letterStage.setVisibility(View.VISIBLE);
                            letterStage.animate()
                                     .alpha(1f)
                                     .translationY(0f)
                                     .setDuration(320)
                                     .setInterpolator(new DecelerateInterpolator())
                                     .start();
                        })
                        .start();
            }
        });
        va.start();
    }

    private void foldBackToEnvelope() {
        opening = false;
        envelopeView.setTearProgress(0f);
        envelopeView.setRotation(0f);
        envelopeView.setTranslationX(0f);
        tvOpeningHint.setText(L10n.s(R.string.ui_461));

        letterStage.animate()
                .alpha(0f)
                .translationY(Cards.dp(this, 24))
                .setDuration(220)
                .withEndAction(() -> {
                    letterStage.setVisibility(View.GONE);
                    envelopeStage.setAlpha(0f);
                    envelopeStage.setScaleX(1f);
                    envelopeStage.setScaleY(1f);
                    envelopeStage.setVisibility(View.VISIBLE);
                    envelopeStage.animate().alpha(1f).setDuration(240).start();
                })
                .start();
    }
}
