package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步图片加载:内存 LruCache → 磁盘缩略图 → 按目标尺寸采样解码原图。
 * 不全量解码原图;缩略图写入 cacheDir,可随时重建;处理 EXIF 方向。
 */
public final class ImageLoader {
    private ImageLoader() {}

    public interface Cb { void done(Bitmap b); }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
            };

    private static int bucket(int px) {
        if (px <= 400) return 360;
        if (px <= 800) return 720;
        return 2048;
    }

    /** 放入 ImageView;回收复用时通过 tag 防止错位。无图或失败保持原背景。 */
    public static void into(ImageView v, Media m, int targetPx) {
        int b = bucket(targetPx);
        String key = m.id + "_" + b;
        v.setTag(key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) { v.setImageBitmap(hit); return; }
        v.setImageBitmap(null);
        load(v.getContext(), m, targetPx, bmp -> {
            if (bmp != null && key.equals(v.getTag())) v.setImageBitmap(bmp);
        });
    }

    public static void load(Context ctx, Media m, int targetPx, Cb cb) {
        Context app = ctx.getApplicationContext();
        int b = bucket(targetPx);
        String key = m.id + "_" + b;
        Bitmap hit = CACHE.get(key);
        if (hit != null) { cb.done(hit); return; }
        POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                bmp = loadSync(app, m, b, key);
            } catch (Throwable ignored) { /* 失败时回调 null,界面保留占位 */ }
            Bitmap result = bmp;
            MAIN.post(() -> cb.done(result));
        });
    }

    private static Bitmap loadSync(Context c, Media m, int b, String key) throws Exception {
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        File thumb = new File(new File(c.getCacheDir(), "thumbs"), key + ".jpg");
        if (b < 2048 && thumb.exists()) {
            Bitmap t = BitmapFactory.decodeFile(thumb.getPath());
            if (t != null) { CACHE.put(key, t); return t; }
        }
        File src = MediaFiles.file(c, m);
        Bitmap bmp = decode(src, b);
        if (bmp == null) return null;
        if (b < 2048) {
            thumb.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(thumb)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            } catch (Exception ignored) { thumb.delete(); }
        }
        CACHE.put(key, bmp);
        return bmp;
    }

    private static Bitmap decode(File f, int maxSide) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), o);
        if (bmp == null) return null;

        int orient = new ExifInterface(f.getPath())
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        Matrix mx = new Matrix();
        switch (orient) {
            case ExifInterface.ORIENTATION_ROTATE_90: mx.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: mx.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: mx.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: mx.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: mx.postScale(1, -1); break;
            default: return bmp;
        }
        Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mx, true);
        if (r != bmp) bmp.recycle();
        return r;
    }
}
