package hk.hkucs.dontwannaforget;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MediaDao {
    @Query("SELECT * FROM media ORDER BY sortOrder ASC, createdAt ASC")
    LiveData<List<Media>> observeAll();

    @Query("SELECT * FROM media ORDER BY id")
    List<Media> allForBackup();

    @Query("SELECT * FROM media WHERE recordId = :rid ORDER BY sortOrder ASC, createdAt ASC")
    List<Media> forRecord(String rid);

    @Query("SELECT * FROM media WHERE recordId = :rid AND role = :role")
    List<Media> byRole(String rid, String role);

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM media WHERE recordId = :rid AND role = :role")
    int maxSort(String rid, String role);

    @Insert
    void insert(Media m);

    @Query("DELETE FROM media WHERE id = :id")
    void delete(String id);

    @Query("UPDATE media SET sortOrder = :sort WHERE id = :id")
    void updateSort(String id, int sort);
}
