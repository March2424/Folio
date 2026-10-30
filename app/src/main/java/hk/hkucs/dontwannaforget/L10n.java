package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** UI resources only. Record text and stored identifiers are never converted. */
public final class L10n {
    private static Context app, localized;
    public static void init(Context context) { app=context.getApplicationContext(); refresh(); }
    private static void refresh() {
        Configuration configuration=new Configuration(app.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(language()));
        localized=app.createConfigurationContext(configuration);
    }
    public static String language() { return app.getSharedPreferences("appearance",0).getString("language","zh-TW"); }
    public static String s(int id) { return localized.getString(id); }
    public static void choose(android.app.Activity activity) {
        new androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("語言 / Language")
            .setSingleChoiceItems(new String[]{"繁體中文","English"},language().equals("en")?1:0,(dialog,index)->{
                String tag=index==1?"en":"zh-TW";
                app.getSharedPreferences("appearance",0).edit().putString("language",tag).apply();refresh();
                dialog.dismiss();
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
}
