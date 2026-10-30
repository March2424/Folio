package hk.hkucs.dontwannaforget;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.UUID;

/** 原图文件管理:复制进 App 私有目录,不依赖外部 URI,原文件(含 EXIF 方向)保持不变。 */
public final class MediaFiles {
    private MediaFiles() {}

    public static File file(Context c, Media m) {
        return new File(c.getFilesDir(), m.path);
    }

    /** 复制并生成 Media 描述(尚未入库)。失败抛出 IOException,且不留下半截文件。 */
    public static Media importUri(Context c, Uri uri, String recordId, String role, int sort)
            throws IOException {
        ContentResolver cr = c.getContentResolver();
        File dir = new File(c.getFilesDir(), "media");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create media dir");

        String id = UUID.randomUUID().toString();
        String mime = cr.getType(uri);
        String ext = "jpg";
        if (mime != null) {
            if (mime.contains("png")) ext = "png";
            else if (mime.contains("webp")) ext = "webp";
            else if (mime.contains("heic") || mime.contains("heif")) ext = "heic";
        }
        File tmp = new File(dir, id + ".tmp");
        File dst = new File(dir, id + "." + ext);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = cr.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                if (in == null) throw new IOException("cannot open uri");
                copy(in, out, md);
                out.getFD().sync();
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("unsupported image");
            if (!tmp.renameTo(dst)) throw new IOException("rename failed");

            Media m = new Media();
            m.id = id;
            m.recordId = recordId;
            m.path = "media/" + dst.getName();
            m.role = role;
            m.width = o.outWidth;
            m.height = o.outHeight;
            m.sha256 = hex(md.digest());
            m.sortOrder = sort;
            m.createdAt = System.currentTimeMillis();
            return m;
        } catch (IOException e) {
            tmp.delete();
            dst.delete();
            throw e;
        } catch (Exception e) {
            tmp.delete();
            dst.delete();
            throw new IOException(e);
        }
    }

    /** 导入用户从本地选择的录音/音频文件 URI 到 App 私有目录。durationSec 存入 width 字段。 */
    public static Media importAudioUri(Context c, Uri uri, String recordId, int durationSec)
            throws IOException {
        ContentResolver cr = c.getContentResolver();
        File dir = new File(c.getFilesDir(), "media");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create media dir");

        String id = UUID.randomUUID().toString();
        String mime = cr.getType(uri);
        String ext = "m4a";
        if (mime != null) {
            if (mime.contains("mpeg") || mime.contains("mp3")) ext = "mp3";
            else if (mime.contains("wav") || mime.contains("wave")) ext = "wav";
            else if (mime.contains("ogg")) ext = "ogg";
            else if (mime.contains("aac")) ext = "aac";
            else if (mime.contains("3gp")) ext = "3gp";
            else if (mime.contains("flac")) ext = "flac";
        }
        File tmp = new File(dir, id + ".tmp");
        File dst = new File(dir, id + "." + ext);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = cr.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                if (in == null) throw new IOException("cannot open audio uri");
                copy(in, out, md);
                out.getFD().sync();
            }
            if (tmp.length() <= 0) throw new IOException("empty audio file");
            if (!tmp.renameTo(dst)) throw new IOException("rename audio failed");

            Media m = new Media();
            m.id = id;
            m.recordId = recordId;
            m.path = "media/" + dst.getName();
            m.role = Media.AUDIO;
            m.width = Math.max(0, durationSec);
            m.height = 0;
            m.sha256 = hex(md.digest());
            m.sortOrder = 0;
            m.createdAt = System.currentTimeMillis();
            return m;
        } catch (IOException e) {
            tmp.delete();
            dst.delete();
            throw e;
        } catch (Exception e) {
            tmp.delete();
            dst.delete();
            throw new IOException(e);
        }
    }

    /** 导入应用内现场录制的音频文件到 App 私有目录。 */
    public static Media importAudioFile(Context c, File src, String recordId, int durationSec)
            throws IOException {
        if (src == null || !src.exists() || src.length() <= 0) {
            throw new IOException("missing recorded audio file");
        }
        File dir = new File(c.getFilesDir(), "media");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create media dir");

        String id = UUID.randomUUID().toString();
        File dst = new File(dir, id + ".m4a");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new java.io.FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                copy(in, out, md);
                out.getFD().sync();
            }
            Media m = new Media();
            m.id = id;
            m.recordId = recordId;
            m.path = "media/" + dst.getName();
            m.role = Media.AUDIO;
            m.width = Math.max(0, durationSec);
            m.height = 0;
            m.sha256 = hex(md.digest());
            m.sortOrder = 0;
            m.createdAt = System.currentTimeMillis();
            return m;
        } catch (Exception e) {
            dst.delete();
            throw new IOException(e);
        }
    }

    private static void copy(InputStream in, OutputStream out, MessageDigest md) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            md.update(buf, 0, n);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
