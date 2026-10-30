package br.com.lcmtech.contentguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;

/** Capa da abertura. Não coleta dados nem acessa a rede. */
public final class SplashActivity extends Activity {
    private static final long DISPLAY_MILLIS = 1200L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable enter = () -> {
        if (!isFinishing() && !isDestroyed()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(4, 23, 68));
        ImageView cover = new ImageView(this);
        cover.setImageResource(R.drawable.contentguard_opening);
        cover.setContentDescription("ContentGuard Child: proteção inteligente de conteúdo");
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(enter);
        handler.postDelayed(enter, DISPLAY_MILLIS);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(enter);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(enter);
        super.onDestroy();
    }
}
