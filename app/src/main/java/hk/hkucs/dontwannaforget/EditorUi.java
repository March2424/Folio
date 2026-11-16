package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Consistent editor sizing; full text remains editable inside bounded multiline fields. */
final class EditorUi {
    private EditorUi() {}

    static final class TopBar {
        final FrameLayout root;
        final TextView back, center, pill;
        TopBar(FrameLayout root, TextView back, TextView center, TextView pill) {
            this.root = root; this.back = back; this.center = center; this.pill = pill;
        }
    }

    /** Unified three-slot editor header: aligned on one optical baseline with a snug right pill. */
    static TopBar topBar(Context context, String title, String pillText, boolean lightSurface,
                         Runnable onBack, Runnable onPill) {
        FrameLayout bar = new FrameLayout(context);
        bar.setPadding(0, 0, 0, Cards.dp(context, 6));

        TextView back = Cards.text(context, L10n.s(R.string.ui_060), 13,
                lightSurface ? 0xFF4A4640 : 0xFFC7C2B8, AppFonts.regular());
        back.setIncludeFontPadding(false);
        back.setSingleLine(true);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setPadding(0, 0, Cards.dp(context, 12), 0);
        back.setOnClickListener(v -> onBack.run());
        HomeNavigation.bind(back);
        bar.addView(back, new FrameLayout.LayoutParams(-2, Cards.dp(context, 40),
                Gravity.START | Gravity.CENTER_VERTICAL));

        TextView center = Cards.text(context, title, 15,
                lightSurface ? 0xFF2B2927 : 0xFFF2EFE9,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        center.setIncludeFontPadding(false);
        center.setSingleLine(true);
        center.setEllipsize(TextUtils.TruncateAt.END);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        int sideGuard = Cards.dp(context, 84);
        clp.leftMargin = sideGuard;
        clp.rightMargin = sideGuard;
        bar.addView(center, clp);

        TextView pill = Cards.text(context, pillText, 11.5f,
                lightSurface ? 0xFF8E4F4B : 0xFFD8C690, AppFonts.regular());
        pill.setIncludeFontPadding(false);
        pill.setSingleLine(true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(Cards.dp(context, 10), Cards.dp(context, 4.5f),
                Cards.dp(context, 10), Cards.dp(context, 4.5f));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(lightSurface ? 0xFFFAF8F3 : 0xFF252726);
        pbg.setCornerRadius(Cards.dp(context, 13));
        pbg.setStroke(Cards.dp(context, 1), lightSurface ? 0xFFD8D2C5 : 0xFF3E413F);
        pill.setBackground(pbg);
        pill.setOnClickListener(v -> onPill.run());
        bar.addView(pill, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));

        return new TopBar(bar, back, center, pill);
    }
    static int previewWidth(android.content.Context context,int preferredDp) {
        int available=context.getResources().getDisplayMetrics().widthPixels-Cards.dp(context,88);
        return Math.max(Cards.dp(context,120),Math.min(Cards.dp(context,preferredDp),available));
    }
    static void action(android.widget.TextView button) {
        button.setTextSize(13);button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);button.setMinHeight(Cards.dp(button.getContext(),44));
        int vertical=Cards.dp(button.getContext(),8);
        button.setPadding(button.getPaddingLeft(),vertical,button.getPaddingRight(),vertical);
    }
    static void input(EditText input,boolean singleLine) {
        input.setTextSize(14);input.setIncludeFontPadding(false);
        int horizontal=Cards.dp(input.getContext(),12),vertical=Cards.dp(input.getContext(),9);
        input.setPadding(horizontal,vertical,horizontal,vertical);
        input.setMinHeight(Cards.dp(input.getContext(),48));
        if(singleLine)return;
        input.setMinLines(3);input.setMaxLines(6);
        input.setGravity(Gravity.TOP|Gravity.START);input.setLineSpacing(0,1.15f);
        input.setVerticalScrollBarEnabled(true);
        input.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN && (v.canScrollVertically(1)||v.canScrollVertically(-1)))
                v.getParent().requestDisallowInterceptTouchEvent(true);
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)
                v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
    }
}
