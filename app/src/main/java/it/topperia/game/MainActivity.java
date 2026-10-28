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
        getWindow().setStatusBarColor(0xfff8f7f0);
        getWindow().setNavigationBarColor(0xfff8f7f0);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        game = new TopperiaView(this);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xfff8f7f0);
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

    @Override protected void onPause() { super.onPause(); game.pause(); game.save(); }
    @Override protected void onResume() { super.onResume(); if (game != null) game.resume(); }
    @Override protected void onDestroy() { game.dispose(); super.onDestroy(); }
    // API 33+ uses the native OnBackInvokedDispatcher registered above.
    // Keep this override for Android 8-12, where that dispatcher does not exist.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { game.goBack(); }
}
