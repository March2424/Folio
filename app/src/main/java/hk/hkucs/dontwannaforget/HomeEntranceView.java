package hk.hkucs.dontwannaforget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** The original welcome artwork, independent of the home page's card layout. */
final class HomeEntranceView extends FrameLayout {
    private FrameLayout splashOverlay;
    private final Runnable finished;
    private final Runnable dismissAction=this::dismissSplash;
    private Runnable nextAct;
    HomeEntranceView(Context context,Runnable finished){
        super(context);this.finished=finished;setTag("home-entrance");
        setContentDescription(getResources().getString(R.string.folio_welcome_line));
        playEntranceAnimation();
    }
    @Override protected void onDetachedFromWindow(){
        if(splashOverlay!=null){
            splashOverlay.removeCallbacks(dismissAction);
            if(nextAct!=null)splashOverlay.removeCallbacks(nextAct);
            for(int i=0;i<splashOverlay.getChildCount();i++)splashOverlay.getChildAt(i).animate().cancel();
            splashOverlay.animate().cancel();
        }
        super.onDetachedFromWindow();
    }
    // ---- 开场动画 (仿 example/loginexp.jpg -> 文艺短句 -> 进入主页) ----
    public void playEntranceAnimation() {
        if (splashOverlay != null) {
            removeView(splashOverlay);
            splashOverlay = null;
        }
        Context c = getContext();
        splashOverlay = new FrameLayout(c);
        splashOverlay.setClickable(true);
        splashOverlay.setFocusable(true);

        ImageView splashBg = new ImageView(c);
        splashBg.setImageResource(R.drawable.bg_archive);
        splashBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splashOverlay.addView(splashBg, new LayoutParams(-1, -1));

        View darkVeil = new View(c);
        darkVeil.setBackgroundColor(0xD8141416);
        splashOverlay.addView(darkVeil, new LayoutParams(-1, -1));

        EntranceGlowTextView stage1View = new EntranceGlowTextView(c,getResources().getString(R.string.folio_welcome_line));
        stage1View.setContentDescription(getResources().getString(R.string.folio_welcome_line));
        splashOverlay.addView(stage1View, new LayoutParams(-1, -1));

        EntranceGlowTextView stage2Box = new EntranceGlowTextView(c,getResources().getString(R.string.folio_welcome_quote));
        stage2Box.setAlpha(0f);
        splashOverlay.addView(stage2Box, new LayoutParams(-1, -1, Gravity.CENTER));

        TextView skipHint = Cards.text(c, L10n.s(R.string.welcome_enter), 9.5f,
                0x66B0ACA4, AppFonts.regular());
        skipHint.setGravity(Gravity.CENTER);
        LayoutParams slp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.bottomMargin = Cards.dp(c, 36);
        splashOverlay.addView(skipHint, slp);

        addView(splashOverlay, new LayoutParams(-1, -1));

        splashOverlay.setOnClickListener(v -> dismissSplash());

        nextAct=()->{
            if(splashOverlay==null)return;
            stage1View.animate().alpha(0f).setDuration(900).start();
            stage2Box.setTranslationY(Cards.dp(c,12));
            stage2Box.animate().alpha(1f).translationY(0f).setDuration(900)
                    .withEndAction(()->{if(splashOverlay!=null)splashOverlay.postDelayed(dismissAction,2800);}).start();
        };
        stage1View.setAlpha(0f);stage1View.setScaleX(.96f);stage1View.setScaleY(.96f);
        stage1View.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900)
                .withEndAction(()->{if(splashOverlay!=null)splashOverlay.postDelayed(nextAct,2500);}).start();
    }

    private void dismissSplash() {
        if (splashOverlay == null) return;
        final View target = splashOverlay;
        target.removeCallbacks(dismissAction);
        if(nextAct!=null)target.removeCallbacks(nextAct);
        splashOverlay = null;
        target.animate()
                .alpha(0f)
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(900)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeView(target);
                        if (getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)getParent()).removeView(HomeEntranceView.this);
                        finished.run();
                    }
                })
                .start();
    }

}
