package hk.hkucs.dontwannaforget;

import android.content.Intent;
import android.view.View;
import androidx.core.view.ViewCompat;

/** Preserve normal back behavior, with a direct home shortcut on the same control. */
public final class HomeNavigation {
    public static final String SHOW_HOME = "show_home";
    private HomeNavigation() {}
    public static void bind(View back) {
        back.setOnLongClickListener(v -> { open(v); return true; });
        ViewCompat.addAccessibilityAction(back, L10n.s(R.string.ui_172), (v,args) -> { open(v); return true; });
    }
    private static void open(View view) {
        Intent home = new Intent(view.getContext(), MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        home.putExtra(SHOW_HOME, true);
        view.getContext().startActivity(home);
    }
}
