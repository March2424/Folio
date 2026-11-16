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
            else if("more".equals(kind)) {
                Paint dot=new Paint(pen);dot.setStyle(Paint.Style.FILL);
                canvas.drawCircle(14,7.5f,1.65f,dot);canvas.drawCircle(14,14f,1.65f,dot);canvas.drawCircle(14,20.5f,1.65f,dot);
            }
            else if("play".equals(kind)) {
                Paint fill=new Paint(pen);fill.setStyle(Paint.Style.FILL);
                Path tri=new Path();tri.moveTo(9.5f,6.5f);tri.lineTo(22f,14f);tri.lineTo(9.5f,21.5f);tri.close();
                canvas.drawPath(tri,fill);
            }
            else if("pause".equals(kind)) {
                Paint fill=new Paint(pen);fill.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(8.5f,6.5f,11.8f,21.5f,1.2f,1.2f,fill);
                canvas.drawRoundRect(16.2f,6.5f,19.5f,21.5f,1.2f,1.2f,fill);
            }
            else if("volume".equals(kind)) {
                p.moveTo(5.5f,11.5f);p.lineTo(9.5f,11.5f);p.lineTo(14.5f,7f);p.lineTo(14.5f,21f);p.lineTo(9.5f,16.5f);p.lineTo(5.5f,16.5f);p.close();
                p.moveTo(18f,10.5f);p.quadTo(20.6f,14f,18f,17.5f);
                p.moveTo(20.6f,8f);p.quadTo(24.4f,14f,20.6f,20f);
            }
            else if("mute".equals(kind)) {
                p.moveTo(5.5f,11.5f);p.lineTo(9.5f,11.5f);p.lineTo(14.5f,7f);p.lineTo(14.5f,21f);p.lineTo(9.5f,16.5f);p.lineTo(5.5f,16.5f);p.close();
                p.moveTo(18.2f,11f);p.lineTo(23.2f,17f);
                p.moveTo(23.2f,11f);p.lineTo(18.2f,17f);
            }
            else if("audio_file".equals(kind)) {
                p.moveTo(11f,19.5f);p.lineTo(11f,7.5f);p.lineTo(21f,5.5f);p.lineTo(21f,17.5f);
                canvas.drawCircle(8.8f,20f,2.6f,pen);
                canvas.drawCircle(18.8f,18f,2.6f,pen);
            }
            else if("photo_frame".equals(kind)) {
                canvas.drawRoundRect(4.5f,6f,23.5f,22f,2.8f,2.8f,pen);
                canvas.drawCircle(10f,11f,1.6f,pen);
                p.moveTo(5.5f,19.5f);p.lineTo(11f,14.5f);p.lineTo(14.8f,18f);p.lineTo(18.2f,14f);p.lineTo(22.5f,19.5f);
            }
            else if("mic".equals(kind)) {
                canvas.drawRoundRect(10.5f,4.5f,17.5f,15.5f,3.5f,3.5f,pen);
                p.moveTo(7.5f,13f);p.cubicTo(7.5f,20f,20.5f,20f,20.5f,13f);
                p.moveTo(14f,18.2f);p.lineTo(14f,23f);
                p.moveTo(10f,23f);p.lineTo(18f,23f);
            }
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
