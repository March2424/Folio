package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.text.style.MetricAffectingSpan;
import android.view.View;

/** Flowing calligraphic script with luminous pearlescent glow, shared by both opening acts. */
final class EntranceGlowTextView extends View {
    private final TextPaint ink=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final SpannableString text;
    private StaticLayout layout;
    private int layoutWidth;
    EntranceGlowTextView(Context context,String words){
        super(context);setContentDescription(words);
        Typeface script=Typeface.create("cursive",Typeface.NORMAL);
        ink.setTypeface(script);
        boolean shortLine=words.length()<28;
        ink.setTextSize((shortLine?25f:20.5f)*getResources().getDisplayMetrics().scaledDensity);
        ink.setLetterSpacing(0.02f);
        text=new SpannableString(words);
        Typeface heavy=Typeface.create("cursive",Typeface.BOLD);
        for(String accent:new String[]{"Folio","words","melodies","glimpses","stay"}){
            int start=words.indexOf(accent);if(start>=0)text.setSpan(new DisplayFace(heavy),start,start+accent.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;
        int width=Math.max(1,getWidth()-Cards.dp(getContext(),52));
        if(layout==null||layoutWidth!=width){layoutWidth=width;layout=StaticLayout.Builder.obtain(text,0,text.length(),ink,width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).setLineSpacing(6*d,1.28f)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build();}
        canvas.save();canvas.translate((getWidth()-width)/2f,getHeight()*.47f-layout.getHeight()/2f);
        ink.setColor(0xFFA29E96);
        ink.setShadowLayer(Cards.dp(getContext(),3),0,0,0xB3FFFFFF);
        layout.draw(canvas);
        canvas.restore();
    }
    private static final class DisplayFace extends MetricAffectingSpan {
        private final Typeface face;DisplayFace(Typeface face){this.face=face;}
        @Override public void updateDrawState(TextPaint paint){paint.setTypeface(face);}
        @Override public void updateMeasureState(TextPaint paint){paint.setTypeface(face);}
    }
}
