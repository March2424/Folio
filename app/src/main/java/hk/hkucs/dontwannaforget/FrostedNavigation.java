package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/** Small downsampled backdrop blur, compatible with the app's API 24 minimum. */
public final class FrostedNavigation extends LinearLayout {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path outline=new Path();
    private float cornerRadiusDp=24;
    public void setCornerRadiusDp(float radius){cornerRadiusDp=Math.max(0,radius);invalidate();}
    private int tintColor=0x662B2E2C,borderColor=0x38FFFFFF,backdropAlpha=255;
    public void setGlassAppearance(int tint,int border,int blurAlpha){
        tintColor=tint;borderColor=border;backdropAlpha=Math.max(0,Math.min(255,blurAlpha));invalidate();
    }
    private Bitmap backdrop;
    private int[] pixels,temporary;
    private View source,backdropBackground;
    public FrostedNavigation(Context context,AttributeSet attrs){super(context,attrs);setWillNotDraw(false);}
    public void setBackdrop(View source,View background){this.source=source;this.backdropBackground=background;invalidate();}
    @Override public void draw(Canvas canvas){
        outline.reset();outline.addRoundRect(new RectF(0,0,getWidth(),getHeight()),Cards.dp(getContext(),cornerRadiusDp),Cards.dp(getContext(),cornerRadiusDp),Path.Direction.CW);
        canvas.save();canvas.clipPath(outline);super.draw(canvas);canvas.restore();
    }
    @Override protected void onDraw(Canvas canvas){
        if(source!=null && getWidth()>0 && getHeight()>0){
            int w=Math.max(1,getWidth()/8),h=Math.max(1,getHeight()/8);
            if(backdrop==null||backdrop.getWidth()!=w||backdrop.getHeight()!=h){
                if(backdrop!=null)backdrop.recycle();backdrop=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);pixels=new int[w*h];temporary=new int[w*h];
            }
            backdrop.eraseColor(0xFF171918);
            // Capture siblings separately; redrawing the ancestor during its draw pass
            // would corrupt Android's elevation-sorted child list.
            Canvas sample=new Canvas(backdrop);sample.scale(w/(float)getWidth(),h/(float)getHeight());
            drawBehind(sample,backdropBackground);drawBehind(sample,source);
            backdrop.getPixels(pixels,0,w,0,0,w,h);
            blur(pixels,temporary,w,h,true);blur(temporary,pixels,w,h,false);
            backdrop.setPixels(pixels,0,w,0,0,w,h);
            paint.setColor(Color.WHITE);paint.setAlpha(backdropAlpha);paint.setStyle(Paint.Style.FILL);
            canvas.drawBitmap(backdrop,null,new RectF(0,0,getWidth(),getHeight()),paint);
        }
        paint.setStyle(Paint.Style.FILL);paint.setColor(tintColor);canvas.drawPath(outline,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Cards.dp(getContext(),1));paint.setColor(borderColor);
        canvas.drawRoundRect(new RectF(1,1,getWidth()-1,getHeight()-1),Cards.dp(getContext(),cornerRadiusDp),Cards.dp(getContext(),cornerRadiusDp),paint);
        paint.setStyle(Paint.Style.FILL);super.onDraw(canvas);
    }
    private void drawBehind(Canvas canvas,View view){
        if(view==null||view.getVisibility()!=VISIBLE)return;
        int[] here=new int[2],there=new int[2];getLocationOnScreen(here);view.getLocationOnScreen(there);
        canvas.save();canvas.translate(there[0]-here[0],there[1]-here[1]);view.draw(canvas);canvas.restore();
    }
    private static void blur(int[] input,int[] output,int w,int h,boolean horizontal){
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=0,g=0,b=0;
            for(int d=-2;d<=2;d++){
                int xx=horizontal?Math.max(0,Math.min(w-1,x+d)):x;
                int yy=horizontal?y:Math.max(0,Math.min(h-1,y+d));int color=input[yy*w+xx];
                r+=Color.red(color);g+=Color.green(color);b+=Color.blue(color);
            }
            output[y*w+x]=Color.rgb(r/5,g/5,b/5);
        }
    }
    @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();if(backdrop!=null){backdrop.recycle();backdrop=null;}pixels=null;temporary=null;}
}
