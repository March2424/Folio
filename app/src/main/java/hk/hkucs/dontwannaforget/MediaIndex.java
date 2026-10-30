package hk.hkucs.dontwannaforget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 内存中的"记录 → 图片"索引,供 UI 同步查询(由数据库 LiveData 刷新)。 */
public final class MediaIndex {
    private MediaIndex() {}

    private static volatile Map<String, List<Media>> map = new HashMap<>();

    public static void set(List<Media> all) {
        Map<String, List<Media>> m = new HashMap<>();
        if (all != null) for (Media x : all) {
            List<Media> l = m.get(x.recordId);
            if (l == null) { l = new ArrayList<>(); m.put(x.recordId, l); }
            l.add(x);
        }
        map = m;
    }

    public static void put(String recordId, List<Media> list) {
        Map<String, List<Media>> m = new HashMap<>(map);
        m.put(recordId, list);
        map = m;
    }

    public static List<Media> list(String recordId, String role) {
        List<Media> all = map.get(recordId);
        if (all == null) return Collections.emptyList();
        List<Media> out = new ArrayList<>();
        for (Media x : all) if (role.equals(x.role)) out.add(x);
        return out;
    }

    public static Media first(String recordId, String role) {
        List<Media> l = list(recordId, role);
        return l.isEmpty() ? null : l.get(0);
    }
}
