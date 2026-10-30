package hk.hkucs.dontwannaforget;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 声音记忆编辑器(“生活中总有些声音是你不想忘记的”):
 * - 支持【🎙 现场录音】(麦克风实时录制 + 声波跳动 + 磁带转轮旋转)
 * - 支持【📁 上传本地录音】(从手机文件管理/语音备忘录选择 .m4a/.mp3/.wav/.aac/.ogg 等音频文件)
 * - 支持录音/上传后现场【▶ 试听声音】与可选配图。
 */
public class VoiceEditActivity extends AppCompatActivity {
    private static final String[][] PALETTES = {
            {"#4A6670", "#D9A464"},
            {"#8E4F4B", "#E5C185"},
            {"#3E4A45", "#C9C2AE"},
            {"#5A7D8C", "#E7A35A"}
    };

    private String dateStr;
    private boolean saving = false;

    // 音频状态
    private File recordedFile;
    private Uri uploadedAudioUri;
    private int durationSec = 0;
    private String sourceLabel = L10n.s(R.string.ui_173);

    // 录音与播放
    private MediaRecorder recorder;
    private boolean isRecording = false;
    private long recordStartMs = 0L;
    private MediaPlayer previewPlayer;
    private boolean isPreviewPlaying = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 可选场景照片
    private Uri coverUri;
    private ImageView ivCoverPreview;
    private TextView tvCoverHint;

    // UI 控件
    private VoiceTapeView tapeView;
    private TextView tvDate, tvStatus, btnRecord, btnUpload, btnPreview, btnSave;
    private EditText etTitle, etSub, etNote;

    private ActivityResultLauncher<String> requestMicPerm;
    private ActivityResultLauncher<String[]> pickAudioDoc;
    private ActivityResultLauncher<PickVisualMediaRequest> pickCoverImage;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (isRecording && recorder != null) {
                int elapsed = Math.max(1, (int) ((System.currentTimeMillis() - recordStartMs) / 1000L));
                durationSec = elapsed;
                String dur = formatDuration(elapsed);
                float amp = 0.2f;
                try {
                    amp = Math.min(1f, recorder.getMaxAmplitude() / 22000f);
                } catch (Exception ignored) {}
                tapeView.pushLiveAmplitude(amp);
                tapeView.setMeta(currentTitle(), textOf(etSub), dur, L10n.s(R.string.ui_174));
                tvStatus.setText(L10n.s(R.string.ui_175) + dur + L10n.s(R.string.ui_176));
                handler.postDelayed(this, 100);
            } else if (isPreviewPlaying && previewPlayer != null) {
                try {
                    int pos = previewPlayer.getCurrentPosition();
                    int total = Math.max(1, previewPlayer.getDuration());
                    tapeView.setProgress(pos / (float) total);
                    tvStatus.setText(L10n.s(R.string.ui_177) + formatDuration(pos / 1000) + " / " + formatDuration(total / 1000));
                    handler.postDelayed(this, 100);
                } catch (Exception ignored) {}
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        dateStr = new SimpleDateFormat("yyyy.MM.dd", Locale.US).format(new Date());

        registerLaunchers();
        buildUi();
    }

    private void registerLaunchers() {
        requestMicPerm = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (Boolean.TRUE.equals(granted)) {
                startRecording();
            } else {
                Toast.makeText(this, L10n.s(R.string.ui_178), Toast.LENGTH_LONG).show();
            }
        });

        pickAudioDoc = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) onAudioUriPicked(uri);
        });

        pickCoverImage = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                coverUri = uri;
                if (ivCoverPreview != null) {
                    ivCoverPreview.setImageURI(uri);
                    ivCoverPreview.setAlpha(1f);
                    ivCoverPreview.setVisibility(View.VISIBLE);
                }
                if (tvCoverHint != null) tvCoverHint.setText(L10n.s(R.string.ui_179));
            }
        });
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF121312);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(true);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Cards.dp(this, 22);
        col.setPadding(pad, Cards.dp(this, 16), pad, Cards.dp(this, 160));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        // 1. 顶部导航栏
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, 0, Cards.dp(this, 14));
        ImageView cancel=new ImageView(this);
        cancel.setImageDrawable(new AgentLineIcon("close",0xFFE8E4DC));
        cancel.setPadding(Cards.dp(this,12),Cards.dp(this,12),Cards.dp(this,12),Cards.dp(this,12));
        cancel.setContentDescription(L10n.s(R.string.ui_060));
        cancel.setOnClickListener(v -> finish());
        HomeNavigation.bind(cancel);
        top.addView(cancel,new LinearLayout.LayoutParams(Cards.dp(this,44),Cards.dp(this,44)));

        TextView head = Cards.text(this, L10n.s(R.string.ui_180), 15, 0xFFF4F2EE,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        head.setGravity(Gravity.CENTER);
        top.addView(head, new LinearLayout.LayoutParams(0, -2, 1f));

        tvDate = Cards.text(this, dateStr.replace('.', '-'), 12.5f, 0xFFA5A19A, AppFonts.regular());
        tvDate.setOnClickListener(v -> pickDate());
        top.addView(tvDate);
        col.addView(top);
        FloatingHeader.pin(top);

        // 2. 场景照片预览框 (仿 voiceexp1.jpg / voiceexp2.jpg 顶部大图)
        buildCoverBox(col);

        // 3. 胶囊形音频播放/声波状态条 (移除旧收音机/磁带造型，改为深色圆角胶囊条)
        tapeView = new VoiceTapeView(this);
        tapeView.setEditorBarMode(true);
        tapeView.setMeta(L10n.s(R.string.ui_000), dateStr, "00:00", sourceLabel);
        tapeView.setOnClickListener(v -> {
            if (recordedFile != null || uploadedAudioUri != null) {
                togglePreview();
            } else {
                toggleRecording();
            }
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, Cards.dp(this, 56));
        tlp.bottomMargin = Cards.dp(this, 12);
        col.addView(tapeView, tlp);

        // 4. 录音状态提示条
        tvStatus = Cards.text(this, L10n.s(R.string.ui_181), 11.5f,
                0xFF8E8A82, AppFonts.regular());
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, Cards.dp(this, 14));
        col.addView(tvStatus);

        // 5. 录音 / 上传 / 试听 按钮组
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);

        btnRecord = actionButton(L10n.s(R.string.ui_182), getColor(R.color.wine), Color.WHITE);
        btnRecord.setOnClickListener(v -> toggleRecording());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -2, 1f);
        rlp.rightMargin = Cards.dp(this, 10);
        actionRow.addView(btnRecord, rlp);

        btnUpload = actionButton(L10n.s(R.string.ui_183), 0xFF222524, 0xFFF4F2EE);
        btnUpload.setOnClickListener(v -> launchAudioPicker());
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, -2, 1f);
        actionRow.addView(btnUpload, ulp);

        LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(-1, -2);
        arp.bottomMargin = Cards.dp(this, 10);
        col.addView(actionRow, arp);

        // 试听按钮(选择或录制音频后显示)
        btnPreview = actionButton(L10n.s(R.string.ui_184), 0xFF2E3834, 0xFFF4F2EE);
        btnPreview.setVisibility(View.GONE);
        btnPreview.setOnClickListener(v -> togglePreview());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.bottomMargin = Cards.dp(this, 16);
        col.addView(btnPreview, plp);

        // 6. 声音标题 / 地点 / 故事输入
        etTitle = addInput(col, L10n.s(R.string.ui_185), 80, true);
        etSub = addInput(col, L10n.s(R.string.ui_186), 100, true);
        etNote = addInput(col, L10n.s(R.string.ui_187), 5000, false);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String sub = textOf(etSub);
                tapeView.setMeta(currentTitle(), sub.isEmpty() ? dateStr : sub,
                        formatDuration(durationSec), sourceLabel);
            }
        };
        etTitle.addTextChangedListener(watcher);
        etSub.addTextChangedListener(watcher);

        // 7. 底部固定保存栏
        FrameLayout bottom = new FrameLayout(this);
        bottom.setPadding(pad, Cards.dp(this, 12), pad, Cards.dp(this, 16));
        bottom.setBackgroundColor(0xFF121312);
        btnSave = Cards.text(this, L10n.s(R.string.ui_188), 15.5f, 0xFF121312,
                Typeface.create(AppFonts.regular(), Typeface.BOLD));
        btnSave.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF4F2EE);
        bg.setCornerRadius(Cards.dp(this, 24));
        btnSave.setBackground(bg);
        btnSave.setPadding(0, Cards.dp(this, 14), 0, Cards.dp(this, 14));
        EditorUi.action(btnSave);
        btnSave.setOnClickListener(v -> onSave());
        bottom.addView(btnSave, new FrameLayout.LayoutParams(-1, -2));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });
    }

    private TextView actionButton(String text, int bgColor, int textColor) {
        TextView b = Cards.text(this, text, 13.5f, textColor, Typeface.create(AppFonts.regular(), Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bgColor);
        g.setCornerRadius(Cards.dp(this, 22));
        g.setStroke(Cards.dp(this, 1), 0xFF333635);
        b.setBackground(g);
        b.setPadding(Cards.dp(this, 12), Cards.dp(this, 12), Cards.dp(this, 12), Cards.dp(this, 12));
        EditorUi.action(b);
        return b;
    }

    private void buildCoverBox(LinearLayout col) {
        FrameLayout box = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1B1D1C);
        g.setCornerRadius(Cards.dp(this, 12));
        g.setStroke(Cards.dp(this, 1), 0xFF2E3230);
        box.setBackground(g);
        box.setClipToOutline(true);

        ivCoverPreview = new ImageView(this);
        ivCoverPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivCoverPreview.setImageResource(R.drawable.voice_scene_2);
        ivCoverPreview.setAlpha(0.55f);
        box.addView(ivCoverPreview, new FrameLayout.LayoutParams(-1, -1));

        tvCoverHint = Cards.text(this, L10n.s(R.string.ui_189), 13.5f, 0xFFF4F2EE, AppFonts.regular());
        tvCoverHint.setGravity(Gravity.CENTER);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xAA121312);
        pill.setCornerRadius(Cards.dp(this, 18));
        tvCoverHint.setBackground(pill);
        tvCoverHint.setPadding(Cards.dp(this, 16), Cards.dp(this, 8), Cards.dp(this, 16), Cards.dp(this, 8));
        box.addView(tvCoverHint, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        box.setOnClickListener(v -> pickCoverImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Cards.dp(this, 170));
        lp.bottomMargin = Cards.dp(this, 14);
        col.addView(box, lp);
    }

    private EditText addInput(LinearLayout col, String hint, int maxLen, boolean singleLine) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(0xFF72706A);
        et.setTextColor(0xFFF4F2EE);
        et.setTextSize(14.5f);
        et.setTypeface(AppFonts.regular());
        et.setSingleLine(singleLine);
        if (!singleLine) {
            et.setMinLines(4);
            et.setGravity(Gravity.TOP);
            et.setLineSpacing(0, 1.45f);
        }
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1B1D1C);
        g.setCornerRadius(Cards.dp(this, 10));
        g.setStroke(Cards.dp(this, 1), 0xFF2E3230);
        et.setBackground(g);
        int p = Cards.dp(this, 14);
        et.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Cards.dp(this, 12);
        col.addView(et, lp);
        EditorUi.input(et,singleLine);
        return et;
    }

    // ---- 现场录音逻辑 ----
    private void toggleRecording() {
        stopPreview();
        if (isRecording) {
            stopRecording();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                requestMicPerm.launch(Manifest.permission.RECORD_AUDIO);
            }
        }
    }

    private void startRecording() {
        try {
            File dir = new File(getCacheDir(), "voice_rec");
            if (!dir.exists()) dir.mkdirs();
            recordedFile = new File(dir, "rec_" + System.currentTimeMillis() + ".m4a");
            uploadedAudioUri = null;

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(recordedFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            isRecording = true;
            recordStartMs = System.currentTimeMillis();
            durationSec = 0;
            sourceLabel = L10n.s(R.string.ui_190);
            btnRecord.setText(L10n.s(R.string.ui_191));
            btnPreview.setVisibility(View.GONE);
            tapeView.setRecording(true);
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } catch (Exception e) {
            isRecording = false;
            releaseRecorder();
            Toast.makeText(this, L10n.s(R.string.ui_192), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        isRecording = false;
        handler.removeCallbacks(ticker);
        try {
            if (recorder != null) recorder.stop();
        } catch (Exception ignored) {}
        releaseRecorder();

        durationSec = Math.max(1, (int) ((System.currentTimeMillis() - recordStartMs) / 1000L));
        String dur = formatDuration(durationSec);
        sourceLabel = L10n.s(R.string.ui_190);
        tapeView.setRecording(false);
        tapeView.setProgress(0f);
        tapeView.setMeta(currentTitle(), textOf(etSub).isEmpty() ? dateStr : textOf(etSub), dur, sourceLabel);
        btnRecord.setText(L10n.s(R.string.ui_193));
        tvStatus.setText(L10n.s(R.string.ui_194) + dur + ")");
        btnPreview.setVisibility(View.VISIBLE);
        btnPreview.setText(L10n.s(R.string.ui_195) + dur + ")");
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    // ---- 本地上传录音文件逻辑 ----
    private void launchAudioPicker() {
        if (isRecording) stopRecording();
        stopPreview();
        pickAudioDoc.launch(new String[]{"audio/*", "application/ogg", "*/*"});
    }

    private void onAudioUriPicked(Uri uri) {
        recordedFile = null;
        uploadedAudioUri = uri;

        String displayName = queryDisplayName(uri);
        int sec = queryAudioDurationSec(uri);
        durationSec = Math.max(1, sec);
        String dur = formatDuration(durationSec);
        sourceLabel = L10n.s(R.string.ui_196) + (displayName != null ? displayName : L10n.s(R.string.ui_197));

        // 如果标题为空，自动用去掉后缀的录音文件名填充
        if (textOf(etTitle).isEmpty() && displayName != null) {
            int dot = displayName.lastIndexOf('.');
            String clean = dot > 0 ? displayName.substring(0, dot) : displayName;
            etTitle.setText(clean);
        }

        tapeView.setRecording(false);
        tapeView.setProgress(0f);
        tapeView.setMeta(currentTitle(), textOf(etSub).isEmpty() ? dateStr : textOf(etSub), dur, L10n.s(R.string.ui_058));
        tvStatus.setText(L10n.s(R.string.ui_198) + (displayName != null ? displayName : L10n.s(R.string.ui_199)) + " (" + dur + ")");
        btnPreview.setVisibility(View.VISIBLE);
        btnPreview.setText(L10n.s(R.string.ui_200) + dur + ")");
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int queryAudioDurationSec(Uri uri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this, uri);
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durStr != null) {
                long ms = Long.parseLong(durStr);
                return Math.max(1, Math.round(ms / 1000f));
            }
        } catch (Exception ignored) {
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        return 15;
    }

    // ---- 试听播放逻辑 ----
    private void togglePreview() {
        if (isPreviewPlaying) {
            stopPreview();
            return;
        }
        try {
            previewPlayer = new MediaPlayer();
            if (recordedFile != null && recordedFile.exists()) {
                previewPlayer.setDataSource(recordedFile.getAbsolutePath());
            } else if (uploadedAudioUri != null) {
                previewPlayer.setDataSource(this, uploadedAudioUri);
            } else {
                return;
            }
            previewPlayer.prepare();
            previewPlayer.setOnCompletionListener(mp -> stopPreview());
            previewPlayer.start();
            isPreviewPlaying = true;
            tapeView.setPlaying(true);
            btnPreview.setText(L10n.s(R.string.ui_201));
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } catch (Exception e) {
            stopPreview();
            Toast.makeText(this, L10n.s(R.string.ui_202), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        isPreviewPlaying = false;
        handler.removeCallbacks(ticker);
        if (previewPlayer != null) {
            try { previewPlayer.stop(); } catch (Exception ignored) {}
            try { previewPlayer.release(); } catch (Exception ignored) {}
            previewPlayer = null;
        }
        if (tapeView != null) {
            tapeView.setPlaying(false);
            tapeView.setProgress(0f);
        }
        if (btnPreview != null && btnPreview.getVisibility() == View.VISIBLE) {
            btnPreview.setText(L10n.s(R.string.ui_195) + formatDuration(durationSec) + ")");
        }
    }

    // ---- 保存逻辑 ----
    private void onSave() {
        if (saving) return;
        if (isRecording) stopRecording();
        stopPreview();

        boolean hasAudio = (recordedFile != null && recordedFile.exists()) || uploadedAudioUri != null;
        String title = textOf(etTitle);
        String sub = textOf(etSub);
        String note = textOf(etNote);

        if (!hasAudio && title.isEmpty() && note.isEmpty()) {
            Toast.makeText(this, L10n.s(R.string.ui_203), Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.isEmpty()) {
            title = L10n.s(R.string.ui_000);
        }
        if (note.isEmpty()) {
            note = L10n.s(R.string.ui_204);
        }

        String durStr = formatDuration(durationSec > 0 ? durationSec : 30);
        String srcTag = hasAudio ? sourceLabel : L10n.s(R.string.ui_205);

        saving = true;
        btnSave.setText(L10n.s(R.string.ui_206));
        btnSave.setAlpha(0.6f);

        String[] pal = PALETTES[Math.abs(title.hashCode()) % PALETTES.length];
        Record r = new Record(Record.VOICE, title, sub, dateStr, null, note,
                pal[0], pal[1], durStr, srcTag);

        new Repo(this).saveVoiceRecord(r, recordedFile, uploadedAudioUri, coverUri, durationSec,
                ok -> runOnUiThread(() -> {
                    if (!ok) {
                        saving = false;
                        btnSave.setText(L10n.s(R.string.ui_188));
                        btnSave.setAlpha(1f);
                        Toast.makeText(this, L10n.s(R.string.ui_207), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, L10n.s(R.string.ui_208), Toast.LENGTH_SHORT).show();
                    finish();
                }));
    }

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            dateStr = String.format(Locale.US, "%04d.%02d.%02d", y, m + 1, d);
            tvDate.setText(dateStr);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String currentTitle() {
        String t = textOf(etTitle);
        return t.isEmpty() ? L10n.s(R.string.ui_000) : t;
    }

    public static String formatDuration(int totalSec) {
        int s = Math.max(0, totalSec);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private static String textOf(EditText et) {
        return et == null ? "" : et.getText().toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRecording) releaseRecorder();
        stopPreview();
    }
}
