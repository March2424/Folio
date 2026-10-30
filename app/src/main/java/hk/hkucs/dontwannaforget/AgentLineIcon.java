package hk.hkucs.dontwannaforget;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small, rounded pen-stroke icons; kept as vectors for every screen density. */
final class AgentLineIcon extends Drawable {
    private final String kind;
    private final Paint pen=AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    AgentLineIcon(String kind,int color){this.kind=kind;pen.setColor(color);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.7f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){
        canvas.save();Rect b=getBounds();float size=Math.min(b.width(),b.height());canvas.translate(b.centerX()-size/2,b.centerY()-size/2);canvas.scale(size/28,size/28);
        Path p=new Path();

            if("previous".equals(kind)) { p.moveTo(22,14);p.lineTo(6,14);p.moveTo(12,8);p.lineTo(6,14);p.lineTo(12,20); }
            else if("next".equals(kind)) { p.moveTo(6,14);p.lineTo(22,14);p.moveTo(16,8);p.lineTo(22,14);p.lineTo(16,20); }
            else if("close".equals(kind)) { p.moveTo(7,7);p.lineTo(21,21);p.moveTo(21,7);p.lineTo(7,21); }
            else if("back".equals(kind)) { p.moveTo(17,6);p.lineTo(9,14);p.lineTo(17,22); }
            else if(kind.equals(L10n.s(R.string.ui_209))) { p.moveTo(6,5);p.lineTo(23,5);p.quadTo(25,5,25,7);p.lineTo(25,19);p.quadTo(25,21,23,21);p.lineTo(11,21);p.lineTo(5,26);p.lineTo(5,8);p.quadTo(5,5,6,5);}
            else if(kind.equals(L10n.s(R.string.ui_210))) { canvas.drawRoundRect(6,3,23,25,2,2,pen);canvas.drawLine(10,3,10,25,pen);canvas.drawLine(14,9,19,9,pen);canvas.drawLine(14,13,19,13,pen);}
            else if(kind.equals(L10n.s(R.string.ui_217))) { p.moveTo(14,25);p.cubicTo(10,22,2,16,3,9);p.cubicTo(4,1,12,2,14,8);p.cubicTo(17,1,25,2,25,9);p.cubicTo(26,16,18,23,14,25);}
            else if(kind.equals(L10n.s(R.string.ui_216))) {
                for(int i=0;i<8;i++){double a=i*Math.PI/4;float x=(float)Math.cos(a),y=(float)Math.sin(a);canvas.drawLine(14+x*9,14+y*9,14+x*12,14+y*12,pen);}
                canvas.drawCircle(14,14,9,pen);canvas.drawCircle(14,14,3.5f,pen);}
            else if(kind.equals(L10n.s(R.string.ui_231))) { canvas.drawLine(14,5,14,23,pen);canvas.drawLine(5,14,23,14,pen);}
            else if(kind.equals(L10n.s(R.string.ui_238))) { p.moveTo(3,12);p.lineTo(25,3);p.lineTo(17,25);p.lineTo(12,16);p.close();p.moveTo(12,16);p.lineTo(25,3);}
        canvas.drawPath(p,pen);canvas.restore();
    }
    @Override public void setAlpha(int alpha){pen.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
