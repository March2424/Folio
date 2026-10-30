package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * "当天拼图"卡:2×2 —— 歌曲卡 / 照片 / 照片 / 歌词页。
 * M0 用渐变假装照片;之后换成真实位图,布局不变。
 */
public class CollageView extends View {
    private final Record r;
    private final Paint p = AppFonts.paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final float d;
    private final Bitmap[] photos = new Bitmap[2];

    public CollageView(Context c, Record r) {
        super(c);
        this.r = r;
        d = c.getResources().getDisplayMetrics().density;
        java.util.List<Media> list = MediaIndex.list(r.id, Media.PHOTO);
        for (int i = 0; i < 2 && !list.isEmpty(); i++) {
            final int slot = i;
            ImageLoader.load(c, list.get(Math.min(i, list.size() - 1)), 720, b -> {
                photos[slot] = b;
                invalidate();
            });
        }
    }

    /** 居中裁切绘制 */
    private void drawCrop(Canvas c, Bitmap b, float l, float t, float rt, float bt) {
        float dw = rt - l, dh = bt - t;
        float s = Math.max(dw / b.getWidth(), dh / b.getHeight());
        float sw = dw / s, sh = dh / s;
        int sl = Math.round((b.getWidth() - sw) / 2f), st = Math.round((b.getHeight() - sh) / 2f);
        c.drawBitmap(b, new Rect(sl, st, Math.round(sl + sw), Math.round(st + sh)),
                new RectF(l, t, rt, bt), null);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Math.round(w * 1.18f));
    }

    private int shade(int color, float f) {
        int a = color >>> 24, rr = (int) (((color >> 16) & 0xFF) * f),
                g = (int) (((color >> 8) & 0xFF) * f), b = (int) ((color & 0xFF) * f);
        return (a << 24) | (rr << 16) | (g << 8) | b;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cw = w / 2f, ch = h / 2f;
        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h), 6 * d, 6 * d, Path.Direction.CW);
        c.clipPath(clip);

        int tone = shade(r.c1, 0.95f);
        // 1 歌曲卡
        p.setShader(null);
        p.setColor(tone);
        c.drawRect(0, 0, cw, ch, p);
        RectF card = new RectF(cw * 0.14f, ch * 0.10f, cw * 0.90f, ch * 0.92f);
        p.setColor(shade(r.c1, 0.28f));
        c.drawRoundRect(card, 8 * d, 8 * d, p);
        float cx = card.centerX(), cr = card.width() * 0.40f, cy = card.top + card.width() * 0.46f;
        p.setShader(new RadialGradient(cx, cy, cr, new int[]{r.c2, shade(r.c2, 0.45f)},
                new float[]{0.25f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, cr, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Typeface.create(AppFonts.regular(), Typeface.BOLD));
        p.setTextSize(15 * d);
        c.drawText(r.title.length() > 7 ? r.title.substring(0, 7) : r.title,
                card.left + 10 * d, card.bottom - 30 * d, p);
        p.setTypeface(AppFonts.regular());
        p.setColor(0xCCFFFFFF);
        p.setTextSize(11 * d);
        c.drawText(r.sub, card.left + 10 * d, card.bottom - 12 * d, p);

        // 2 照片(暖天空)
        p.setShader(new LinearGradient(cw, 0, cw, ch,
                new int[]{shade(r.c2, 1.1f), r.c2, shade(r.c1, 0.35f), shade(r.c1, 0.15f)},
                new float[]{0f, 0.4f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(cw, 0, w, ch, p);
        if (photos[0] != null) drawCrop(c, photos[0], cw, 0, w, ch);

        // 3 照片(海面)
        p.setShader(new LinearGradient(0, ch, 0, h,
                new int[]{shade(r.c2, 0.9f), shade(r.c1, 0.55f), shade(r.c1, 0.3f)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, ch, cw, h, p);
        if (photos[1] != null) drawCrop(c, photos[1], 0, ch, cw, h);

        // 4 歌词页:模糊封面做底
        p.setShader(null);
        p.setColor(tone);
        c.drawRect(cw, ch, w, h, p);
        p.setShader(new RadialGradient(cw + cw * 0.5f, ch + ch * 0.35f, cw * 0.55f,
                new int[]{r.c2, 0x00000000}, new float[]{0.2f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(cw, ch, w, h, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Typeface.create(AppFonts.regular(), Typeface.NORMAL));
        p.setTextSize(17 * d);
        float x = cw + 14 * d, y = ch + 30 * d;
        String t = r.title;
        for (int i = 0; i < t.length() && i < 9; i += 3) {
            c.drawText(t.substring(i, Math.min(t.length(), i + 3)), x, y, p);
            y += 21 * d;
        }
        p.setTextSize(7.5f * d);
        p.setColor(0xF2FFFFFF);
        String[] lines = r.note.split("\n");
        y += 6 * d;
        for (int i = 0; i < lines.length && i < 5; i++) {
            c.drawText(lines[i].length() > 14 ? lines[i].substring(0, 14) : lines[i], x, y, p);
            y += 11.5f * d;
        }
        p.setTypeface(AppFonts.regular());
        p.setTextSize(8.5f * d);
        p.setColor(0xCCFFFFFF);
        c.drawText(r.date.substring(0, 4) + "  " + r.date.substring(5), w - 80 * d, ch + 16 * d, p);
    }
}
