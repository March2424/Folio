package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.text.TextPaint;
import androidx.core.content.res.ResourcesCompat;

/** Bundled Ming Chinese and typewriter Latin, including offline Android 24 support. */
public final class AppFonts {
    private static Typeface regular;
    public static void init(Context context) {
        if(regular==null)regular=ResourcesCompat.getFont(context,R.font.memory_regular);
    }
    public static Typeface regular(){return regular;}
    public static Typeface bold(){return Typeface.create(regular,Typeface.BOLD);}
    public static Paint paint(int flags){Paint p=new Paint(flags);p.setTypeface(regular);return p;}
    public static TextPaint textPaint(int flags){TextPaint p=new TextPaint(flags);p.setTypeface(regular);return p;}
}
