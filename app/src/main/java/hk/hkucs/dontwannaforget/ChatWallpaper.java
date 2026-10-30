package hk.hkucs.dontwannaforget;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Quiet, deterministic paper flecks; drawn natively at every screen density. */
final class ChatWallpaper extends Drawable {
    private final Paint paint=AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    ChatWallpaper(float density){this.density=density;}
    @Override public void draw(@NonNull Canvas canvas){
        canvas.drawColor(0xFFFCFCFC);float w=getBounds().width(),h=getBounds().height();
        for(int i=0;i<38;i++){
            float x=((i*73+19)%317)/317f*w,y=((i*113+41)%521)/521f*h;
            paint.setColor(i%6==0?0xFFDDDDDD:0xFFE7E7E7);
            if(i%6==0){float r=(i%2==0?4.5f:3)*density;Path star=new Path();
                for(int j=0;j<8;j++){double angle=-Math.PI/2+j*Math.PI/4;float radius=j%2==0?r:r*.3f;float sx=x+(float)Math.cos(angle)*radius,sy=y+(float)Math.sin(angle)*radius;if(j==0)star.moveTo(sx,sy);else star.lineTo(sx,sy);}star.close();canvas.drawPath(star,paint);
            }else canvas.drawCircle(x,y,.55f*density,paint);
        }
    }
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(@Nullable ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
