package hk.hkucs.dontwannaforget;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RecordDao {
    /** 时间线:日期倒序,同日按创建时间倒序,再按 id 稳定排序;回收站内容不出现。 */
    @Query("SELECT * FROM records WHERE deletedAt = 0 ORDER BY date DESC, createdAt DESC, id ASC")
    LiveData<List<Record>> observeActive();

    @Query("SELECT * FROM records WHERE deletedAt = 0 ORDER BY date DESC, createdAt DESC, id ASC")
    List<Record> getActiveOnce();

    @Query("SELECT id,updatedAt,deletedAt FROM records ORDER BY id")
    List<ArchiveVersion> agentVersions();

    @Query("SELECT * FROM records ORDER BY id")
    List<Record> allForBackup();

    @Query("SELECT * FROM records WHERE id = :id")
    Record get(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Record r);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Record> list);

    @Update
    void update(Record r);

    @Query("UPDATE records SET deletedAt = :t WHERE id = :id")
    void setDeleted(String id, long t);

    @Query("SELECT COUNT(*) FROM records")
    int count();

    @Query("DELETE FROM records WHERE id LIKE 'sample-%'")
    void deleteSamples();
}
