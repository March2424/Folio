package hk.hkucs.dontwannaforget;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {Record.class, Media.class}, version = 2, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class AppDb extends RoomDatabase {
    public abstract RecordDao records();
    public abstract MediaDao media();

    private static volatile AppDb instance;

    public static AppDb get(Context c) {
        if (instance == null) {
            synchronized (AppDb.class) {
                if (instance == null) {
                    // 开发期:尚无真实用户数据,版本变化直接重建。
                    // 进入 M3 前必须改为正式 Migration,避免丢失用户记录。
                    instance = Room.databaseBuilder(c.getApplicationContext(), AppDb.class, "memory.db")
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return instance;
    }
}
