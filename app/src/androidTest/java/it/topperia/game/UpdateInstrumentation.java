package it.topperia.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import it.topperia.game.update.GitHubUpdater;
import it.topperia.game.update.UpdateInfo;
import it.topperia.game.update.UpdatePolicy;
import it.topperia.game.update.UpdatePromptQueue;
import org.json.JSONObject;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Updater dialogs must survive the focus handoff from another dialog or Android settings. */
public final class UpdateInstrumentation extends Instrumentation {
    private Activity activity;
    private TopperiaView view;
    private UpdatePromptQueue queue;
    private GitHubUpdater.Listener listener;
    private int checks;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            // Only run on a disposable emulator: preference changes keep this test offline.
            getTargetContext().getSharedPreferences("topperia", 0).edit()
                .putBoolean("onboarding_complete", true).putBoolean("reduced", true).commit();
            getTargetContext().getSharedPreferences("topperia_updates", 0).edit()
                .putLong("last_check", System.currentTimeMillis()).remove("pending_install").commit();
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            view = main(() -> findView(activity.getWindow().getDecorView()));
            check(view != null, "native game view exists");
            queue = main(() -> (UpdatePromptQueue) field(view, "updatePrompts"));
            GitHubUpdater updater = main(() -> (GitHubUpdater) field(view, "updater"));
            listener = main(() -> (GitHubUpdater.Listener) field(updater, "listener"));
            await(() -> activity.hasWindowFocus(), "activity gains focus");

            // Reproduce the original failure: an updater callback arrives while
            // an unrelated dialog is still dismissing and owns the window focus.
            AlertDialog blocker = blockingDialog(null);
            main(() -> { listener.onInstallPermissionRequired(); return null; });
            SystemClock.sleep(120);
            check(main(() -> activeDialog() == null), "permission prompt waits behind the blocking dialog");
            AlertDialog permissionBlocker = blocker;
            main(() -> { permissionBlocker.dismiss(); return null; });
            expectPrompt("Consenti gli aggiornamenti");
            dismissPrompt();

            // A callback fired by the positive button runs before Android
            // automatically dismisses that button's original dialog.
            blocker = blockingDialog(() -> listener.onInstallPermissionRequired());
            AlertDialog buttonDialog = blocker;
            main(() -> { buttonDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); return null; });
            expectPrompt("Consenti gli aggiornamenti");
            check(main(() -> !buttonDialog.isShowing()), "source button dialog has dismissed before the next prompt");
            dismissPrompt();

            UpdateInfo update = updateFixture();
            blocker = blockingDialog(null);
            main(() -> { listener.onReadyToInstall(update); return null; });
            SystemClock.sleep(120);
            check(main(() -> activeDialog() == null), "download-ready prompt waits for focus");
            AlertDialog readyBlocker = blocker;
            main(() -> { readyBlocker.dismiss(); return null; });
            expectPrompt("Tutto pronto per aggiornare");
            check(main(() -> (Boolean) field(view, "updateReady")), "download completion updates the ready state");
            dismissPrompt();

            blocker = blockingDialog(null);
            main(() -> { listener.onUpdateAvailable(update); return null; });
            AlertDialog availableBlocker = blocker;
            main(() -> { availableBlocker.dismiss(); return null; });
            expectPrompt("Topperia 9.9.9 è pronta");
            dismissPrompt();

            blocker = blockingDialog(null);
            main(() -> { listener.onError("Errore di aggiornamento di prova"); return null; });
            SystemClock.sleep(120);
            check(main(() -> activeDialog() == null), "error prompt waits for focus");
            AlertDialog errorBlocker = blocker;
            main(() -> { errorBlocker.dismiss(); return null; });
            expectPrompt("Aggiornamento non completato");
            check(main(() -> containsText(activeDialog().getWindow().getDecorView(), "Errore di aggiornamento di prova")), "deferred error preserves its message");
            check(main(() -> !(Boolean) field(view, "updateReady")), "an unavailable cached APK is not left installable");
            dismissPrompt();

            // Match the view lifecycle hooks called when Android's permission
            // screen covers the activity; no prompt may be lost while paused.
            main(() -> { view.pause(); listener.onInstallPermissionRequired(); return null; });
            SystemClock.sleep(120);
            check(main(() -> activeDialog() == null), "paused activity does not open updater prompts");
            main(() -> { view.resume(); return null; });
            expectPrompt("Consenti gli aggiornamenti");
            dismissPrompt();

            // The next callback must also survive dismissal of a dialog owned
            // by the updater itself, without stacking two windows.
            main(() -> { listener.onError("Primo messaggio"); return null; });
            expectPrompt("Aggiornamento non completato");
            AlertDialog first = main(this::activeDialog);
            main(() -> { listener.onInstallPermissionRequired(); return null; });
            SystemClock.sleep(120);
            check(main(() -> activeDialog() == first && first.isShowing()), "an existing updater dialog remains the only visible prompt");
            main(() -> { first.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); return null; });
            expectPrompt("Consenti gli aggiornamenti");
            check(main(() -> activeDialog() != first && !first.isShowing()), "the queued prompt replaces the dismissed updater dialog");
            dismissPrompt();

            // A formerly verified download may become unreadable or corrupt.
            // Installing it must restore a downloadable state instead of
            // trapping the user in an endless Installa/error cycle.
            File directory = new File(getTargetContext().getFilesDir(), "updates");
            check(directory.isDirectory() || directory.mkdirs(), "prepares isolated updater cache fixture");
            File apk = new File(directory, "topperia-update.apk");
            File marker = new File(directory, "verified-update.json");
            Files.write(apk.toPath(), new byte[]{1});
            JSONObject metadata = new JSONObject().put("schemaVersion", 1).put("versionCode", update.versionCode)
                .put("versionName", update.versionName).put("packageName", getTargetContext().getPackageName())
                .put("apkAssetName", update.apkAssetName).put("sha256", update.sha256)
                .put("sizeBytes", update.sizeBytes).put("tag", update.tag).put("apkUrl", update.apkUrl);
            Files.write(marker.toPath(), metadata.toString().getBytes(StandardCharsets.UTF_8));
            main(() -> { setField(updater, "downloaded", update); listener.onReadyToInstall(update); return null; });
            expectPrompt("Tutto pronto per aggiornare");
            check(main(() -> (Boolean) field(view, "updateReady")), "cached update begins in the ready state");
            main(() -> { activeDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick(); return null; });
            expectPrompt("Aggiornamento non completato");
            check(updater.downloadedUpdate() == null, "failed integrity check clears the cached update identity");
            check(!apk.exists() && !marker.exists(), "failed integrity check removes both corrupt cache artifacts");
            check(main(() -> !(Boolean) field(view, "updateReady")), "failed installation returns the UI to a downloadable state");
            dismissPrompt();

            result.putString("stream", "PASS: " + checks + " Android updater dialog assertions\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            StringWriter stack = new StringWriter(); error.printStackTrace(new PrintWriter(stack));
            result.putString("stream", "FAIL after " + checks + " checks\n" + stack);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private AlertDialog blockingDialog(Runnable positive) throws Exception {
        AlertDialog blocker = main(() -> new AlertDialog.Builder(activity).setTitle("Dialogo di prova")
            .setPositiveButton("Continua", (dialog, which) -> { if (positive != null) positive.run(); }).show());
        await(() -> blocker.isShowing() && !activity.hasWindowFocus(), "blocking dialog owns window focus");
        return blocker;
    }

    private void expectPrompt(String text) throws Exception {
        await(() -> activeDialog() != null && activeDialog().isShowing()
            && containsText(activeDialog().getWindow().getDecorView(), text), "shows deferred prompt: " + text);
    }

    private void dismissPrompt() throws Exception {
        main(() -> { activeDialog().dismiss(); return null; });
        await(() -> activeDialog() == null && activity.hasWindowFocus(), "dismissed prompt returns focus to the game");
    }

    private AlertDialog activeDialog() throws Exception { return (AlertDialog) field(queue, "dialog"); }

    private static UpdateInfo updateFixture() throws Exception {
        UpdatePolicy.Metadata metadata = new UpdatePolicy.Metadata(1, 99999, "9.9.9", "it.topperia.game",
            "Topperia-9.9.9.apk", new String(new char[64]).replace('\0', 'a'), 1, "v9.9.9");
        Constructor<UpdateInfo> constructor = UpdateInfo.class.getDeclaredConstructor(UpdatePolicy.Metadata.class,
            String.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(metadata,
            "https://github.com/carellice/Topperia/releases/download/v9.9.9/Topperia-9.9.9.apk",
            "https://github.com/carellice/Topperia/releases/tag/v9.9.9", "Note di prova");
    }

    private static TopperiaView findView(View root) {
        if (root instanceof TopperiaView) return (TopperiaView) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            TopperiaView found = findView(((ViewGroup) root).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }

    private static boolean containsText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return true;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++)
            if (containsText(((ViewGroup) root).getChildAt(i), text)) return true;
        return false;
    }

    private void await(Callable<Boolean> condition, String description) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (main(condition)) { check(true, description); return; }
            SystemClock.sleep(40);
        }
        check(false, description);
    }

    private void check(boolean condition, String description) { checks++; if (!condition) throw new AssertionError(description); }
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private <T> T main(Callable<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> { try { value.set(action.call()); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new RuntimeException(failure.get());
        return value.get();
    }
}
