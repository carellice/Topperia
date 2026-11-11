package it.topperia.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.os.Build;
import android.view.WindowInsets;

public final class MainActivity extends Activity {
    private TopperiaView game;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xff2b1463);
        getWindow().setNavigationBarColor(0xff160b33);
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        game = new TopperiaView(this);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xff2b1463, 0xff160b33}));
        frame.addView(game, new FrameLayout.LayoutParams(-1,-1));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            frame.setOnApplyWindowInsetsListener((view,insets) -> {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(frame);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> game.goBack());
        }
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != TopperiaView.SPEECH_REQUEST || result != RESULT_OK || data == null || game == null) return;
        java.util.ArrayList<String> heard = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        if (heard != null && !heard.isEmpty()) game.heard(heard.get(0));
    }

    @Override protected void onPause() { super.onPause(); game.pause(); game.save(); }
    @Override protected void onResume() { super.onResume(); if (game != null) game.resume(); }
    @Override protected void onDestroy() { game.dispose(); super.onDestroy(); }
    // API 33+ uses the native OnBackInvokedDispatcher registered above.
    // Keep this override for Android 8-12, where that dispatcher does not exist.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { game.goBack(); }
}
