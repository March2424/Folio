package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;

import androidx.lifecycle.LiveData;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 数据访问入口:所有写操作在后台单线程执行,读取通过 LiveData / 回调。 */
public class Repo {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final AppDb db;
    private final Context app;

    public Repo(Context c) {
        app = c.getApplicationContext();
        db = AppDb.get(app);
    }

    public LiveData<List<Record>> observeActive() {
        return db.records().observeActive();
    }

    /** One-shot snapshot for explicitly selected agent uploads. */
    public void getActiveOnce(Callback<List<Record>> cb) {
        IO.execute(() -> cb.done(db.records().getActiveOnce()));
    }

    public LiveData<List<Media>> observeMedia() {
        return db.media().observeAll();
    }

    public interface Callback<T> { void done(T result); }

    /** 读取记录,同时刷新该记录的图片索引。在后台线程回调。 */
    public void get(String id, Callback<Record> cb) {
        IO.execute(() -> {
            Record r = db.records().get(id);
            if (r != null) MediaIndex.put(id, db.media().forRecord(id));
            cb.done(r);
        });
    }

    public void save(Record r, Runnable done) {
        IO.execute(() -> {
            r.updatedAt = System.currentTimeMillis();
            db.records().insert(r);
            if (done != null) done.run();
        });
    }

    public void moveToTrash(String id) {
        IO.execute(() -> db.records().setDeleted(id, System.currentTimeMillis()));
    }

    /**
     * 导入图片并入库。COVER 角色每条记录仅一张:成功导入新图后才替换旧图。
     * 失败(无法读取/不支持格式)时旧数据不变,回调 false。在后台线程回调。
     */
    public void addMedia(Uri uri, String recordId, String role, Callback<Boolean> cb) {
        IO.execute(() -> {
            try {
                MediaDao dao = db.media();
                int sort = dao.maxSort(recordId, role) + 1;
                Media m = MediaFiles.importUri(app, uri, recordId, role, sort);
                if (Media.COVER.equals(role)) {
                    for (Media old : dao.byRole(recordId, role)) {
                        dao.delete(old.id);
                        new File(app.getFilesDir(), old.path).delete();
                    }
                }
                dao.insert(m);
                MediaIndex.put(recordId, dao.forRecord(recordId));
                cb.done(true);
            } catch (Exception e) {
                cb.done(false);
            }
        });
    }

    /**
     * 原子化保存记录与待导入的图片 URI(先复制图片到私有目录,全部成功后再提交记录与媒体表)。
     * 失败时清理本次已复制的文件,不产生半完成记录。
     */
    public void saveNewRecord(Record r, Uri coverUri, List<Uri> photoUris, Callback<Boolean> cb) {
        IO.execute(() -> {
            java.util.ArrayList<Media> imported = new java.util.ArrayList<>();
            try {
                if (coverUri != null) {
                    imported.add(MediaFiles.importUri(app, coverUri, r.id, Media.COVER, 0));
                }
                if (photoUris != null) {
                    for (int i = 0; i < photoUris.size(); i++) {
                        Uri u = photoUris.get(i);
                        if (u != null) {
                            imported.add(MediaFiles.importUri(app, u, r.id, Media.PHOTO, i));
                        }
                    }
                }
                long now = System.currentTimeMillis();
                if (r.createdAt == 0) r.createdAt = now;
                r.updatedAt = now;
                db.runInTransaction(() -> {
                    db.records().insert(r);
                    MediaDao mdao = db.media();
                    for (Media m : imported) mdao.insert(m);
                });
                MediaIndex.put(r.id, db.media().forRecord(r.id));
                cb.done(true);
            } catch (Exception e) {
                for (Media m : imported) {
                    new File(app.getFilesDir(), m.path).delete();
                }
                cb.done(false);
            }
        });
    }

    /**
     * 保存或更新足迹纸质画布记录(支持多页画布,每页可保留原图或替换为新选图片)。
     */
    public void saveFootprintRecord(Record r, List<Uri> newUris, List<Media> existingMedias, Callback<Boolean> cb) {
        IO.execute(() -> {
            java.util.ArrayList<Media> imported = new java.util.ArrayList<>();
            try {
                int count = Math.max(newUris != null ? newUris.size() : 0,
                        existingMedias != null ? existingMedias.size() : 0);
                for (int i = 0; i < count; i++) {
                    Uri u = (newUris != null && i < newUris.size()) ? newUris.get(i) : null;
                    if (u != null) {
                        imported.add(MediaFiles.importUri(app, u, r.id, Media.PHOTO, i));
                    } else {
                        imported.add(null);
                    }
                }
                long now = System.currentTimeMillis();
                if (r.createdAt == 0) r.createdAt = now;
                r.updatedAt = now;

                db.runInTransaction(() -> {
                    db.records().insert(r);
                    MediaDao mdao = db.media();
                    List<Media> oldAll = mdao.byRole(r.id, Media.PHOTO);
                    java.util.HashSet<String> keepIds = new java.util.HashSet<>();
                    for (int i = 0; i < count; i++) {
                        Media fresh = i < imported.size() ? imported.get(i) : null;
                        Media old = (existingMedias != null && i < existingMedias.size()) ? existingMedias.get(i) : null;
                        if (fresh != null) {
                            mdao.insert(fresh);
                        } else if (old != null) {
                            keepIds.add(old.id);
                            mdao.updateSort(old.id, i);
                        }
                    }
                    for (Media m : oldAll) {
                        if (!keepIds.contains(m.id)) {
                            mdao.delete(m.id);
                            new File(app.getFilesDir(), m.path).delete();
                        }
                    }
                });
                MediaIndex.put(r.id, db.media().forRecord(r.id));
                cb.done(true);
            } catch (Exception e) {
                for (Media m : imported) {
                    if (m != null) new File(app.getFilesDir(), m.path).delete();
                }
                cb.done(false);
            }
        });
    }

    /** 导入或替换某条声音记录的录音文件。 */
    public void addAudio(Uri uri, String recordId, int durationSec, Callback<Boolean> cb) {
        IO.execute(() -> {
            try {
                MediaDao dao = db.media();
                Media m = MediaFiles.importAudioUri(app, uri, recordId, durationSec);
                for (Media old : dao.byRole(recordId, Media.AUDIO)) {
                    dao.delete(old.id);
                    new File(app.getFilesDir(), old.path).delete();
                }
                dao.insert(m);
                MediaIndex.put(recordId, dao.forRecord(recordId));
                cb.done(true);
            } catch (Exception e) {
                cb.done(false);
            }
        });
    }

    /**
     * 原子化保存一条声音记忆(支持现场录音文件 recordedFile 或本地上传音频 uploadedAudioUri + 可选封面图 coverUri)。
     */
    public void saveVoiceRecord(Record r, File recordedFile, Uri uploadedAudioUri, Uri coverUri,
                                int durationSec, Callback<Boolean> cb) {
        IO.execute(() -> {
            java.util.ArrayList<Media> imported = new java.util.ArrayList<>();
            try {
                if (recordedFile != null && recordedFile.exists()) {
                    imported.add(MediaFiles.importAudioFile(app, recordedFile, r.id, durationSec));
                } else if (uploadedAudioUri != null) {
                    imported.add(MediaFiles.importAudioUri(app, uploadedAudioUri, r.id, durationSec));
                }
                if (coverUri != null) {
                    imported.add(MediaFiles.importUri(app, coverUri, r.id, Media.COVER, 0));
                }
                long now = System.currentTimeMillis();
                if (r.createdAt == 0) r.createdAt = now;
                r.updatedAt = now;
                db.runInTransaction(() -> {
                    db.records().insert(r);
                    MediaDao mdao = db.media();
                    for (Media m : imported) mdao.insert(m);
                });
                MediaIndex.put(r.id, db.media().forRecord(r.id));
                cb.done(true);
            } catch (Exception e) {
                for (Media m : imported) {
                    new File(app.getFilesDir(), m.path).delete();
                }
                cb.done(false);
            }
        });
    }

    /**
     * 原子化保存或更新一条电影票根记录(支持可选电影海报 coverUri 与扫描/拍照上传的实体票根 originalTicketUri)。
     */
    public void saveTicketRecord(Record r, Uri coverUri, Uri originalTicketUri, Callback<Boolean> cb) {
        IO.execute(() -> {
            Media importedCover = null;
            Media importedOrig = null;
            try {
                if (coverUri != null) {
                    importedCover = MediaFiles.importUri(app, coverUri, r.id, Media.COVER, 0);
                }
                if (originalTicketUri != null) {
                    importedOrig = MediaFiles.importUri(app, originalTicketUri, r.id, Media.ORIGINAL, 0);
                }
                long now = System.currentTimeMillis();
                if (r.createdAt == 0) r.createdAt = now;
                r.updatedAt = now;

                final Media finalCover = importedCover;
                final Media finalOrig = importedOrig;
                db.runInTransaction(() -> {
                    db.records().insert(r);
                    MediaDao mdao = db.media();
                    if (finalCover != null) {
                        for (Media old : mdao.byRole(r.id, Media.COVER)) {
                            mdao.delete(old.id);
                            new File(app.getFilesDir(), old.path).delete();
                        }
                        mdao.insert(finalCover);
                    }
                    if (finalOrig != null) {
                        for (Media old : mdao.byRole(r.id, Media.ORIGINAL)) {
                            mdao.delete(old.id);
                            new File(app.getFilesDir(), old.path).delete();
                        }
                        mdao.insert(finalOrig);
                    }
                });
                MediaIndex.put(r.id, db.media().forRecord(r.id));
                cb.done(true);
            } catch (Exception e) {
                if (importedCover != null) new File(app.getFilesDir(), importedCover.path).delete();
                if (importedOrig != null) new File(app.getFilesDir(), importedOrig.path).delete();
                cb.done(false);
            }
        });
    }

    /**
     * 原子化保存或更新一条书籍摘抄记录 (支持可选封面/心情照片 coverUri 与附加照片 extraPhotoUris)。
     */
    public void saveBookRecord(Record r, Uri coverUri, List<Uri> extraPhotoUris, Callback<Boolean> cb) {
        IO.execute(() -> {
            Media importedCover = null;
            java.util.ArrayList<Media> importedExtras = new java.util.ArrayList<>();
            try {
                if (coverUri != null) {
                    importedCover = MediaFiles.importUri(app, coverUri, r.id, Media.COVER, 0);
                }
                if (extraPhotoUris != null) {
                    int baseSort = db.media().maxSort(r.id, Media.PHOTO) + 1;
                    for (int i = 0; i < extraPhotoUris.size(); i++) {
                        Uri u = extraPhotoUris.get(i);
                        if (u != null) {
                            importedExtras.add(MediaFiles.importUri(app, u, r.id, Media.PHOTO, baseSort + i));
                        }
                    }
                }
                long now = System.currentTimeMillis();
                if (r.createdAt == 0) r.createdAt = now;
                r.updatedAt = now;

                final Media finalCover = importedCover;
                db.runInTransaction(() -> {
                    db.records().insert(r);
                    MediaDao mdao = db.media();
                    if (finalCover != null) {
                        for (Media old : mdao.byRole(r.id, Media.COVER)) {
                            mdao.delete(old.id);
                            new File(app.getFilesDir(), old.path).delete();
                        }
                        mdao.insert(finalCover);
                    }
                    for (Media m : importedExtras) {
                        mdao.insert(m);
                    }
                });
                MediaIndex.put(r.id, db.media().forRecord(r.id));
                cb.done(true);
            } catch (Exception e) {
                if (importedCover != null) new File(app.getFilesDir(), importedCover.path).delete();
                for (Media m : importedExtras) new File(app.getFilesDir(), m.path).delete();
                cb.done(false);
            }
        });
    }

    /**
     * 仅调试构建:空库时灌入示例数据,方便预览 UI。
     * 示例记录 id 以 sample- 开头,可通过 {@link #clearSamples()} 清除;发布构建不会执行。
     */
    public void seedSamplesIfDebug() {
        boolean debuggable = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debuggable) return;
        SharedPreferences sp = app.getSharedPreferences("dev", Context.MODE_PRIVATE);
        if (sp.getBoolean("seeded_v10", false)) return;
        IO.execute(() -> {
            db.records().deleteSamples();
            db.records().insertAll(Record.sample());
            sp.edit().putBoolean("seeded_v10", true).apply();
        });
    }

    public void clearSamples() {
        IO.execute(() -> db.records().deleteSamples());
    }
}
