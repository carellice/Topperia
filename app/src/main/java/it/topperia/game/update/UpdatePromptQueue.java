package it.topperia.game.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;

/** Retains the next updater prompt across dialog dismissal and Android settings. */
public final class UpdatePromptQueue {
    private final Activity activity;
    private final View host;
    private final Runnable drain = this::drain;
    private Runnable pending;
    private AlertDialog dialog;
    private boolean resumed, posted, closed;

    public UpdatePromptQueue(Activity activity, View host) {
        this.activity = activity;
        this.host = host;
    }

    public void post(Runnable prompt) {
        if (closed) return;
        pending = prompt;
        recheck();
    }

    public void onResume() { resumed = true; recheck(); }
    public void onPause() { resumed = false; }
    public void onWindowFocusChanged(boolean focused) { if (focused) recheck(); }

    private void recheck() {
        if (closed || posted || pending == null) return;
        posted = true;
        host.post(drain);
    }

    private void drain() {
        posted = false;
        if (closed || !resumed || pending == null || !host.isAttachedToWindow()
                || !activity.hasWindowFocus() || activity.isFinishing() || activity.isDestroyed()
                || (dialog != null && dialog.isShowing())) return;
        Runnable prompt = pending;
        pending = null;
        prompt.run();
    }

    /** Called by a queued prompt; dismissal schedules the next prompt after focus returns. */
    public void show(AlertDialog.Builder builder) {
        if (closed) return;
        AlertDialog next = builder.create();
        dialog = next;
        next.setOnDismissListener(ignored -> {
            if (dialog == next) dialog = null;
            recheck();
        });
        next.show();
    }

    public void close() {
        closed = true;
        pending = null;
        host.removeCallbacks(drain);
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }
}
