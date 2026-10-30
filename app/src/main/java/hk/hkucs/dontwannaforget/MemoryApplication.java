package hk.hkucs.dontwannaforget;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class MemoryApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();L10n.init(this);AppFonts.init(this);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(L10n.language()));
    }
}
