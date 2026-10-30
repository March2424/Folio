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
import java.util.Collections;
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
        plane = new FrameLayout(context);
        plane.setClipChildren(false);
        plane.setPivotX(0); plane.setPivotY(0);
        viewport.addView(plane, new LayoutParams(1,1));
        LayoutParams vp = new LayoutParams(-1,-1);
        vp.topMargin=0; vp.bottomMargin=0;
        addView(viewport,vp);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16),0,dp(16),0);
        TextView brand = Cards.text(context,"Folio",20,0xB3E4E1D8,Typeface.create("cursive",Typeface.NORMAL));
        brand.setTag("folio-brand");brand.setGravity(Gravity.CENTER_VERTICAL);brand.setPadding(dp(8),0,dp(8),0);
        header.addView(brand,new LinearLayout.LayoutParams(-2,dp(48)));
        View space = new View(context);
        header.addView(space,new LinearLayout.LayoutParams(0,1,1));
        TextView all = button(L10n.s(R.string.ui_121));
        all.setOnClickListener(v -> host.openModule(-1));
        header.addView(all);
        addView(header,new LayoutParams(-1,dp(56),Gravity.TOP));
        FloatingHeader.style(header);
        // Each control owns only its visible bounds; the gaps pass gestures to the canvas.
        zoomIn = new ZoomIcon(context,true);
        zoomOut = new ZoomIcon(context,false);
        addGlassControl(zoomIn,curtain,48,48,Gravity.BOTTOM|Gravity.LEFT,16,68);
        addGlassControl(zoomOut,curtain,48,48,Gravity.BOTTOM|Gravity.LEFT,16,16);
        miniMap = new MiniMap(context);
        addGlassControl(miniMap,curtain,100,100,Gravity.BOTTOM|Gravity.LEFT,74,16);
        TextView chat=button(L10n.s(R.string.ui_125)+"  +");
        chat.setTextSize(12);
        chat.setMinHeight(0);
        chat.setPadding(dp(8),dp(6),dp(8),dp(6));
        chat.setIncludeFontPadding(false);
        chat.setSingleLine(true);
        chat.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chat.setOnClickListener(v -> host.openConversation());
        addGlassControl(chat,curtain,90,64,Gravity.BOTTOM|Gravity.RIGHT,16,16);
        // Keep the left group compact; the chat frame fits its label with a small inset.
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int width=Math.round((r-l)/4f),height=Math.round((b-t)/9f);
            int gap=dp(4),side=dp(16),bottom=dp(4);
            int zoomWidth=Math.round(width*.34f),zoomHeight=(height-gap)/2;
            sizeControl(zoomIn,zoomWidth,zoomHeight,side,bottom+height-zoomHeight);
            sizeControl(zoomOut,zoomWidth,zoomHeight,side,bottom);
            sizeControl(miniMap,width-zoomWidth-gap,height,side+zoomWidth+gap,bottom);
            int chatWidth=(int)Math.ceil(chat.getPaint().measureText(chat.getText().toString()))+dp(16);
            android.graphics.Paint.FontMetrics metrics=chat.getPaint().getFontMetrics();
            int chatHeight=(int)Math.ceil(metrics.descent-metrics.ascent)+dp(12);
            sizeControl(chat,Math.min(chatWidth,(r-l)/2-dp(24)),chatHeight,side,bottom);
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
        // Keep previews small; grow the canvas from their actual sizes and spacing.
        int cardDp=Math.max(88,Math.min(120,Math.round(w/getResources().getDisplayMetrics().density*.29f)));
        int oldWorldW=plane.getLayoutParams().width,oldWorldH=plane.getLayoutParams().height;
        Random random=new Random(seed);
        List<Record> selected=new ArrayList<>();
        for(int type=0;type<NAMES.length;type++) {
            int limit=2+random.nextInt(2),count=0;
            for(Record r:records) if(r.type==type && count++<limit)selected.add(r);
        }
        List<View> cards=new ArrayList<>();
        List<RectF> footprints=new ArrayList<>();
        float occupiedArea=0;
        Collections.shuffle(selected,random);
        for(Record record:selected) {
            View card=preview(record,Math.round(cardDp*(.92f+random.nextFloat()*.12f)));
            LayoutParams size=(LayoutParams)card.getLayoutParams();
            float rotation=random.nextFloat()*14f-7f;
            double angle=Math.toRadians(rotation);
            // Include a breathing space around each rotated card.
            float boundsW=(float)(size.width*Math.cos(angle)+size.height*Math.abs(Math.sin(angle)))+dp(32);
            float boundsH=(float)(size.height*Math.cos(angle)+size.width*Math.abs(Math.sin(angle)))+dp(32);
            card.setRotation(rotation);
            cards.add(card);
            footprints.add(new RectF(0,0,boundsW,boundsH));
            occupiedArea+=boundsW*boundsH;
        }
        float aspect=w/(float)h;
        int worldW=Math.max(Math.round(w*1.12f),Math.round((float)Math.sqrt(occupiedArea/.64f*aspect)));
        int worldH=Math.max(Math.round(h*1.12f),Math.round((float)Math.sqrt(occupiedArea/.64f/aspect)));
        RectF conversationSpace=new RectF();
        List<RectF> placed=new ArrayList<>();
        plane.removeAllViews();
        for(int i=0;i<selected.size();i++) {
            Record record=selected.get(i);
            View card=cards.get(i);
            FrameLayout.LayoutParams old=(LayoutParams)card.getLayoutParams();
            LayoutParams lp=new LayoutParams(old.width,old.height);
            RectF footprint=footprints.get(i);
            RectF position=scatteredPosition(footprint.width(),footprint.height(),worldW,worldH,conversationSpace,placed,random);
            placed.add(position);
            lp.leftMargin=Math.round(position.centerX()-old.width/2f);
            lp.topMargin=Math.round(position.centerY()-old.height/2f);
            card.setContentDescription(NAMES[record.type]+" · "+record.title+L10n.s(R.string.ui_122));
            card.setOnClickListener(v -> host.openModule(record.type));
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
    private void addGlassControl(View content,View curtain,int width,int height,int gravity,int side,int bottom) {
        FrostedNavigation glass=new FrostedNavigation(getContext(),null);
        glass.setCornerRadiusDp(6);
        glass.setGlassAppearance(0x282B2E2C,0x28FFFFFF,184);
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
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(.7f));ink.setColor(0x45D8DAD4);
            canvas.drawRect(map,ink);
            canvas.save();canvas.clipRect(map);
            for(int i=0;i<plane.getChildCount();i++){
                View card=plane.getChildAt(i);
                LayoutParams lp=(LayoutParams)card.getLayoutParams();
                float x=map.left+lp.leftMargin*mapScale,y=map.top+lp.topMargin*mapScale;
                RectF rect=new RectF(x,y,x+lp.width*mapScale,y+lp.height*mapScale);
                canvas.save();canvas.rotate(card.getRotation(),rect.centerX(),rect.centerY());
                ink.setStyle(Paint.Style.FILL);ink.setColor(0x24E0E1DA);canvas.drawRect(rect,ink);
                ink.setStyle(Paint.Style.STROKE);ink.setColor(0x85CDD0C8);canvas.drawRect(rect,ink);
                canvas.restore();
            }
            float left=-plane.getTranslationX()/viewport.zoom,top=-plane.getTranslationY()/viewport.zoom;
            RectF visible=new RectF(map.left+left*mapScale,map.top+top*mapScale,
                    map.left+(left+viewport.getWidth()/viewport.zoom)*mapScale,
                    map.top+(top+viewport.getHeight()/viewport.zoom)*mapScale);
            visible.intersect(map);
            visible.inset(dp(.8f),dp(.8f));
            ink.setStyle(Paint.Style.FILL);ink.setColor(0x28FFFFFF);canvas.drawRect(visible,ink);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(1.4f));ink.setColor(0xE0ECEDE6);canvas.drawRect(visible,ink);
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

    private RectF scatteredPosition(float width,float height,int worldW,int worldH,
                                    RectF conversation,List<RectF> placed,Random random) {
        RectF best=null;
        float bestScore=Float.MAX_VALUE;
        // Sample freely in both axes. Prefer clear, loosely spaced positions without making
        // rows or columns; the fixed session seed keeps media refreshes from jumping.
        for(int attempt=0;attempt<1200;attempt++) {
            float x=dp(8)+random.nextFloat()*(worldW-width-dp(16));
            float y=dp(8)+random.nextFloat()*(worldH-height-dp(16));
            RectF candidate=new RectF(x,y,x+width,y+height);
            if(RectF.intersects(candidate,conversation))continue;
            float overlap=0,alignment=0;
            for(RectF other:placed) {
                overlap+=Math.max(0,Math.min(candidate.right,other.right)-Math.max(x,other.left))
                        *Math.max(0,Math.min(candidate.bottom,other.bottom)-Math.max(y,other.top));
                float dx=Math.abs(candidate.centerX()-other.centerX());
                float dy=Math.abs(candidate.centerY()-other.centerY());
                alignment+=Math.max(0,1-dx/dp(22))+Math.max(0,1-dy/dp(22));
            }
            float nx=(candidate.centerX()-worldW/2f)/(worldW/2f);
            float ny=(candidate.centerY()-worldH/2f)/(worldH/2f);
            float score=overlap/(width*height)*1000+(nx*nx+ny*ny)*.65f
                    +alignment*.24f+random.nextFloat()*.32f;
            if(score<bestScore){bestScore=score;best=candidate;}
        }
        // Fallback for an unusually small viewport.
        return best!=null?best:new RectF(dp(8),dp(8),dp(8)+width,dp(8)+height);
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
