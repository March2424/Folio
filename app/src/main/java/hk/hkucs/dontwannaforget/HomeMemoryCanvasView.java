package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** A bounded, zoomable home canvas, populated only from active local memories. */
public class HomeMemoryCanvasView extends FrameLayout {
    public interface Host {
        void openModule(int type);
        void openConversation();
        void back();
    }
    private final Host host;
    private final Viewport viewport;
    private final FrameLayout plane;
    private final List<FrostedNavigation> glassControls = new ArrayList<>();
    private MiniMap miniMap;
    private ZoomIcon zoomIn, zoomOut;
    private final List<Record> records = new ArrayList<>();
    private final long seed = System.nanoTime();
    private final String[] NAMES = {L10n.s(R.string.ui_083), L10n.s(R.string.ui_085), L10n.s(R.string.ui_084), L10n.s(R.string.ui_086), L10n.s(R.string.ui_042), L10n.s(R.string.ui_087)};

    public HomeMemoryCanvasView(Context context, Host host) {
        super(context);
        this.host = host;
        View curtain = new View(context);
        curtain.setBackgroundResource(R.drawable.bg_archive);
        addView(curtain,new LayoutParams(-1,-1));
        viewport = new Viewport(context);
        plane = new DeskPlane(context);
        plane.setClipChildren(false);
        plane.setPivotX(0); plane.setPivotY(0);
        viewport.addView(plane, new LayoutParams(1,1));
        LayoutParams vp = new LayoutParams(-1,-1);
        vp.topMargin=0; vp.bottomMargin=0;
        addView(viewport,vp);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16),0,dp(16),0);
        TextView brand = Cards.text(context,"Folio",21,0xFFDAD6CE,Typeface.create("cursive",Typeface.NORMAL));
        brand.setTag("folio-brand");brand.setGravity(Gravity.CENTER_VERTICAL);brand.setPadding(dp(8),0,dp(8),0);
        brand.setShadowLayer(dp(3),0,0,0x66FFFFFF);
        header.addView(brand,new LinearLayout.LayoutParams(-2,dp(48)));
        View space = new View(context);
        header.addView(space,new LinearLayout.LayoutParams(0,1,1));
        TextView all = button(L10n.s(R.string.ui_121));
        all.setTextSize(14);
        all.setOnClickListener(v -> host.openModule(-1));
        header.addView(all);
        addView(header,new LayoutParams(-1,dp(56),Gravity.TOP));
        FloatingHeader.style(header);
        FloatingHeader.glow(all);
        // Re-apply subtle pearlescent glow on cursive brand without overwriting its cursive font.
        brand.setShadowLayer(dp(3),0,0,0x66FFFFFF);
        // Each control owns only its visible bounds; the gaps pass gestures to the canvas.
        zoomIn = new ZoomIcon(context,true);
        zoomOut = new ZoomIcon(context,false);
        addGlassControl(zoomIn,curtain,48,48,Gravity.BOTTOM|Gravity.LEFT,16,68,14);
        addGlassControl(zoomOut,curtain,48,48,Gravity.BOTTOM|Gravity.LEFT,16,16,14);
        miniMap = new MiniMap(context);
        addGlassControl(miniMap,curtain,100,100,Gravity.BOTTOM|Gravity.LEFT,74,16,14);
        TextView chat=button("✧  "+L10n.s(R.string.ui_125));
        chat.setTextSize(12.5f);
        chat.setTextColor(0xFFEDEAE2);
        chat.setMinHeight(0);
        chat.setPadding(dp(14),dp(8),dp(14),dp(8));
        chat.setIncludeFontPadding(false);
        chat.setSingleLine(true);
        chat.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chat.setOnClickListener(v -> host.openConversation());
        addGlassControl(chat,curtain,110,44,Gravity.BOTTOM|Gravity.RIGHT,16,16,22);
        // Keep the left group compact; the chat frame fits its label with a small inset.
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int width=Math.round((r-l)/4f),height=Math.round((b-t)/9f);
            int gap=dp(5),side=dp(16),bottom=dp(10);
            int zoomWidth=Math.round(width*.35f),zoomHeight=(height-gap)/2;
            sizeControl(zoomIn,zoomWidth,zoomHeight,side,bottom+height-zoomHeight);
            sizeControl(zoomOut,zoomWidth,zoomHeight,side,bottom);
            sizeControl(miniMap,width-zoomWidth-gap,height,side+zoomWidth+gap,bottom);
            int chatWidth=(int)Math.ceil(chat.getPaint().measureText(chat.getText().toString()))+dp(30);
            android.graphics.Paint.FontMetrics metrics=chat.getPaint().getFontMetrics();
            int chatHeight=(int)Math.ceil(metrics.descent-metrics.ascent)+dp(18);
            sizeControl(chat,Math.min(chatWidth,(r-l)/2-dp(20)),chatHeight,side,bottom);
        });
        viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (r-l != or-ol || b-t != ob-ot) { rebuild(); viewport.reset(); }
        });
    }
    public void setRecords(List<Record> data) {
        records.clear();
        if(data!=null) for(Record r:data) if(r.deletedAt==0 && r.type>=0 && r.type<NAMES.length) records.add(r);
        records.sort(Comparator.comparing((Record r)->r.date==null?"":r.date).reversed()
                .thenComparing(Comparator.comparingLong((Record r)->r.createdAt).reversed()).thenComparing(r->r.id));
        rebuild();
    }
    public void refreshMedia() { rebuild(); }
    private void rebuild() {
        int w=viewport.getWidth(),h=viewport.getHeight();
        if(w==0 || h==0)return;
        // Keep three clear scale tiers so the desk has a focal centerpiece and organic orbit.
        int cardDp=Math.max(88,Math.min(114,Math.round(w/getResources().getDisplayMetrics().density*.28f)));
        int oldWorldW=plane.getLayoutParams().width,oldWorldH=plane.getLayoutParams().height;
        Random random=new Random(seed);
        List<List<Record>> buckets=new ArrayList<>();
        for(int type=0;type<NAMES.length;type++) {
            List<Record> bucket=new ArrayList<>();
            int limit=2+random.nextInt(2);
            for(Record r:records) if(r.type==type && bucket.size()<limit)bucket.add(r);
            if(!bucket.isEmpty())buckets.add(bucket);
        }
        // Round-robin interleave across categories so neighboring cards vary visually.
        List<Record> selected=new ArrayList<>();
        int round=0;
        boolean added;
        do {
            added=false;
            for(List<Record> b:buckets) {
                if(round<b.size()) { selected.add(b.get(round)); added=true; }
            }
            round++;
        } while(added);

        List<View> cards=new ArrayList<>();
        List<RectF> footprints=new ArrayList<>();
        float occupiedArea=0;
        for(int i=0;i<selected.size();i++) {
            Record record=selected.get(i);
            float tierScale=(i==0)?1.28f:(i<=3)?1.06f:(.86f+random.nextFloat()*.14f);
            View card=preview(record,Math.round(cardDp*tierScale));
            LayoutParams size=(LayoutParams)card.getLayoutParams();
            float rotation=(i==0)?-1.6f:(random.nextFloat()*12f-6f);
            double angle=Math.toRadians(rotation);
            int breathe=dp(i==0?34:28);
            float boundsW=(float)(size.width*Math.cos(angle)+size.height*Math.abs(Math.sin(angle)))+breathe;
            float boundsH=(float)(size.height*Math.cos(angle)+size.width*Math.abs(Math.sin(angle)))+breathe;
            card.setRotation(rotation);
            card.setElevation(dp(i==0?10:i<=3?6:4));
            cards.add(card);
            footprints.add(new RectF(0,0,boundsW,boundsH));
            occupiedArea+=boundsW*boundsH;
        }
        float aspect=w/(float)h;
        int worldW=Math.max(Math.round(w*1.26f),Math.round((float)Math.sqrt(occupiedArea/.56f*aspect)));
        int worldH=Math.max(Math.round(h*1.22f),Math.round((float)Math.sqrt(occupiedArea/.56f/aspect)));
        List<RectF> placed=new ArrayList<>();
        plane.removeAllViews();
        float seedAngle=random.nextFloat()*6.28f;
        for(int i=0;i<selected.size();i++) {
            final Record record=selected.get(i);
            View card=cards.get(i);
            FrameLayout.LayoutParams old=(LayoutParams)card.getLayoutParams();
            LayoutParams lp=new LayoutParams(old.width,old.height);
            RectF footprint=footprints.get(i);
            RectF position=constellationPosition(i,footprint.width(),footprint.height(),worldW,worldH,w,h,placed,seedAngle,random);
            placed.add(position);
            lp.leftMargin=Math.round(position.centerX()-old.width/2f);
            lp.topMargin=Math.round(position.centerY()-old.height/2f);
            card.setContentDescription(NAMES[record.type]+" · "+record.title+L10n.s(R.string.ui_122));
            card.setOnClickListener(v -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(75)
                    .withEndAction(() -> {
                        v.setScaleX(1f);v.setScaleY(1f);
                        host.openModule(record.type);
                    }).start());
            plane.addView(card,lp);
        }
        // Fit the final spread plus edge room.
        float halfW=w*.56f,halfH=h*.56f;
        for(RectF bounds:placed) {
            halfW=Math.max(halfW,Math.max(worldW/2f-bounds.left,bounds.right-worldW/2f)+dp(24));
            halfH=Math.max(halfH,Math.max(worldH/2f-bounds.top,bounds.bottom-worldH/2f)+dp(24));
        }
        int fittedW=Math.round(halfW*2),fittedH=Math.round(halfH*2);
        for(View card:cards) {
            LayoutParams lp=(LayoutParams)card.getLayoutParams();
            lp.leftMargin+=Math.round((fittedW-worldW)/2f);
            lp.topMargin+=Math.round((fittedH-worldH)/2f);
            card.setLayoutParams(lp);
        }
        worldW=fittedW;worldH=fittedH;
        plane.setLayoutParams(new LayoutParams(worldW,worldH));
        // Data/media refreshes may resize the canvas: preserve the viewed center.
        plane.setTranslationX(plane.getTranslationX()+(oldWorldW-worldW)*viewport.zoom/2f);
        plane.setTranslationY(plane.getTranslationY()+(oldWorldH-worldH)*viewport.zoom/2f);
        viewport.clamp();
        if(selected.isEmpty()) {
            TextView empty=Cards.text(getContext(),L10n.s(R.string.ui_124),12,0xFFABA79F,AppFonts.regular());
            empty.setGravity(Gravity.CENTER);
            LayoutParams cp=new LayoutParams(Math.min(dp(220),w-dp(32)),dp(64));
            cp.leftMargin=(worldW-cp.width)/2;cp.topMargin=(worldH-cp.height)/2;
            plane.addView(empty,cp);
        }
        updateControls();
    }
    private void addGlassControl(View content,View curtain,int width,int height,int gravity,int side,int bottom,float radiusDp) {
        FrostedNavigation glass=new FrostedNavigation(getContext(),null);
        glass.setCornerRadiusDp(radiusDp);
        glass.setGlassAppearance(0x362B2E2C,0x3CFFFFFF,195);
        glass.enableRoundedShadow(8);
        glass.setBackdrop(viewport,curtain);
        glass.addView(content,new LinearLayout.LayoutParams(-1,-1));
        LayoutParams lp=new LayoutParams(dp(width),dp(height),gravity);
        if((gravity&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.RIGHT)lp.rightMargin=dp(side);
        else lp.leftMargin=dp(side);
        lp.bottomMargin=dp(bottom);
        addView(glass,lp);
        glassControls.add(glass);
    }
    private void sizeControl(View content,int width,int height,int side,int bottom) {
        View glass=(View)content.getParent();
        LayoutParams lp=(LayoutParams)glass.getLayoutParams();
        boolean right=(lp.gravity&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.RIGHT;
        if(lp.width==width && lp.height==height && lp.bottomMargin==bottom
                && (right?lp.rightMargin:lp.leftMargin)==side)return;
        lp.width=width;lp.height=height;lp.bottomMargin=bottom;
        if(right)lp.rightMargin=side;else lp.leftMargin=side;
        glass.setLayoutParams(lp);
    }
    private void updateControls() {
        for(FrostedNavigation glass:glassControls)glass.invalidate();
        if(miniMap!=null)miniMap.invalidate();
        if(zoomIn!=null)zoomIn.setEnabled(viewport.zoom<1.399f);
        if(zoomOut!=null)zoomOut.setEnabled(viewport.zoom>.651f);
    }
    private class ZoomIcon extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        ZoomIcon(Context context,boolean increase) {
            super(context);
            setContentDescription(L10n.s(increase?R.string.home_zoom_in:R.string.home_zoom_out));
            setOnClickListener(v -> viewport.zoomAt(viewport.zoom*(increase?1.15f:1/1.15f),
                    viewport.getWidth()/2f,viewport.getHeight()/2f));
            setTag(increase);
            setFocusable(true);
        }
        @Override public CharSequence getAccessibilityClassName(){return android.widget.Button.class.getName();}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ink.setColor(isEnabled()?0xDDDADBD6:0x55DADBD6);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(1.1f));ink.setStrokeCap(Paint.Cap.ROUND);
            float cx=getWidth()*.45f,cy=getHeight()*.44f,r=Math.min(getWidth(),getHeight())*.23f;
            canvas.drawCircle(cx,cy,r,ink);
            canvas.drawLine(cx+r*.72f,cy+r*.72f,cx+r*1.36f,cy+r*1.36f,ink);
            canvas.drawLine(cx-r*.45f,cy,cx+r*.45f,cy,ink);
            if(Boolean.TRUE.equals(getTag()))canvas.drawLine(cx,cy-r*.45f,cx,cy+r*.45f,ink);
        }
        @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);invalidate();}
    }
    private class MiniMap extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF map=new RectF();
        private float mapScale;
        MiniMap(Context context){
            super(context);setClickable(true);setFocusable(true);
            setContentDescription(L10n.s(R.string.home_canvas_map));
        }
        private void measureMap(){
            int worldW=plane.getLayoutParams().width,worldH=plane.getLayoutParams().height;
            mapScale=Math.min((getWidth()-dp(8))/(float)Math.max(1,worldW),
                    (getHeight()-dp(8))/(float)Math.max(1,worldH));
            float w=worldW*mapScale,h=worldH*mapScale;
            map.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);measureMap();
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(.75f));ink.setColor(0x45D8DAD4);
            canvas.drawRoundRect(map,dp(6),dp(6),ink);
            canvas.save();
            android.graphics.Path clip=new android.graphics.Path();
            clip.addRoundRect(map,dp(6),dp(6),android.graphics.Path.Direction.CW);
            canvas.clipPath(clip);
            for(int i=0;i<plane.getChildCount();i++){
                View card=plane.getChildAt(i);
                LayoutParams lp=(LayoutParams)card.getLayoutParams();
                float x=map.left+lp.leftMargin*mapScale,y=map.top+lp.topMargin*mapScale;
                RectF rect=new RectF(x,y,x+lp.width*mapScale,y+lp.height*mapScale);
                canvas.save();canvas.rotate(card.getRotation(),rect.centerX(),rect.centerY());
                ink.setStyle(Paint.Style.FILL);ink.setColor(0x2AE0E1DA);canvas.drawRoundRect(rect,dp(1.5f),dp(1.5f),ink);
                ink.setStyle(Paint.Style.STROKE);ink.setColor(0x88CDD0C8);canvas.drawRoundRect(rect,dp(1.5f),dp(1.5f),ink);
                canvas.restore();
            }
            float left=-plane.getTranslationX()/viewport.zoom,top=-plane.getTranslationY()/viewport.zoom;
            RectF visible=new RectF(map.left+left*mapScale,map.top+top*mapScale,
                    map.left+(left+viewport.getWidth()/viewport.zoom)*mapScale,
                    map.top+(top+viewport.getHeight()/viewport.zoom)*mapScale);
            visible.intersect(map);
            visible.inset(dp(.8f),dp(.8f));
            ink.setStyle(Paint.Style.FILL);ink.setColor(0x28F2E4C6);canvas.drawRoundRect(visible,dp(3),dp(3),ink);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(1.3f));ink.setColor(0xDCD8C690);canvas.drawRoundRect(visible,dp(3),dp(3),ink);
            canvas.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN || event.getActionMasked()==MotionEvent.ACTION_MOVE){
                getParent().requestDisallowInterceptTouchEvent(true);measureMap();
                if(mapScale>0){
                    float x=Math.max(map.left,Math.min(map.right,event.getX()));
                    float y=Math.max(map.top,Math.min(map.bottom,event.getY()));
                    viewport.centerOn((x-map.left)/mapScale,(y-map.top)/mapScale);
                }
                return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP){performClick();return true;}
            return event.getActionMasked()==MotionEvent.ACTION_CANCEL || super.onTouchEvent(event);
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }

    /** Place the centerpiece near optical center and orbit remaining cards along a golden-angle spiral. */
    private RectF constellationPosition(int index,float width,float height,int worldW,int worldH,
                                        int viewW,int viewH,List<RectF> placed,float seedAngle,Random random) {
        float cx=worldW/2f, cy=worldH/2f-dp(18);
        if(index==0) {
            float x=cx-width/2f, y=cy-height/2f;
            return new RectF(x,y,x+width,y+height);
        }
        float visLeft=(worldW-viewW)/2f, visRight=visLeft+viewW;
        float visTop=(worldH-viewH)/2f, visBottom=visTop+viewH;
        float topGuard=visTop+dp(62), bottomGuard=visBottom-dp(94);

        RectF best=null;
        float bestScore=Float.MAX_VALUE;
        float goldenAngle=2.3999632f;
        float baseAngle=index*goldenAngle+seedAngle;
        float minR=dp(84)+Math.max(width,height)*.36f;
        float maxR=Math.max(worldW,worldH)*.46f;

        for(int attempt=0;attempt<420;attempt++) {
            float t=attempt/420f;
            float r=minR+t*(maxR-minR);
            float theta=baseAngle+(attempt%9-4)*0.22f+t*1.6f;
            float x=cx+r*(float)Math.cos(theta)-width/2f;
            float y=cy+r*(float)Math.sin(theta)*1.08f-height/2f;
            x=Math.max(dp(10),Math.min(worldW-width-dp(10),x));
            y=Math.max(dp(10),Math.min(worldH-height-dp(10),y));

            RectF candidate=new RectF(x,y,x+width,y+height);
            float overlap=0;
            for(RectF other:placed) {
                overlap+=Math.max(0,Math.min(candidate.right,other.right)-Math.max(x,other.left))
                        *Math.max(0,Math.min(candidate.bottom,other.bottom)-Math.max(y,other.top));
            }
            float hudPenalty=0;
            if(candidate.right>visLeft && candidate.left<visRight) {
                if(candidate.top<topGuard && candidate.bottom>visTop) hudPenalty+=450;
                if(candidate.bottom>bottomGuard && candidate.top<visBottom) hudPenalty+=450;
            }
            float distPenalty=(float)Math.hypot(candidate.centerX()-cx,candidate.centerY()-cy)/dp(180);
            float score=overlap/(width*height)*2000f+hudPenalty+distPenalty+random.nextFloat()*.15f;
            if(score<bestScore){bestScore=score;best=candidate;}
            if(overlap==0 && hudPenalty==0 && attempt>24) break;
        }
        return best!=null?best:new RectF(dp(12),dp(68),dp(12)+width,dp(68)+height);
    }

    /** Delicate archival orbital rings and registration crosshairs drawn behind the memory cards. */
    private final class DeskPlane extends FrameLayout {
        private final Paint deskPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        DeskPlane(Context context) {
            super(context);
            setWillNotDraw(false);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w=getWidth(), h=getHeight();
            if(w<=0 || h<=0) return;
            float cx=w/2f, cy=h/2f-dp(18);
            deskPaint.setStyle(Paint.Style.STROKE);
            deskPaint.setStrokeWidth(dp(.8f));
            deskPaint.setColor(0x14D8C690);
            float baseR=Math.min(w,h)*.24f;
            canvas.drawCircle(cx,cy,baseR,deskPaint);
            canvas.drawCircle(cx,cy,baseR*1.75f,deskPaint);

            deskPaint.setColor(0x1CF2EFE9);
            float cross=dp(4.5f);
            float[][] marks={{cx-baseR*1.3f,cy-baseR*.9f},{cx+baseR*1.4f,cy+baseR*.85f},{cx-baseR*.9f,cy+baseR*1.4f}};
            for(float[] m:marks) {
                canvas.drawLine(m[0]-cross,m[1],m[0]+cross,m[1],deskPaint);
                canvas.drawLine(m[0],m[1]-cross,m[0],m[1]+cross,deskPaint);
            }
        }
    }
    private View preview(Record record,int widthDp) {
        // Scale the complete original card so typography and decorations shrink together.
        View content=Cards.buildDayStackCard(getContext(),record,180,false);
        LayoutParams original=(LayoutParams)content.getLayoutParams();
        float scale=dp(widthDp)/(float)original.width;
        content.setPivotX(0);content.setPivotY(0);
        content.setScaleX(scale);content.setScaleY(scale);
        FrameLayout preview=new FrameLayout(getContext());
        preview.setClipChildren(false);
        preview.addView(content);
        preview.setLayoutParams(new LayoutParams(dp(widthDp),Math.round(original.height*scale)));
        return preview;
    }
    private TextView button(String label) {
        TextView view=Cards.text(getContext(),label,13,0xFFC9C6BF,AppFonts.regular());
        view.setGravity(Gravity.CENTER);view.setMinHeight(dp(48));
        view.setPadding(dp(12),0,dp(12),0);return view;
    }
    private int dp(float value){return Cards.dp(getContext(),value);}

    private class Viewport extends FrameLayout {
        private final ScaleGestureDetector detector;
        private final int slop;
        private float downX,downY,lastX,lastY,zoom=1f;
        private boolean dragging;
        Viewport(Context context) {
            super(context);setClipChildren(true);setClickable(true);
            slop=ViewConfiguration.get(context).getScaledTouchSlop();
            detector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d) {
                    zoomAt(zoom*d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;
                }
            });
        }
        @Override public boolean dispatchTouchEvent(MotionEvent e){detector.onTouchEvent(e);return super.dispatchTouchEvent(e);}
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=lastX=e.getX();downY=lastY=e.getY();dragging=false;}
            if(e.getPointerCount()>1 || detector.isInProgress())dragging=true;
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE && Math.hypot(e.getX()-downX,e.getY()-downY)>slop)dragging=true;
            return dragging;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE && e.getPointerCount()==1 && !detector.isInProgress()) {
                plane.setTranslationX(plane.getTranslationX()+e.getX()-lastX);
                plane.setTranslationY(plane.getTranslationY()+e.getY()-lastY);clamp();
            }
            if(e.getActionMasked()==MotionEvent.ACTION_POINTER_UP){int index=e.getActionIndex()==0?1:0;lastX=e.getX(index);lastY=e.getY(index);}
            else {lastX=e.getX();lastY=e.getY();}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!dragging)performClick();dragging=false;}
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        void zoomAt(float value,float x,float y) {
            float next=Math.max(.65f,Math.min(1.4f,value));
            plane.setTranslationX(x-(x-plane.getTranslationX())*next/zoom);
            plane.setTranslationY(y-(y-plane.getTranslationY())*next/zoom);
            zoom=next;plane.setScaleX(zoom);plane.setScaleY(zoom);clamp();
        }
        void reset(){zoom=1;plane.setScaleX(1);plane.setScaleY(1);plane.setTranslationX((getWidth()-plane.getLayoutParams().width)/2f);plane.setTranslationY((getHeight()-plane.getLayoutParams().height)/2f);clamp();}
        void centerOn(float x,float y){
            plane.setTranslationX(getWidth()/2f-x*zoom);
            plane.setTranslationY(getHeight()/2f-y*zoom);clamp();
        }
        void clamp(){
            float remainingX=getWidth()-plane.getLayoutParams().width*zoom;
            float remainingY=getHeight()-plane.getLayoutParams().height*zoom;
            plane.setTranslationX(remainingX>=0?remainingX/2f:Math.max(remainingX,Math.min(0,plane.getTranslationX())));
            plane.setTranslationY(remainingY>=0?remainingY/2f:Math.max(remainingY,Math.min(0,plane.getTranslationY())));
            updateControls();
        }
    }
}
