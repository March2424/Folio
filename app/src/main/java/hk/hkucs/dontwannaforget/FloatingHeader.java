package hk.hkucs.dontwannaforget;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Navigation floats above content; only its controls consume touches. */
final class FloatingHeader {
    private FloatingHeader() {}
    static void style(View header) {
        header.setTag("floating-header");
        header.setBackground(null);
        header.setAlpha(.86f);
        header.setElevation(Cards.dp(header.getContext(), 8));
        softenText(header);
    }
    static final int DETAIL_BACKGROUND=0xFF111212;
    static final int DETAIL_TEXT=0xFFE8E4DC,DETAIL_MUTED=0xFF8E8A82;
    /** Preserve labels and listeners, with back at the left and all actions at the right. */
    static void detailRow(android.widget.LinearLayout row,TextView back,boolean preserveVoiceStyle){
        java.util.List<View> actions=new java.util.ArrayList<>();
        for(int i=0;i<row.getChildCount();i++){
            View child=row.getChildAt(i);if(child!=back && child instanceof TextView)actions.add(child);
        }
        row.removeAllViews();row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if(!preserveVoiceStyle){back.setBackground(null);back.setTextColor(DETAIL_TEXT);back.setTextSize(13);}
        back.setGravity(android.view.Gravity.CENTER_VERTICAL);back.setMinHeight(Cards.dp(row.getContext(),48));
        row.addView(back,new android.widget.LinearLayout.LayoutParams(-2,-2));
        View gap=new View(row.getContext());row.addView(gap,new android.widget.LinearLayout.LayoutParams(0,1,1));
        for(View action:actions){
            TextView text=(TextView)action;
            if(!preserveVoiceStyle){
                text.setBackground(null);text.setTextColor(DETAIL_MUTED);text.setTextSize(11.5f);
                text.setPadding(Cards.dp(row.getContext(),10),Cards.dp(row.getContext(),6),0,Cards.dp(row.getContext(),6));
            }
            text.setGravity(android.view.Gravity.CENTER_VERTICAL);text.setMinHeight(Cards.dp(row.getContext(),48));
            row.addView(text,new android.widget.LinearLayout.LayoutParams(-2,-2));
        }
        style(row);
    }
    private static void softenText(View view) {
        if(view instanceof TextView && !(view instanceof EditText)) {
            TextView text=(TextView)view;
            text.setIncludeFontPadding(false);
            int color=text.getCurrentTextColor();
            boolean light=Color.red(color)+Color.green(color)+Color.blue(color)>420;
            text.setShadowLayer(Cards.dp(view.getContext(), 3),0,0,light?0xA0000000:0xB3FFFFFF);
            if(text.isClickable())text.setMinHeight(Cards.dp(view.getContext(),48));
        } else if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)softenText(group.getChildAt(i));
        }
    }
    static void pin(View header) {
        header.post(()->{
            if(!(header.getParent() instanceof ViewGroup))return;
            ViewGroup column=(ViewGroup)header.getParent();
            android.view.ViewParent ancestor=column.getParent();
            while(ancestor instanceof View && !(ancestor instanceof ScrollView))ancestor=ancestor.getParent();
            if(!(ancestor instanceof ScrollView))return;
            ScrollView scroll=(ScrollView)ancestor;
            if(!(scroll.getParent() instanceof ViewGroup))return;
            ViewGroup parent=(ViewGroup)scroll.getParent();
            int position=parent.indexOfChild(scroll), index=column.indexOfChild(header);
            ViewGroup.LayoutParams scrollParams=scroll.getLayoutParams(), headerParams=header.getLayoutParams();
            View spacer=new View(header.getContext());
            column.removeView(header);
            // The initial breathing room scrolls away with the document.
            headerParams.height=Math.max(header.getHeight(),Cards.dp(header.getContext(),56));
            column.addView(spacer,index,headerParams);
            FrameLayout layer=new FrameLayout(header.getContext());
            parent.removeView(scroll);parent.addView(layer,position,scrollParams);
            layer.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
            FrameLayout.LayoutParams floating=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.TOP);
            layer.addView(header,floating);style(header);
            Runnable align=()->{
                FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)header.getLayoutParams();
                int top=scroll.getPaddingTop()+column.getPaddingTop();
                int left=scroll.getPaddingLeft()+column.getPaddingLeft(),right=scroll.getPaddingRight()+column.getPaddingRight();
                if(lp.topMargin!=top||lp.leftMargin!=left||lp.rightMargin!=right){lp.topMargin=top;lp.leftMargin=left;lp.rightMargin=right;header.setLayoutParams(lp);}
                int height=header.getHeight();
                if(height>0&&spacer.getLayoutParams().height!=height){spacer.getLayoutParams().height=height;spacer.requestLayout();}
            };
            layer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->align.run());
            header.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->align.run());
            align.run();
        });
    }
}
