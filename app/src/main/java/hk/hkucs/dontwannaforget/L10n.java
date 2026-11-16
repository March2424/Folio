package hk.hkucs.dontwannaforget;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** UI resources only. Record text and stored identifiers are never converted. */
public final class L10n {
    private static Context app, localized;
    public static void init(Context context) { app=context.getApplicationContext(); refresh(); }
    private static void refresh() {
        Configuration configuration=new Configuration(app.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(language()));
        localized=app.createConfigurationContext(configuration);
    }
    public static String language() { return app.getSharedPreferences("appearance",0).getString("language","zh-TW"); }
    public static String s(int id) { return localized.getString(id); }
    public static void choose(android.app.Activity activity) {
        Dialog dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrostedNavigation panel=new FrostedNavigation(activity,null);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setCornerRadiusDp(20);
        panel.setGlassAppearance(0x58222528,0x48FFFFFF,210);
        panel.enableRoundedShadow(14);
        View backdrop=activity.findViewById(android.R.id.content);
        if(backdrop!=null)panel.setBackdrop(backdrop,null);
        int hPad=Cards.dp(activity,16);
        panel.setPadding(hPad,Cards.dp(activity,14),hPad,Cards.dp(activity,10));

        TextView title=Cards.text(activity,"語言 / Language",14.5f,0xFFF2F3F5,Typeface.create(AppFonts.regular(),Typeface.BOLD));
        title.setPadding(Cards.dp(activity,4),0,Cards.dp(activity,4),Cards.dp(activity,8));
        panel.addView(title);

        String[] labels={"繁體中文","English"};
        String[] tags={"zh-TW","en"};
        int current=language().equals("en")?1:0;
        for(int i=0;i<labels.length;i++){
            final boolean selected=i==current;
            final String tag=tags[i];
            LinearLayout row=new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Cards.dp(activity,40));
            row.setPadding(Cards.dp(activity,10),Cards.dp(activity,6),Cards.dp(activity,10),Cards.dp(activity,6));
            if(selected){
                GradientDrawable selectedBg=new GradientDrawable();
                selectedBg.setColor(0x1CFFFFFF);
                selectedBg.setCornerRadius(Cards.dp(activity,12));
                row.setBackground(selectedBg);
            }
            View radio=new View(activity);
            radio.setBackground(radioDot(activity,selected));
            row.addView(radio,new LinearLayout.LayoutParams(Cards.dp(activity,16),Cards.dp(activity,16)));

            TextView label=Cards.text(activity,labels[i],14,selected?0xFFF2F3F5:0xFFC5C7CB,AppFonts.regular());
            LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-2,-2);
            llp.leftMargin=Cards.dp(activity,12);
            row.addView(label,llp);

            row.setOnClickListener(v->{
                app.getSharedPreferences("appearance",0).edit().putString("language",tag).apply();
                refresh();
                dialog.dismiss();
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
                if(activity instanceof MainActivity){
                    ((MainActivity)activity).applyLanguageUI();
                }else if(activity!=null&&!activity.isFinishing()){
                    activity.recreate();
                }
            });
            LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);
            rlp.bottomMargin=Cards.dp(activity,2);
            panel.addView(row,rlp);
        }

        TextView cancel=Cards.text(activity,activity.getString(android.R.string.cancel),13,0xFFB5B8BD,AppFonts.regular());
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinHeight(Cards.dp(activity,34));
        cancel.setPadding(Cards.dp(activity,12),0,Cards.dp(activity,12),0);
        cancel.setOnClickListener(v->dialog.dismiss());
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-2,-2);
        clp.gravity=Gravity.END;
        clp.topMargin=Cards.dp(activity,4);
        panel.addView(cancel,clp);

        int shadowPad=Cards.dp(activity,14);
        int cardWidth=Math.min(Cards.dp(activity,240),Math.round(activity.getResources().getDisplayMetrics().widthPixels*0.68f));
        FrameLayout wrapper=new FrameLayout(activity);
        wrapper.setClipChildren(false);wrapper.setClipToPadding(false);
        wrapper.setPadding(shadowPad,shadowPad,shadowPad,shadowPad);
        wrapper.addView(panel,new FrameLayout.LayoutParams(cardWidth,ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(wrapper);
        dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.24f);
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private static Drawable radioDot(Context context,boolean selected){
        float stroke=Cards.dp(context,1.5f);
        int color=selected?0xFFF2F3F5:0xFF8C9096;
        return new Drawable(){
            private final Paint paint=AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){
                float cx=getBounds().exactCenterX(),cy=getBounds().exactCenterY();
                float r=Math.min(getBounds().width(),getBounds().height())/2f-stroke;
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(color);
                canvas.drawCircle(cx,cy,r,paint);
                if(selected){paint.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy,r*0.52f,paint);}
            }
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
            @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
}
