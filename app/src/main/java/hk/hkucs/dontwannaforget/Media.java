package hk.hkucs.dontwannaforget;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/** 一张已导入的原图。文件在 filesDir 下由 App 管理,path 为相对 filesDir 的路径。 */
@Entity(tableName = "media", indices = {@Index("recordId")})
public class Media {
    public static final String COVER = "cover";                 // 电影/书/音乐封面
    public static final String ORIGINAL = "ticket_original";    // 票根原件
    public static final String PHOTO = "photo";                 // 普通附图/足迹照片
    public static final String AUDIO = "audio";                 // 声音/录音文件

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();
    public String recordId;
    public String path;
    public String role;
    public int width, height;
    public String sha256;
    public int sortOrder;
    public long createdAt;
}
