package hk.hkucs.dontwannaforget;

import android.content.Context;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class ArchiveRoomSource implements ArchiveMemoryBridge.Records {
    private final AppDb db;
    ArchiveRoomSource(Context context){db=AppDb.get(context.getApplicationContext());}
    @Override public List<Record> active(){return db.records().getActiveOnce();}
    @Override public String version() throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update("samples-enabled-v2".getBytes(StandardCharsets.UTF_8));
        for(ArchiveVersion r:db.records().agentVersions())digest.update((r.id+"\u0000"+r.updatedAt+"\u0000"+r.deletedAt+"\n").getBytes(StandardCharsets.UTF_8));
        return android.util.Base64.encodeToString(digest.digest(),android.util.Base64.NO_WRAP);
    }
}
