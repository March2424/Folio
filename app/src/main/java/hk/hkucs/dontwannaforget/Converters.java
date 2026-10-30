package hk.hkucs.dontwannaforget;

import androidx.room.TypeConverter;

/** String[] 与数据库文本互转(用 \u001F 作分隔符,正文里基本不会出现)。 */
public class Converters {
    private static final String SEP = "\u001F";

    @TypeConverter
    public static String fromArray(String[] a) {
        return a == null ? "" : String.join(SEP, a);
    }

    @TypeConverter
    public static String[] toArray(String s) {
        return s == null || s.isEmpty() ? new String[0] : s.split(SEP, -1);
    }
}
