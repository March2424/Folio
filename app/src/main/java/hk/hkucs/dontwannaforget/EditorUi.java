package hk.hkucs.dontwannaforget;

import android.view.MotionEvent;
import android.view.Gravity;
import android.widget.EditText;

/** Consistent editor sizing; full text remains editable inside bounded multiline fields. */
final class EditorUi {
    private EditorUi() {}
    static int previewWidth(android.content.Context context,int preferredDp) {
        int available=context.getResources().getDisplayMetrics().widthPixels-Cards.dp(context,88);
        return Math.max(Cards.dp(context,120),Math.min(Cards.dp(context,preferredDp),available));
    }
    static void action(android.widget.TextView button) {
        button.setTextSize(13);button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);button.setMinHeight(Cards.dp(button.getContext(),44));
        int vertical=Cards.dp(button.getContext(),8);
        button.setPadding(button.getPaddingLeft(),vertical,button.getPaddingRight(),vertical);
    }
    static void input(EditText input,boolean singleLine) {
        input.setTextSize(14);input.setIncludeFontPadding(false);
        int horizontal=Cards.dp(input.getContext(),12),vertical=Cards.dp(input.getContext(),9);
        input.setPadding(horizontal,vertical,horizontal,vertical);
        input.setMinHeight(Cards.dp(input.getContext(),48));
        if(singleLine)return;
        input.setMinLines(3);input.setMaxLines(6);
        input.setGravity(Gravity.TOP|Gravity.START);input.setLineSpacing(0,1.15f);
        input.setVerticalScrollBarEnabled(true);
        input.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN && (v.canScrollVertically(1)||v.canScrollVertically(-1)))
                v.getParent().requestDisallowInterceptTouchEvent(true);
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)
                v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
    }
}
