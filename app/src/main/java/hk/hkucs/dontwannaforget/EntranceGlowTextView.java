package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.text.style.MetricAffectingSpan;
import android.view.View;
import androidx.core.content.res.ResourcesCompat;

/** Rounded display serif with close, restrained ink bloom, shared by both opening acts. */
final class EntranceGlowTextView extends View {
    private final TextPaint ink=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final SpannableString text;
    private StaticLayout layout;
    private int layoutWidth;
    EntranceGlowTextView(Context context,String words){
        super(context);setContentDescription(words);
        ink.setTypeface(ResourcesCompat.getFont(context,R.font.folio_display));
        ink.setTextSize(17*getResources().getDisplayMetrics().scaledDensity);
        text=new SpannableString(words);
        Typeface heavy=ResourcesCompat.getFont(context,R.font.folio_display_black);
        for(String accent:new String[]{"Folio","words","melodies","glimpses","stay"}){
            int start=words.indexOf(accent);if(start>=0)text.setSpan(new DisplayFace(heavy),start,start+accent.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;
        int width=Math.max(1,getWidth()-Cards.dp(getContext(),56));
        if(layout==null||layoutWidth!=width){layoutWidth=width;layout=StaticLayout.Builder.obtain(text,0,text.length(),ink,width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(4*d,1.18f)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build();}
        canvas.save();canvas.translate((getWidth()-width)/2f,getHeight()*.47f-layout.getHeight()/2f);
        // A nearby low-opacity echo thickens the ink without blurring its silhouette.
        ink.setColor(0x308F8D90);ink.setShadowLayer(1.1f*d,0,0,0x508F8D90);
        canvas.save();canvas.translate(.55f*d,-.18f*d);layout.draw(canvas);canvas.restore();
        ink.setColor(0xFFC2C0C2);ink.setShadowLayer(.55f*d,0,0,0x409B999C);layout.draw(canvas);
        canvas.restore();
    }
    private static final class DisplayFace extends MetricAffectingSpan {
        private final Typeface face;DisplayFace(Typeface face){this.face=face;}
        @Override public void updateDrawState(TextPaint paint){paint.setTypeface(face);}
        @Override public void updateMeasureState(TextPaint paint){paint.setTypeface(face);}
    }
}
