package hk.hkucs.dontwannaforget;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** 原图查看:完整显示,可缩放。接收 extras: path(相对 filesDir)、id(Media id)。 */
public class ImageViewerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ZoomImageView iv = new ZoomImageView(this);
        root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        TextView close = Cards.text(this, L10n.s(R.string.ui_060), 14, Color.WHITE, AppFonts.regular());
        close.setPadding(Cards.dp(this, 20), Cards.dp(this, 40), Cards.dp(this, 20), Cards.dp(this, 20));
        close.setOnClickListener(v -> finish());
        HomeNavigation.bind(close);
        root.addView(close, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        FloatingHeader.style(close);
        setContentView(root);

        Media m = new Media();
        m.id = getIntent().getStringExtra("id");
        m.path = getIntent().getStringExtra("path");
        ImageLoader.load(this, m, 2048, bmp -> {
            if (bmp == null) {
                Toast.makeText(this, L10n.s(R.string.ui_459), Toast.LENGTH_SHORT).show();
                return;
            }
            iv.setImageBitmap(bmp);
        });
    }
}
