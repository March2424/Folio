package hk.hkucs.dontwannaforget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.List;

/** 详情页:票根正面(封面)点击翻转到背面(电子票根);声音类型提供复古磁带播放与录音上传;其他类型直接展示卡片。 */
public class DetailActivity extends AppCompatActivity {
    private static final int W = 300, H = 440, SPLIT = 290;

    private View front, back;
    private FrameLayout flip;
    private boolean showingFront = true, busy = false;
    private String recordId, pendingRole = Media.COVER;
    private Repo repo;
    private ActivityResultLauncher<PickVisualMediaRequest> picker;
    private ActivityResultLauncher<String[]> audioPicker;

    // 声音播放状态
    private MediaPlayer voicePlayer;
    private AudioTrack synthTrack;
    private boolean isVoicePlaying = false;
    private boolean isVoiceMuted = false;
    private long synthStartMs = 0L;
    private int synthDurationSec = 30;
    private VoiceTapeView activeTapeView;
    private TextView activePlayBtn, activeTimeLabel, activeSpeakerBtn;
    private android.widget.SeekBar activeSeekBar;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static String formatShortTime(int totalSeconds) {
        int m = Math.max(0, totalSeconds) / 60;
        int s = Math.max(0, totalSeconds) % 60;
        return String.format(java.util.Locale.US, "%d:%02d", m, s);
    }

    private final Runnable voiceTicker = new Runnable() {
        @Override
        public void run() {
            if (!isVoicePlaying) return;
            if (voicePlayer != null) {
                try {
                    int pos = voicePlayer.getCurrentPosition();
                    int total = Math.max(1, voicePlayer.getDuration());
                    float prog = Math.max(0f, Math.min(1f, pos / (float) total));
                    if (activeTapeView != null) activeTapeView.setProgress(prog);
                    if (activeSeekBar != null) activeSeekBar.setProgress(Math.round(prog * 1000f));
                    if (activeTimeLabel != null) {
                        activeTimeLabel.setText(formatShortTime(pos / 1000) + " / " + formatShortTime(total / 1000));
                    }
                    handler.postDelayed(this, 100);
                } catch (Exception ignored) {}
            } else {
                long elapsedMs = System.currentTimeMillis() - synthStartMs;
                int totalMs = Math.max(1000, synthDurationSec * 1000);
                if (elapsedMs >= totalMs) {
                    stopVoicePlayback();
                    return;
                }
                float prog = Math.max(0f, Math.min(1f, elapsedMs / (float) totalMs));
                if (activeTapeView != null) activeTapeView.setProgress(prog);
                if (activeSeekBar != null) activeSeekBar.setProgress(Math.round(prog * 1000f));
                if (activeTimeLabel != null) {
                    activeTimeLabel.setText(formatShortTime((int) (elapsedMs / 1000))
                            + " / " + formatShortTime(synthDurationSec));
                }
                handler.postDelayed(this, 100);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
        repo = new Repo(this);
        recordId = getIntent().getStringExtra("id");
        picker = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null || recordId == null) return;
            repo.addMedia(uri, recordId, pendingRole, ok -> runOnUiThread(() -> {
                if (!ok) {
                    Toast.makeText(this, L10n.s(R.string.ui_056), Toast.LENGTH_SHORT).show();
                    return;
                }
                reload();
            }));
        });
        audioPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || recordId == null) return;
            onNewAudioPicked(uri);
        });
        setContentView(new View(this));
    }

    private void onNewAudioPicked(Uri uri) {
        stopVoicePlayback();
        int sec = 15;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this, uri);
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durStr != null) sec = Math.max(1, Math.round(Long.parseLong(durStr) / 1000f));
        } catch (Exception ignored) {
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        final int finalSec = sec;
        repo.addAudio(uri, recordId, finalSec, ok -> {
            if (!ok) {
                runOnUiThread(() -> Toast.makeText(this, L10n.s(R.string.ui_057), Toast.LENGTH_SHORT).show());
                return;
            }
            repo.get(recordId, r -> {
                if (r != null) {
                    r.fields = new String[]{VoiceEditActivity.formatDuration(finalSec), L10n.s(R.string.ui_058)};
                    repo.save(r, () -> runOnUiThread(() -> {
                        Toast.makeText(this, L10n.s(R.string.ui_059), Toast.LENGTH_SHORT).show();
                        reload();
                    }));
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopVoicePlayback();
    }

    private void reload() {
        repo.get(recordId, r -> runOnUiThread(() -> {
            if (r == null) { finish(); return; }
            show(r);
        }));
    }

    private void openFootprintEditor(Record r) {
        Intent i = new Intent(this, FootprintEditActivity.class);
        i.putExtra("id", r.id);
        startActivity(i);
    }

    private void openTicketEditor(Record r) {
        Intent i = new Intent(this, TicketEditActivity.class);
        i.putExtra("id", r.id);
        startActivity(i);
    }

    private void openBookEditor(Record r) {
        Intent i = new Intent(this, BookEditActivity.class);
        i.putExtra("id", r.id);
        startActivity(i);
    }

    private void openMusicEditor(Record r) {
        Intent i = new Intent(this, MusicEditActivity.class);
        i.putExtra("id", r.id);
        startActivity(i);
    }

    private void pickImage(String role) {
        pendingRole = role;
        picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void openViewer(Media m) {
        Intent i = new Intent(this, ImageViewerActivity.class);
        i.putExtra("id", m.id);
        i.putExtra("path", m.path);
        startActivity(i);
    }

    private void show(Record r) {
        stopVoicePlayback();
        if (r.type == Record.FOOTPRINT) {
            buildFootprint(r);
            return;
        }
        if (r.type == Record.VOICE) {
            buildVoiceDetail(r);
            return;
        }
        if (r.type == Record.BOOK) {
            buildBookDetail(r);
            return;
        }
        if (r.type == Record.MUSIC) {
            buildMusicDetail(r);
            return;
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(true);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        sv.setBackgroundColor(FloatingHeader.DETAIL_BACKGROUND);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Cards.dp(this, 24);
        col.setPadding(pad, pad, pad, pad * 6);
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        setContentView(sv);
        ViewCompat.setOnApplyWindowInsetsListener(sv, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(sv);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, Cards.dp(this, 16));
        TextView backBtn = Cards.text(this, L10n.s(R.string.ui_060), 14, 0xFFA7A39B, AppFonts.regular());
        backBtn.setOnClickListener(v -> finish());
        HomeNavigation.bind(backBtn);
        topBar.addView(backBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        if (r.type == Record.TICKET) {
            TextView addOrig = Cards.text(this, L10n.s(R.string.ui_061), 13, getColor(R.color.wine), AppFonts.regular());
            addOrig.setOnClickListener(v -> pickImage(Media.ORIGINAL));
            topBar.addView(addOrig);

            TextView editTicketBtn = Cards.text(this, L10n.s(R.string.ui_062), 13, Color.WHITE, AppFonts.regular());
            editTicketBtn.setPadding(Cards.dp(this, 12), Cards.dp(this, 6), Cards.dp(this, 12), Cards.dp(this, 6));
            GradientDrawable ebg = new GradientDrawable();
            ebg.setColor(getColor(R.color.wine));
            ebg.setCornerRadius(Cards.dp(this, 14));
            editTicketBtn.setBackground(ebg);
            editTicketBtn.setOnClickListener(v -> openTicketEditor(r));
            topBar.addView(editTicketBtn);
        } else {
            TextView addBtn = Cards.text(this, L10n.s(R.string.ui_063), 13, getColor(R.color.wine), AppFonts.regular());
            addBtn.setOnClickListener(v -> pickImage(Media.COVER));
            topBar.addView(addBtn);
        }
        col.addView(topBar, new LinearLayout.LayoutParams(-1, -2));
        FloatingHeader.detailRow(topBar,backBtn,false);
        FloatingHeader.pin(topBar);

        if (r.type == Record.TICKET) {
            buildFlip(col, r);
            TextView hint = Cards.text(this, L10n.s(R.string.ui_064), 11,
                    0xFFA7A39B, AppFonts.regular());
            hint.setPadding(0, Cards.dp(this, 14), 0, 0);
            col.addView(hint);
        } else if (r.type == Record.MUSIC) {
            col.addView(Cards.musicCard(this, r, 300, false));
        } else if (r.type == Record.BOOK) {
            col.addView(Cards.bookCard(this, r, 200));
        }

        String body = r.note;
        if (r.type == Record.MUSIC) body = r.note.replace("\n", " ");
        if (r.type == Record.BOOK) {
            TextView meta = Cards.text(this, "《" + r.title + "》 · " + r.sub, 14,
                    0xFFA7A39B, AppFonts.regular());
            meta.setPadding(0, Cards.dp(this, 24), 0, 0);
            col.addView(meta);
        }
        TextView note = Cards.text(this, body, 15, FloatingHeader.DETAIL_TEXT, AppFonts.regular());
        note.setLineSpacing(0, 1.6f);
        note.setPadding(Cards.dp(this, 8), Cards.dp(this, r.type == Record.TICKET ? 28 : 20),
                Cards.dp(this, 8), 0);
        col.addView(note);

        // 展示票根原件或附图缩略(点击可放大查看完整原图)
        String thumbRole = r.type == Record.TICKET ? Media.ORIGINAL : Media.PHOTO;
        List<Media> originals = MediaIndex.list(r.id, thumbRole);
        if (!originals.isEmpty()) {
            TextView origTitle = Cards.text(this,
                    r.type == Record.TICKET ? L10n.s(R.string.ui_065) : L10n.s(R.string.ui_066),
                    11, 0xFFA7A39B, AppFonts.regular());
            origTitle.setPadding(0, Cards.dp(this, 24), 0, Cards.dp(this, 8));
            col.addView(origTitle, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            for (Media m : originals) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setBackgroundColor(0xFFEAE5DC);
                int p = Cards.dp(this, 4);
                iv.setPadding(p, p, p, p);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Cards.dp(this, 120), Cards.dp(this, 150));
                lp.rightMargin = Cards.dp(this, 10);
                strip.addView(iv, lp);
                ImageLoader.into(iv, m, 480);
                iv.setOnClickListener(v -> openViewer(m));
            }
            col.addView(strip, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /** Images, excerpt and reflection share one growing paper sheet (example/IMG_3940.jpg). */
    private void buildBookDetail(Record r) {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(FloatingHeader.DETAIL_BACKGROUND);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Cards.dp(this, 12);
        content.setPadding(padding, Cards.dp(this, 6), padding, Cards.dp(this, 32));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        setContentView(scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(scroll);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Cards.text(this, L10n.s(R.string.ui_060), 14, 0xFFC7C2B8, AppFonts.regular());
        back.setMinHeight(Cards.dp(this, 48));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> finish());
        HomeNavigation.bind(back);
        toolbar.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = Cards.text(this, L10n.s(R.string.ui_067), 12, 0xFFD8C690, AppFonts.regular());
        edit.setId(R.id.book_edit_entry);
        edit.setSingleLine(true);
        edit.setGravity(Gravity.CENTER);
        edit.setMinHeight(Cards.dp(this, 48));
        edit.setPadding(Cards.dp(this, 8), 0, Cards.dp(this, 8), 0);
        edit.setOnClickListener(v -> openBookEditor(r));
        toolbar.addView(edit);
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        FloatingHeader.detailRow(toolbar,back,false);
        FloatingHeader.pin(toolbar);

        java.util.ArrayList<Media> photos = new java.util.ArrayList<>();
        Media cover = MediaIndex.first(r.id, Media.COVER);
        if (cover != null) photos.add(cover);
        for (Media photo : MediaIndex.list(r.id, Media.PHOTO)) {
            if (cover == null || !photo.id.equals(cover.id)) photos.add(photo);
        }
        BookReadingView reading = new BookReadingView(this, r, photos, this::openViewer);
        content.addView(reading, new LinearLayout.LayoutParams(-1, -2));
    }

    /**
     * 音乐歌词卡片详情页 (模仿 example/IMG_3870.jpg):
     * - 大幅展示模糊背景音乐歌词卡片 (无底部 Spotify 标志);
     * - 通过“修改音乐与卡片样式”统一进入编辑器，展示页不直接修改内容或样式;
     * - 下方展示听歌随感与清晰原图预览。
     */
    private void buildMusicDetail(Record r) {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        sv.setBackgroundColor(FloatingHeader.DETAIL_BACKGROUND);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Cards.dp(this, 18);
        col.setPadding(pad, Cards.dp(this, 14), pad, Cards.dp(this, 64));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        setContentView(sv);
        ViewCompat.setOnApplyWindowInsetsListener(sv, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(sv);

        ViewCompat.requestApplyInsets(sv);

        // 1. 展示页只保留统一修改入口。
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, Cards.dp(this, 12));

        TextView backBtn = Cards.text(this, L10n.s(R.string.ui_060), 15f, 0xFFC7C2B8, AppFonts.regular());
        backBtn.setMinHeight(Cards.dp(this, 48));
        backBtn.setGravity(Gravity.CENTER_VERTICAL);
        backBtn.setOnClickListener(v -> finish());
        HomeNavigation.bind(backBtn);
        topBar.addView(backBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView editMusicBtn = Cards.text(this, L10n.s(R.string.ui_068), 12, 0xFFD8C690, AppFonts.regular());
        editMusicBtn.setId(R.id.music_edit_entry);
        editMusicBtn.setSingleLine(true);
        editMusicBtn.setMinHeight(Cards.dp(this, 48));
        editMusicBtn.setGravity(Gravity.CENTER);
        editMusicBtn.setPadding(Cards.dp(this, 8), 0, Cards.dp(this, 8), 0);
        editMusicBtn.setOnClickListener(v -> openMusicEditor(r));
        topBar.addView(editMusicBtn);

        col.addView(topBar, new LinearLayout.LayoutParams(-1, -2));
        FloatingHeader.detailRow(topBar,backBtn,false);
        FloatingHeader.pin(topBar);

        final MusicCardStyle style = MusicCardStyle.fromRecord(r);

        // 2. 核心展示：仿 IMG_3870.jpg 音乐歌词卡片 (无底部 Spotify 标志)
        int cardW=Math.min(292,Math.round(getResources().getDisplayMetrics().widthPixels
                /getResources().getDisplayMetrics().density)-60);
        MusicLyricCardView cardView = Cards.musicCard(this, r, cardW, false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                Cards.dp(this, cardW),
                Math.round(Cards.dp(this, cardW) * cardView.computeHeightRatio()));
        clp.topMargin=Cards.dp(this,12);
        col.addView(cardView, clp);
        col.addOnLayoutChangeListener((v,l,t,right,b,ol,ot,or,ob)->{
            int available=right-l-col.getPaddingLeft()-col.getPaddingRight()-Cards.dp(this,24);
            if(available<=0)return;
            int width=Math.min(Cards.dp(this,292),available);
            if(cardView.getLayoutParams().width!=width){
                cardView.getLayoutParams().width=width;
                cardView.getLayoutParams().height=Math.round(width*cardView.computeHeightRatio());
                cardView.requestLayout();
            }
        });

        // 3. 若有听歌随感/心情备注，展示在歌词卡片下方
        if (style.thought != null && !style.thought.trim().isEmpty()) {
            LinearLayout thoughtCard = new LinearLayout(this);
            thoughtCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(0xFF202221);
            tbg.setCornerRadius(Cards.dp(this, 12));
            tbg.setStroke(Cards.dp(this, 1), 0xFF323533);
            thoughtCard.setBackground(tbg);
            int tp = Cards.dp(this, 16);
            thoughtCard.setPadding(tp, tp, tp, tp);

            TextView thoughtLabel = Cards.text(this, L10n.s(R.string.ui_069), 11f, 0xFFD8C690, AppFonts.regular());
            thoughtLabel.setPadding(0, 0, 0, Cards.dp(this, 6));
            thoughtCard.addView(thoughtLabel);

            TextView thoughtBody = Cards.text(this, style.thought.trim(), 14.5f, 0xFFEAE6DF, AppFonts.regular());
            thoughtBody.setLineSpacing(0f, 1.5f);
            thoughtCard.addView(thoughtBody);

            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Cards.dp(this, cardW), -2);
            tlp.topMargin = Cards.dp(this, 16);
            col.addView(thoughtCard, tlp);
        }

        // 4. 若已上传封面原图，支持点击查看未模糊的清晰原图
        Media cover = MediaIndex.first(r.id, Media.COVER);
        if (cover != null) {
            LinearLayout origRow = new LinearLayout(this);
            origRow.setOrientation(LinearLayout.HORIZONTAL);
            origRow.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable obg = new GradientDrawable();
            obg.setColor(0xFF1E201F);
            obg.setCornerRadius(Cards.dp(this, 10));
            obg.setStroke(Cards.dp(this, 1), 0xFF2E3130);
            origRow.setBackground(obg);
            int op = Cards.dp(this, 10);
            origRow.setPadding(op, op, op, op);

            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(Cards.dp(this, 48), Cards.dp(this, 48));
            ivLp.rightMargin = Cards.dp(this, 12);
            origRow.addView(iv, ivLp);
            ImageLoader.into(iv, cover, 240);

            TextView origInfo = Cards.text(this, L10n.s(R.string.ui_070), 12.5f, 0xFFC7C2B8, AppFonts.regular());
            origRow.addView(origInfo, new LinearLayout.LayoutParams(0, -2, 1f));

            origRow.setOnClickListener(v -> openViewer(cover));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(Cards.dp(this, cardW), -2);
            olp.topMargin = Cards.dp(this, 14);
            col.addView(origRow, olp);
        }

        // 5. 底部归档曲目、歌手与日期
        String musicMeta = (r.title != null ? r.title : "")
                + (r.sub != null && !r.sub.trim().isEmpty() ? (" · " + r.sub.trim()) : "")
                + "   ·   " + (r.date != null ? r.date : "");
        TextView tvFooter = Cards.text(this, musicMeta, 12f, 0xFF8E8A82, AppFonts.regular());
        tvFooter.setGravity(Gravity.CENTER);
        tvFooter.setPadding(0, Cards.dp(this, 18), 0, Cards.dp(this, 8));
        col.addView(tvFooter);
    }

    /**
     * 声音详情页 (仿 example/voiceexp1.jpg 与 example/voiceexp2.jpg 的暗黑背景适配版):
     * - 纯黑/深炭底色 (#111212) + 右上角极简 ✕ 关闭键
     * - 顶部左对齐标题与采集人/场景 ("r.title by r.sub")
     * - 大幅定格场景画面照片 (自适应竖幅或横幅构图)
     * - 照片正下方紧贴深灰胶囊音频播放条 (#222524): ▶/❚❚ + 0:38 / 9:54 + 进度滑块 + 🔊 静音键 + ⋮ 更多菜单
     * - 胶囊条下方左对齐分段呈现地点引语、声音背后的故事与录音来源归档信息
     */
    private void buildVoiceDetail(Record r) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        sv.setBackgroundColor(0xFF111212);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int padH = Cards.dp(this, 22);
        col.setPadding(padH, Cards.dp(this, 14), padH, Cards.dp(this, 96));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        setContentView(sv);

        ViewCompat.setOnApplyWindowInsetsListener(sv, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(sv);

        // 1. 顶部操作行: 右上角 ✕ 关闭按钮 (仿 voiceexp1.jpg / voiceexp2.jpg) + 左侧低饱和度快捷操作
        LinearLayout closeRow = new LinearLayout(this);
        closeRow.setOrientation(LinearLayout.HORIZONTAL);
        closeRow.setGravity(Gravity.CENTER_VERTICAL);
        closeRow.setPadding(0, Cards.dp(this, 4), 0, Cards.dp(this, 10));

        TextView actionAudio = Cards.text(this, L10n.s(R.string.ui_071), 11.5f, 0xFF8E8A82, AppFonts.regular());
        actionAudio.setPadding(0, Cards.dp(this, 6), Cards.dp(this, 14), Cards.dp(this, 6));
        actionAudio.setOnClickListener(v -> audioPicker.launch(new String[]{"audio/*", "application/ogg", "*/*"}));
        closeRow.addView(actionAudio);

        TextView actionPhoto = Cards.text(this, L10n.s(R.string.ui_072), 11.5f, 0xFF8E8A82, AppFonts.regular());
        actionPhoto.setPadding(0, Cards.dp(this, 6), Cards.dp(this, 14), Cards.dp(this, 6));
        actionPhoto.setOnClickListener(v -> pickImage(Media.COVER));
        closeRow.addView(actionPhoto);

        View spacer = new View(this);
        closeRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView closeBtn = Cards.text(this, L10n.s(R.string.ui_060), 13f, 0xFFE8E4DC, AppFonts.regular());
        closeBtn.setPadding(Cards.dp(this, 10), Cards.dp(this, 2), Cards.dp(this, 4), Cards.dp(this, 2));
        closeBtn.setOnClickListener(v -> finish());
        HomeNavigation.bind(closeBtn);
        closeRow.addView(closeBtn);
        col.addView(closeRow, new LinearLayout.LayoutParams(-1, -2));
        FloatingHeader.detailRow(closeRow,closeBtn,true);
        FloatingHeader.pin(closeRow);

        // A quiet editorial heading: the recording title and a separate place signature.
        TextView tvHeader=Cards.text(this,r.title!=null?r.title:"Untitled Voice",17,0xFFF4F2EE,AppFonts.regular());
        tvHeader.setIncludeFontPadding(false);
        tvHeader.setLineSpacing(Cards.dp(this,3),1f);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.topMargin=Cards.dp(this,8);titleLp.bottomMargin=Cards.dp(this,14);
        col.addView(tvHeader,titleLp);
        if(r.sub!=null&&!r.sub.trim().isEmpty()){
            LinearLayout signature=new LinearLayout(this);
            signature.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            View rule=new View(this);rule.setBackgroundColor(0x667B847D);
            LinearLayout.LayoutParams ruleLp=new LinearLayout.LayoutParams(Cards.dp(this,24),Cards.dp(this,1));
            ruleLp.rightMargin=Cards.dp(this,12);signature.addView(rule,ruleLp);
            LinearLayout place=new LinearLayout(this);place.setOrientation(LinearLayout.VERTICAL);
            place.setGravity(Gravity.END);
            String[] parts=r.sub.trim().split("\\s*[·•]\\s*",2);
            if(parts.length==2&&!parts[0].trim().isEmpty()&&!parts[1].trim().isEmpty()){
                TextView region=Cards.text(this,parts[0].trim(),10,0xFF92988F,AppFonts.regular());
                region.setIncludeFontPadding(false);region.setGravity(Gravity.END);region.setLetterSpacing(.12f);
                place.addView(region,new LinearLayout.LayoutParams(-1,-2));
                TextView location=Cards.text(this,parts[1].trim(),12,0xFFCCCBC2,AppFonts.regular());
                location.setIncludeFontPadding(false);location.setGravity(Gravity.END);location.setLineSpacing(Cards.dp(this,2),1);
                LinearLayout.LayoutParams locationLp=new LinearLayout.LayoutParams(-1,-2);locationLp.topMargin=Cards.dp(this,4);
                place.addView(location,locationLp);
            }else{
                TextView location=Cards.text(this,r.sub.trim(),12,0xFFCCCBC2,AppFonts.regular());
                location.setIncludeFontPadding(false);location.setGravity(Gravity.END);location.setLineSpacing(Cards.dp(this,2),1);
                place.addView(location,new LinearLayout.LayoutParams(-1,-2));
            }
            // Weight bounds long place names to the available width without clipping them.
            signature.addView(place,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams signatureLp=new LinearLayout.LayoutParams(-1,-2);
            signatureLp.leftMargin=Cards.dp(this,48);signatureLp.bottomMargin=Cards.dp(this,20);
            col.addView(signature,signatureLp);
        }

        // 3. 大幅场景照片 (自适应竖图或横图比例, 仿 voiceexp1.jpg 竖图 / voiceexp2.jpg 横图)
        ImageView photoView = new ImageView(this);
        photoView.setAdjustViewBounds(true);
        photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoView.setMaxHeight(Cards.dp(this, 470));
        photoView.setBackgroundColor(0xFF1A1C1B);

        Media coverMedia = MediaIndex.first(r.id, Media.COVER);
        if (coverMedia != null) {
            ImageLoader.into(photoView, coverMedia, 1080);
            photoView.setOnClickListener(v -> openViewer(coverMedia));
        } else {
            int[] fallbacks = {
                    R.drawable.voice_scene_2,
                    R.drawable.voice_scene_3,
                    R.drawable.voice_scene_1
            };
            int idx = Math.abs((r.title != null ? r.title : "").hashCode()) % fallbacks.length;
            photoView.setImageResource(fallbacks[idx]);
            photoView.setOnClickListener(v -> pickImage(Media.COVER));
        }
        LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(-1, -2);
        photoLp.bottomMargin = Cards.dp(this, 18);
        col.addView(photoView, photoLp);

        // 4. 胶囊形音频播放器控制条 (仿 voiceexp1.jpg / voiceexp2.jpg 的圆角胶囊条，适配暗黑背景 #222524)
        LinearLayout pillBar = new LinearLayout(this);
        pillBar.setOrientation(LinearLayout.HORIZONTAL);
        pillBar.setGravity(Gravity.CENTER_VERTICAL);
        int pillPadH = Cards.dp(this, 18);
        pillBar.setPadding(pillPadH, 0, pillPadH, 0);

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xFF222524);
        pillBg.setCornerRadius(Cards.dp(this, 28));
        pillBg.setStroke(Cards.dp(this, 1), 0xFF323635);
        pillBar.setBackground(pillBg);

        // 播放/暂停按钮
        activePlayBtn = Cards.text(this, "▶", 15f, 0xFFF4F2EE, AppFonts.regular());
        activePlayBtn.setGravity(Gravity.CENTER);
        activePlayBtn.setPadding(Cards.dp(this, 4), Cards.dp(this, 8), Cards.dp(this, 12), Cards.dp(this, 8));
        activePlayBtn.setOnClickListener(v -> toggleVoicePlayback(r));
        pillBar.addView(activePlayBtn);

        // 时间显示 ("0:00 / 0:42")
        int totalSec = parseDurationSeconds(r);
        activeTimeLabel = Cards.text(this, "0:00 / " + formatShortTime(totalSec), 13f, 0xFFE8E4DC, AppFonts.regular());
        activeTimeLabel.setPadding(0, 0, Cards.dp(this, 10), 0);
        pillBar.addView(activeTimeLabel);

        // 进度条 SeekBar
        activeSeekBar = new android.widget.SeekBar(this);
        activeSeekBar.setMax(1000);
        activeSeekBar.setProgress(0);
        activeSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFF4F2EE));
        activeSeekBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF555957));
        activeSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFF4F2EE));
        activeSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float frac = progress / 1000f;
                if (voicePlayer != null) {
                    try {
                        int targetMs = Math.round(frac * voicePlayer.getDuration());
                        voicePlayer.seekTo(targetMs);
                        activeTimeLabel.setText(formatShortTime(targetMs / 1000)
                                + " / " + formatShortTime(voicePlayer.getDuration() / 1000));
                    } catch (Exception ignored) {}
                } else if (isVoicePlaying) {
                    int totalMs = Math.max(1000, synthDurationSec * 1000);
                    synthStartMs = System.currentTimeMillis() - Math.round(frac * totalMs);
                } else {
                    activeTimeLabel.setText(formatShortTime(Math.round(frac * totalSec))
                            + " / " + formatShortTime(totalSec));
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(0, -2, 1f);
        pillBar.addView(activeSeekBar, sblp);

        // 扬声器/静音图标
        isVoiceMuted = false;
        activeSpeakerBtn = Cards.text(this, "🔊", 14f, 0xFFE8E4DC, AppFonts.regular());
        activeSpeakerBtn.setPadding(Cards.dp(this, 8), Cards.dp(this, 8), Cards.dp(this, 8), Cards.dp(this, 8));
        activeSpeakerBtn.setOnClickListener(v -> toggleVoiceMute());
        pillBar.addView(activeSpeakerBtn);

        // 三点更多菜单 (⋮)
        TextView moreBtn = Cards.text(this, "⋮", 18f, 0xFFE8E4DC,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        moreBtn.setPadding(Cards.dp(this, 8), Cards.dp(this, 6), Cards.dp(this, 4), Cards.dp(this, 6));
        moreBtn.setOnClickListener(v -> showVoiceMoreMenu(r));
        pillBar.addView(moreBtn);

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-1, Cards.dp(this, 54));
        pillLp.bottomMargin = Cards.dp(this, 24);
        col.addView(pillBar, pillLp);

        // The location appears once above the photograph; the story follows the player.
        String storyBody = (r.note != null && !r.note.trim().isEmpty())
                ? r.note.trim()
                : L10n.s(R.string.ui_073);
        TextView tvBody = Cards.text(this, storyBody, 15f, 0xFFF0ECE4, AppFonts.regular());
        tvBody.setLineSpacing(0f, 1.45f);
        tvBody.setPadding(0, 0, 0, Cards.dp(this, 22));
        col.addView(tvBody, new LinearLayout.LayoutParams(-1, -2));

        String srcTag = (r.fields != null && r.fields.length > 1 && !r.fields[1].isEmpty())
                ? r.fields[1] : L10n.s(R.string.ui_074);
        String dateFmt = (r.date != null ? r.date.replace('.', '-') : "");
        TextView tvCredit = Cards.text(this,
                "Recorded on " + dateFmt + "  ·  " + srcTag,
                13.5f, 0xFF8E8A82, AppFonts.regular());
        tvCredit.setLineSpacing(0f, 1.35f);
        col.addView(tvCredit, new LinearLayout.LayoutParams(-1, -2));
    }

    private void toggleVoiceMute() {
        isVoiceMuted = !isVoiceMuted;
        float vol = isVoiceMuted ? 0f : 1f;
        if (voicePlayer != null) {
            try { voicePlayer.setVolume(vol, vol); } catch (Exception ignored) {}
        }
        if (synthTrack != null) {
            try { synthTrack.setVolume(vol); } catch (Exception ignored) {}
        }
        if (activeSpeakerBtn != null) {
            activeSpeakerBtn.setText(isVoiceMuted ? "🔇" : "🔊");
        }
    }

    private void showVoiceMoreMenu(Record r) {
        String[] items = {L10n.s(R.string.ui_075), L10n.s(R.string.ui_076), L10n.s(R.string.ui_077)};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(r.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        audioPicker.launch(new String[]{"audio/*", "application/ogg", "*/*"});
                    } else if (which == 1) {
                        pickImage(Media.COVER);
                    } else if (which == 2) {
                        startActivity(new Intent(this, VoiceEditActivity.class));
                    }
                })
                .show();
    }

    private void toggleVoicePlayback(Record r) {
        if (isVoicePlaying) {
            stopVoicePlayback();
            return;
        }
        Media audioMedia = MediaIndex.first(r.id, Media.AUDIO);
        if (audioMedia != null) {
            File f = MediaFiles.file(this, audioMedia);
            if (f.exists()) {
                try {
                    voicePlayer = new MediaPlayer();
                    voicePlayer.setDataSource(f.getAbsolutePath());
                    voicePlayer.prepare();
                    float vol = isVoiceMuted ? 0f : 1f;
                    voicePlayer.setVolume(vol, vol);
                    voicePlayer.setOnCompletionListener(mp -> stopVoicePlayback());
                    voicePlayer.start();
                    isVoicePlaying = true;
                    if (activeTapeView != null) activeTapeView.setPlaying(true);
                    if (activePlayBtn != null) activePlayBtn.setText("❚❚");
                    handler.removeCallbacks(voiceTicker);
                    handler.post(voiceTicker);
                    return;
                } catch (Exception e) {
                    stopVoicePlayback();
                    Toast.makeText(this, L10n.s(R.string.ui_078), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        // 示例记录或尚未上传外部音频时: 合成温和的环境海浪/留声机氛围音
        startAmbientSamplePlayback(r);
    }

    private void startAmbientSamplePlayback(Record r) {
        synthDurationSec = parseDurationSeconds(r);
        synthStartMs = System.currentTimeMillis();
        isVoicePlaying = true;
        if (activeTapeView != null) activeTapeView.setPlaying(true);
        if (activePlayBtn != null) activePlayBtn.setText("❚❚");
        handler.removeCallbacks(voiceTicker);
        handler.post(voiceTicker);

        new Thread(() -> {
            int sampleRate = 22050;
            int totalSamples = sampleRate * 4; // 4 秒循环温润海浪与泛音
            short[] pcm = new short[totalSamples];
            java.util.Random rnd = new java.util.Random(r.title.hashCode());
            float lp = 0f;
            for (int i = 0; i < totalSamples; i++) {
                double t = i / (double) sampleRate;
                // 潮汐包络 + 柔和粉红噪声 + 温暖泛音
                double waveEnv = 0.25 + 0.35 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 0.25 * t));
                float white = (rnd.nextFloat() * 2f - 1f);
                lp = lp * 0.94f + white * 0.06f;
                double chime = 0.12 * Math.sin(2 * Math.PI * 261.63 * t) * Math.exp(-t * 0.8)
                        + 0.08 * Math.sin(2 * Math.PI * 329.63 * t) * Math.exp(-t * 0.6);
                double val = (lp * waveEnv + chime) * 14000;
                pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, val));
            }
            try {
                AudioTrack at = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                at.write(pcm, 0, pcm.length);
                at.setLoopPoints(0, totalSamples, -1);
                if (isVoiceMuted) at.setVolume(0f);
                synthTrack = at;
                if (isVoicePlaying) at.play();
            } catch (Exception ignored) {}
        }).start();
    }

    private static int parseDurationSeconds(Record r) {
        if (r.fields != null && r.fields.length > 0 && r.fields[0].contains(":")) {
            try {
                String[] p = r.fields[0].split(":");
                return Math.max(5, Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]));
            } catch (Exception ignored) {}
        }
        return 30;
    }

    private void stopVoicePlayback() {
        isVoicePlaying = false;
        handler.removeCallbacks(voiceTicker);
        if (voicePlayer != null) {
            try { voicePlayer.stop(); } catch (Exception ignored) {}
            try { voicePlayer.release(); } catch (Exception ignored) {}
            voicePlayer = null;
        }
        if (synthTrack != null) {
            try { synthTrack.stop(); } catch (Exception ignored) {}
            try { synthTrack.release(); } catch (Exception ignored) {}
            synthTrack = null;
        }
        if (activeTapeView != null) {
            activeTapeView.setPlaying(false);
            activeTapeView.setProgress(0f);
        }
        if (activeSeekBar != null) {
            activeSeekBar.setProgress(0);
        }
        if (activePlayBtn != null) {
            activePlayBtn.setText("▶");
        }
    }

    /** 足迹详情:展示类纸质背景+可移动文本框合成手账画布(参考 editexp1.jpg / editexp2.png),点击即可进入画布编辑器 */
    private void buildFootprint(Record r) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(true);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        sv.setBackgroundColor(FloatingHeader.DETAIL_BACKGROUND);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 20);
        col.setPadding(pad, Cards.dp(this, 8), pad, Cards.dp(this, 96));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        setContentView(sv);
        ViewCompat.setOnApplyWindowInsetsListener(sv, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(sv);

        int ink = FloatingHeader.DETAIL_TEXT, sub = 0xFFA7A39B;
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Cards.text(this, L10n.s(R.string.ui_060), 15, sub, AppFonts.regular());
        back.setOnClickListener(v -> finish());
        HomeNavigation.bind(back);
        bar.addView(back, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView editBtn = Cards.text(this, L10n.s(R.string.ui_079), 14, Color.WHITE, AppFonts.regular());
        editBtn.setPadding(Cards.dp(this, 14), Cards.dp(this, 7), Cards.dp(this, 14), Cards.dp(this, 7));
        android.graphics.drawable.GradientDrawable ebg = new android.graphics.drawable.GradientDrawable();
        ebg.setColor(getColor(R.color.wine));
        ebg.setCornerRadius(Cards.dp(this, 16));
        editBtn.setBackground(ebg);
        editBtn.setOnClickListener(v -> openFootprintEditor(r));
        bar.addView(editBtn);
        col.addView(bar, new LinearLayout.LayoutParams(-1, Cards.dp(this, 48)));
        FloatingHeader.detailRow(bar,back,false);
        FloatingHeader.pin(bar);

        TextView place = Cards.text(this, r.title, 19, ink, Typeface.create(AppFonts.regular(), Typeface.BOLD));
        place.setPadding(0, Cards.dp(this, 12), 0, Cards.dp(this, 2));
        col.addView(place);
        TextView meta = Cards.text(this, r.sub + "  ·  " + r.date + L10n.s(R.string.ui_080),
                12, sub, AppFonts.regular());
        meta.setPadding(0, 0, 0, Cards.dp(this, 18));
        col.addView(meta);

        List<Media> photos = MediaIndex.list(r.id, Media.PHOTO);
        int pageCount = Math.max(1, r.fields != null ? r.fields.length : 1);
        for (int i = 0; i < pageCount; i++) {
            FootprintCanvasView cv = new FootprintCanvasView(this);
            cv.setEditable(false);
            cv.setElevation(Cards.dp(this, 4));
            cv.setFallbackColors(i % 2 == 0 ? r.c1 : r.c2, i % 2 == 0 ? r.c2 : r.c1);
            String raw = (r.fields != null && i < r.fields.length) ? r.fields[i] : "";
            cv.setState(FootprintCanvasView.State.fromField(raw, r, i));
            if (i < photos.size()) {
                cv.setPhotoMedia(photos.get(i));
            } else if (!photos.isEmpty()) {
                cv.setPhotoMedia(photos.get(0));
            }
            cv.setOnClickListener(v -> openFootprintEditor(r));
            cv.setContentDescription(L10n.s(R.string.ui_081)+(i+1));
            LinearLayout.LayoutParams pageParams=new LinearLayout.LayoutParams(-1,-2);
            pageParams.bottomMargin=Cards.dp(this,20);
            col.addView(cv,pageParams);
        }
    }

    private View diamondDivider() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Cards.dp(this, 36), 0, 0);
        View l = new View(this), rr = new View(this);
        l.setBackgroundColor(0xFFD3CDC2);
        rr.setBackgroundColor(0xFFD3CDC2);
        TextView d = Cards.text(this, "◆", 12, 0xFF9AA3B2, AppFonts.regular());
        d.setPadding(Cards.dp(this, 12), 0, Cards.dp(this, 12), 0);
        row.addView(l, new LinearLayout.LayoutParams(0, Cards.dp(this, 1), 1f));
        row.addView(d);
        row.addView(rr, new LinearLayout.LayoutParams(0, Cards.dp(this, 1), 1f));
        return row;
    }

    private void buildFlip(LinearLayout col, Record r) {
        showingFront = true;
        flip = new FrameLayout(this);
        flip.setCameraDistance(8000f * getResources().getDisplayMetrics().density);

        int cardWidth=Math.min(288,Math.round(getResources().getDisplayMetrics().widthPixels
                /getResources().getDisplayMetrics().density)-64);
        front = Cards.ticketCard(this, r, cardWidth, false, false);
        back = Cards.ticketCard(this, r, cardWidth, true, false);
        back.setVisibility(View.GONE);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Cards.dp(this, cardWidth), -2, Gravity.CENTER);
        flip.addView(front, flp);
        flip.addView(back, new FrameLayout.LayoutParams(Cards.dp(this, cardWidth), -2, Gravity.CENTER));
        flip.setOnClickListener(v -> flipCard());
        col.addView(flip, new LinearLayout.LayoutParams(Cards.dp(this, cardWidth), -2));
    }

    private boolean reduceMotion() {
        return Settings.Global.getFloat(getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    private void flipCard() {
        if (busy) return;
        if (reduceMotion()) { swap(); return; }
        busy = true;
        ObjectAnimator out = ObjectAnimator.ofFloat(flip, "rotationY", 0f, 90f).setDuration(220);
        ObjectAnimator in = ObjectAnimator.ofFloat(flip, "rotationY", -90f, 0f).setDuration(260);
        out.setInterpolator(new android.view.animation.AccelerateInterpolator());
        in.setInterpolator(new android.view.animation.DecelerateInterpolator());
        out.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { swap(); in.start(); }
        });
        in.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { busy = false; }
        });
        out.start();
    }

    private void swap() {
        showingFront = !showingFront;
        front.setVisibility(showingFront ? View.VISIBLE : View.GONE);
        back.setVisibility(showingFront ? View.GONE : View.VISIBLE);
    }
}
