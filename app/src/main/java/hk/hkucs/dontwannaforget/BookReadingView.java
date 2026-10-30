package hk.hkucs.dontwannaforget;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** One continuous sheet: photos, excerpt and reflection, with no fixed reading height. */
public class BookReadingView extends FrameLayout {
    public interface OnOpenPhoto { void open(Media media); }

    public BookReadingView(Context context, Record record, List<Media> photos, OnOpenPhoto onOpenPhoto) {
        super(context);
        BookNoteStyle style = BookNoteStyle.fromRecord(record);
        BookNoteView paper = new BookNoteView(context);
        paper.setId(R.id.book_reading_paper);
        paper.bind(record, style, false);
        paper.setPaperOnly(true);
        // FrameLayout measures the content first, then stretches this background to its full height.
        addView(paper, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        int ink = paper.getReadingTextColor();
        int secondary = paper.getReadingSecondaryColor();
        Typeface bodyFont = FontManager.resolve(context, style.fontId);
        LinearLayout content = new LinearLayout(context);
        content.setId(R.id.book_reading_content);
        content.setOrientation(LinearLayout.VERTICAL);
        int left = style.noteStyle == BookNoteStyle.STYLE_GRID_PAPERCLIP ? 36 : 20;
        int top = style.noteStyle == BookNoteStyle.STYLE_BINDER_TORN ? 38 : 30;
        content.setPadding(dp(left), dp(top), dp(20), dp(80));

        if (!photos.isEmpty()) {
            LinearLayout images = new LinearLayout(context);
            images.setId(R.id.book_reading_images);
            images.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < photos.size(); i++) {
                Media media = photos.get(i);
                FrameLayout photo = buildPhoto(media, i, secondary, onOpenPhoto);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) lp.topMargin = dp(12);
                images.addView(photo, lp);
            }
            content.addView(images, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(10), photos.isEmpty() ? dp(8) : dp(28), dp(10), 0);
        TextView title = text(record.title, 18, ink, AppFonts.regular());
        title.setId(R.id.book_reading_title);
        text.addView(title, new LinearLayout.LayoutParams(-1, -2));
        if (record.sub != null && !record.sub.trim().isEmpty()) {
            TextView author = text(record.sub.trim(), 12, secondary, AppFonts.regular());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            text.addView(author, lp);
        }

        // Do not use resolveCardDisplayText: its optional reflection belongs only to the outer card.
        if (record.note != null && !record.note.trim().isEmpty()) {
            TextView quote = text(record.note, Math.max(16, style.textSizeSp), ink, bodyFont);
            quote.setId(R.id.book_reading_quote);
            quote.setTextIsSelectable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(30);
            text.addView(quote, lp);
        }
        if (style.thought != null && !style.thought.trim().isEmpty()) {
            TextView thought = text(style.thought, Math.max(15, style.textSizeSp - 1), ink, bodyFont);
            thought.setId(R.id.book_reading_thought);
            thought.setTextIsSelectable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(28);
            text.addView(thought, lp);
        }
        if (record.date != null && !record.date.isEmpty()) {
            TextView date = text(record.date, 11, secondary, AppFonts.regular());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(32);
            text.addView(date, lp);
        }
        content.addView(text, new LinearLayout.LayoutParams(-1, -2));
        addView(content, new LayoutParams(-1, -2));
    }

    private FrameLayout buildPhoto(Media media, int index, int ink, OnOpenPhoto onOpenPhoto) {
        FrameLayout frame = new FrameLayout(getContext());
        ImageView image = new ImageView(getContext());
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(index == 0 ? L10n.s(R.string.ui_047) : L10n.s(R.string.ui_048) + index);
        frame.addView(image, new LayoutParams(-1, -2));
        TextView status = text(L10n.s(R.string.ui_049), 12, ink, AppFonts.regular());
        status.setGravity(Gravity.CENTER);
        status.setMinHeight(dp(160));
        frame.addView(status, new LayoutParams(-1, -2));
        loadPhoto(frame, image, status, media, onOpenPhoto);
        return frame;
    }

    private void loadPhoto(FrameLayout frame, ImageView image, TextView status,
                           Media media, OnOpenPhoto onOpenPhoto) {
        status.setText(L10n.s(R.string.ui_049));
        ImageLoader.load(getContext(), media, 1280, bitmap -> {
            if (bitmap == null) {
                image.setVisibility(View.GONE);
                status.setVisibility(View.VISIBLE);
                status.setText(L10n.s(R.string.ui_050));
                frame.setOnClickListener(v -> loadPhoto(frame, image, status, media, onOpenPhoto));
            } else {
                image.setImageBitmap(bitmap);
                image.setVisibility(View.VISIBLE);
                status.setVisibility(View.GONE);
                frame.setOnClickListener(v -> {
                    if (onOpenPhoto != null) onOpenPhoto.open(media);
                });
            }
        });
    }

    private TextView text(String value, float size, int color, Typeface font) {
        TextView text = Cards.text(getContext(), value != null ? value : "", size, color, font);
        text.setLineSpacing(0, 1.6f);
        return text;
    }

    private int dp(float value) { return Cards.dp(getContext(), value); }
}
