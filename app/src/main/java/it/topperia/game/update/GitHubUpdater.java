package it.topperia.game.update;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import it.topperia.game.BuildConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Public GitHub releases only; no account tokens, background service, or silent installation. */
public final class GitHubUpdater {
    public interface Listener {
        default void onChecking() { }
        default void onUpToDate(String versionName) { }
        default void onUpdateAvailable(UpdateInfo info) { }
        default void onDownloadProgress(int percent) { }
        default void onReadyToInstall(UpdateInfo info) { }
        default void onInstallPermissionRequired() { }
        default void onInstallerOpened() { }
        default void onNoPublishedRelease() { onError("Non ci sono ancora release pubblicate."); }
        default void onError(String message) { }
    }
    private static final long CHECK_INTERVAL = 24L * 60L * 60L * 1000L;
    private static final Object ARTIFACT_LOCK = new Object();
    private final Activity activity;
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SharedPreferences preferences;
    private final PackageManager packages;
    private final String owner = BuildConfig.GITHUB_OWNER, repository = BuildConfig.GITHUB_REPO;
    private volatile boolean busy, closed;
    private volatile HttpsURLConnection activeConnection;
    private volatile UpdateInfo downloaded;

    public GitHubUpdater(Activity activity, Listener listener) {
        this.activity = activity; this.context = activity.getApplicationContext(); this.listener = listener;
        this.packages = context.getPackageManager();
        this.preferences = context.getSharedPreferences("topperia_updates", Context.MODE_PRIVATE);
        try {
            UpdatePolicy.validateRepository(owner, repository);
            downloaded = loadDownloaded();
            if (downloaded != null && !UpdatePolicy.isNewer(downloaded.versionCode, installedCode())) {
                discardDownloaded(); downloaded = null;
            }
        } catch (Exception ignored) { downloaded = null; }
    }

    public boolean isBusy() { return busy; }
    public UpdateInfo downloadedUpdate() { return downloaded; }
    public String currentVersionName() {
        try { return installedInfo().versionName; } catch (PackageManager.NameNotFoundException impossible) { return "—"; }
    }
    public String releasesUrl() { return "https://github.com/" + owner + "/" + repository + "/releases"; }

    /** Automatic checks are throttled and quiet unless a newer stable release exists. */
    public void check(boolean manual) {
        if (closed) return;
        if (busy) { if (manual) notifyUi(() -> listener.onError("Attendi che termini l'operazione in corso.")); return; }
        long now = System.currentTimeMillis(), previous = preferences.getLong("last_check", 0);
        if (!manual && previous > 0 && now >= previous && now - previous < CHECK_INTERVAL) return;
        preferences.edit().putLong("last_check", now).apply(); busy = true;
        if (manual) notifyUi(listener::onChecking);
        executor.execute(() -> {
            try {
                UpdateInfo update = fetchLatest();
                boolean newer = update != null && UpdatePolicy.isNewer(update.versionCode, installedCode());
                busy = false;
                if (update == null) { if (manual) notifyUi(listener::onNoPublishedRelease); }
                else if (newer) notifyUi(() -> listener.onUpdateAvailable(update));
                else if (manual) notifyUi(() -> listener.onUpToDate(currentVersionName()));
            } catch (Exception error) { busy = false; if (manual) report(error, false); }
        });
    }

    /** Call only after the player chooses to download the offered version. */
    public void beginDownload(UpdateInfo update) {
        if (closed) return;
        if (busy) { notifyUi(() -> listener.onError("Un'operazione è già in corso.")); return; }
        busy = true; notifyUi(() -> listener.onDownloadProgress(0));
        executor.execute(() -> {
            File part = null;
            try {
                requireCurrentUpdate(update);
                File directory = UpdateApkProvider.directory(context);
                if (!directory.isDirectory() && !directory.mkdirs()) throw new UserFailure("Non riesco a preparare il download.");
                if (downloaded != null && downloaded.sha256.equals(update.sha256) && UpdateApkProvider.apk(context).isFile()) {
                    validateApk(UpdateApkProvider.apk(context), update);
                } else {
                    part = new File(directory, "download-" + UUID.randomUUID() + ".part");
                    download(update, part);
                    validateApk(part, update);
                    synchronized (ARTIFACT_LOCK) {
                        requireOpen(); move(part, UpdateApkProvider.apk(context)); part = null;
                        writeMarker(update); downloaded = update;
                    }
                }
                downloaded = update; busy = false;
                notifyUi(() -> listener.onDownloadProgress(100)); notifyUi(() -> listener.onReadyToInstall(update));
            } catch (Exception error) { busy = false; report(error, true); }
            finally { if (part != null && part.isFile()) part.delete(); }
        });
    }

    /** Revalidates the private APK before opening Android's installer. This always requires a user tap. */
    public void installDownloaded() {
        if (closed) return;
        if (busy) { notifyUi(() -> listener.onError("Attendi che termini il download.")); return; }
        UpdateInfo update = downloaded;
        if (update == null) { notifyUi(() -> listener.onError("Scarica prima l'aggiornamento.")); return; }
        busy = true;
        executor.execute(() -> {
            try {
                requireCurrentUpdate(update); validateApk(UpdateApkProvider.apk(context), update); busy = false;
                notifyUi(() -> {
                    if (!packages.canRequestPackageInstalls()) listener.onInstallPermissionRequired();
                    else openInstaller();
                });
            } catch (Exception error) { busy = false; report(error, true); }
        });
    }

    /** Opens only Android's permission screen; called after the player approves that step. */
    public void requestInstallPermission() {
        if (closed || downloaded == null) return;
        preferences.edit().putString("pending_install", downloaded.sha256).apply();
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName())));
        } catch (RuntimeException unavailable) {
            cancelPendingInstall(); notifyUi(() -> listener.onError("Apri le impostazioni Android e consenti a Topperia di installare aggiornamenti."));
        }
    }

    public void cancelPendingInstall() { preferences.edit().remove("pending_install").apply(); }

    /** Resumes only an installation explicitly approved before visiting Android settings. */
    public void onResume() {
        String pending = preferences.getString("pending_install", "");
        if (closed || pending.isEmpty()) return;
        cancelPendingInstall();
        if (downloaded == null || !pending.equals(downloaded.sha256)) return;
        if (packages.canRequestPackageInstalls()) installDownloaded();
        else notifyUi(() -> listener.onError("Permesso non concesso. L'APK resta pronto: puoi installarlo più tardi."));
    }

    private void openInstaller() {
        cancelPendingInstall();
        try {
            Uri uri = UpdateApkProvider.uri(context);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Aggiornamento Topperia", uri));
            activity.startActivity(intent); listener.onInstallerOpened();
        } catch (RuntimeException unavailable) { listener.onError("Android non riesce ad aprire l'installazione. L'APK resta pronto: riprova dalle impostazioni di Topperia."); }
    }

    private UpdateInfo fetchLatest() throws Exception {
        UpdatePolicy.validateRepository(owner, repository);
        String api = "https://api.github.com/repos/" + owner + "/" + repository + "/releases/latest";
        JSONObject release;
        try { release = new JSONObject(new String(readHttps(api, 1024 * 1024, false), StandardCharsets.UTF_8)); }
        catch (HttpFailure missing) { if (missing.code == 404) return null; throw missing; }
        if (release.getBoolean("draft") || release.getBoolean("prerelease")) throw new UserFailure("La release non è stabile.");
        String tag = release.getString("tag_name");
        JSONArray assets = release.getJSONArray("assets");
        JSONObject manifestAsset = findAsset(assets, "update.json");
        if (manifestAsset == null) throw new UserFailure("Questa release non contiene ancora i dati per l'aggiornamento in app.");
        long manifestSize = manifestAsset.getLong("size");
        if (manifestSize <= 0 || manifestSize > 32768) throw new UserFailure("Dati di aggiornamento non validi.");
        String manifestUrl = manifestAsset.getString("browser_download_url");
        UpdatePolicy.requireReleaseAssetUrl(manifestUrl, owner, repository, tag, "update.json");
        JSONObject json = new JSONObject(new String(readHttps(manifestUrl, 32768, true), StandardCharsets.UTF_8));
        UpdatePolicy.Metadata metadata = metadata(json);
        JSONObject apk = findAsset(assets, metadata.apkAssetName);
        if (apk == null) throw new UserFailure("Il file APK non è presente nella release.");
        UpdatePolicy.validateMetadata(metadata, context.getPackageName(), tag, apk.getLong("size"));
        String apkUrl = apk.getString("browser_download_url");
        UpdatePolicy.requireReleaseAssetUrl(apkUrl, owner, repository, tag, metadata.apkAssetName);
        String notes = release.optString("body", ""); if (notes.length() > 4000) notes = notes.substring(0, 4000);
        return new UpdateInfo(metadata, apkUrl, "https://github.com/" + owner + "/" + repository + "/releases/tag/" + tag, notes);
    }

    private static JSONObject findAsset(JSONArray assets, String name) throws JSONException, IOException {
        JSONObject found = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (name.equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))) {
                if (found != null) throw new UserFailure("La release contiene file ambigui."); found = asset;
            }
        }
        return found;
    }

    private static UpdatePolicy.Metadata metadata(JSONObject json) throws JSONException {
        return new UpdatePolicy.Metadata(json.getInt("schemaVersion"), json.getLong("versionCode"),
                json.getString("versionName"), json.getString("packageName"), json.getString("apkAssetName"),
                json.getString("sha256"), json.getLong("sizeBytes"), json.getString("tag"));
    }

    private void requireCurrentUpdate(UpdateInfo update) throws Exception {
        if (update == null) throw new UserFailure("Aggiornamento non disponibile.");
        UpdatePolicy.validateMetadata(update.metadata, context.getPackageName(), update.tag, update.sizeBytes);
        UpdatePolicy.requireReleaseAssetUrl(update.apkUrl, owner, repository, update.tag, update.apkAssetName);
        if (!UpdatePolicy.isNewer(update.versionCode, installedCode())) throw new UserFailure("Questa versione è già installata.");
    }

    private void download(UpdateInfo update, File target) throws Exception {
        HttpsURLConnection connection = connect(update.apkUrl, true);
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(target)) {
            long declared = connection.getContentLengthLong();
            if (declared > UpdatePolicy.MAX_APK_BYTES || (declared > 0 && declared != update.sizeBytes))
                throw new UserFailure("La dimensione del download non corrisponde alla release.");
            byte[] buffer = new byte[32768]; long total = 0; int count, previous = -1;
            while ((count = input.read(buffer)) >= 0) {
                requireOpen(); if (count == 0) continue;
                total += count;
                if (total > update.sizeBytes || total > UpdatePolicy.MAX_APK_BYTES) throw new UserFailure("File di aggiornamento troppo grande.");
                output.write(buffer, 0, count);
                int progress = (int)(total * 100L / update.sizeBytes);
                if (progress / 5 != previous / 5 || previous < 0) { previous = progress; int value = progress; notifyUi(() -> listener.onDownloadProgress(value)); }
            }
            output.getFD().sync();
            if (total != update.sizeBytes) throw new UserFailure("Download interrotto: riprova.");
        } finally { connection.disconnect(); activeConnection = null; }
    }

    private byte[] readHttps(String url, int maxBytes, boolean releaseAsset) throws Exception {
        HttpsURLConnection connection = connect(url, releaseAsset);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (connection.getContentLengthLong() > maxBytes) throw new UserFailure("Risposta di aggiornamento troppo grande.");
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) >= 0) {
                requireOpen(); if (output.size() + count > maxBytes) throw new UserFailure("Risposta di aggiornamento troppo grande.");
                if (count > 0) output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally { connection.disconnect(); activeConnection = null; }
    }

    private HttpsURLConnection connect(String initial, boolean asset) throws Exception {
        String current = initial;
        for (int redirects = 0; redirects <= 5; redirects++) {
            requireOpen();
            if (asset && !UpdatePolicy.trustedAssetRedirect(current, initial)) throw new UserFailure("Destinazione del download non autorizzata.");
            HttpsURLConnection connection = (HttpsURLConnection)new URL(current).openConnection(); activeConnection = connection;
            connection.setConnectTimeout(10000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false); connection.setRequestProperty("User-Agent", "Topperia-Android/" + currentVersionName());
            connection.setRequestProperty("Accept", asset ? "application/octet-stream" : "application/vnd.github+json");
            if (!asset) connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400 && asset) {
                String location = connection.getHeaderField("Location"); connection.disconnect(); activeConnection = null;
                if (location == null) throw new UserFailure("Reindirizzamento di download non valido.");
                current = new URL(new URL(current), location).toString(); continue;
            }
            if (status != 200) { connection.disconnect(); activeConnection = null; throw new HttpFailure(status); }
            return connection;
        }
        throw new UserFailure("Troppi reindirizzamenti per il download.");
    }

    private void validateApk(File file, UpdateInfo update) throws Exception {
        requireOpen();
        if (!file.isFile() || file.length() != update.sizeBytes || !UpdatePolicy.sha256(file).equals(update.sha256))
            throw new UserFailure("Il file scaricato non supera il controllo di integrità. Riprova il download.");
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = packages.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo installed = packages.getPackageInfo(context.getPackageName(), flags);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)
                || versionCode(candidate) != update.versionCode || !update.versionName.equals(candidate.versionName))
            throw new UserFailure("Il file scaricato non corrisponde alla versione di Topperia.");
        if (!UpdatePolicy.sameSignerSet(signers(installed), signers(candidate)))
            throw new UserFailure("La firma dell'aggiornamento non corrisponde a questa installazione di Topperia.");
        requireOpen();
    }

    private static List<String> signers(PackageInfo info) {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        else signatures = info.signatures;
        ArrayList<String> result = new ArrayList<>();
        if (signatures != null) for (Signature signature : signatures) result.add(UpdatePolicy.sha256(signature.toByteArray()));
        return result;
    }
    private PackageInfo installedInfo() throws PackageManager.NameNotFoundException { return packages.getPackageInfo(context.getPackageName(), 0); }
    private long installedCode() throws PackageManager.NameNotFoundException { return versionCode(installedInfo()); }
    private static long versionCode(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }

    private void writeMarker(UpdateInfo update) throws Exception {
        JSONObject json = new JSONObject(); UpdatePolicy.Metadata m = update.metadata;
        json.put("schemaVersion", m.schemaVersion).put("versionCode", m.versionCode).put("versionName", m.versionName)
                .put("packageName", m.packageName).put("apkAssetName", m.apkAssetName).put("sha256", m.sha256)
                .put("sizeBytes", m.sizeBytes).put("tag", m.tag).put("apkUrl", update.apkUrl).put("notes", update.notes);
        File temporary = new File(UpdateApkProvider.directory(context), "marker-" + UUID.randomUUID() + ".part");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(json.toString().getBytes(StandardCharsets.UTF_8)); output.getFD().sync(); }
            move(temporary, UpdateApkProvider.marker(context)); cancelPendingInstall();
        } finally { if (temporary.isFile()) temporary.delete(); }
    }
    private UpdateInfo loadDownloaded() throws Exception {
        File marker = UpdateApkProvider.marker(context), file = UpdateApkProvider.apk(context);
        if (!marker.isFile() || !file.isFile() || marker.length() > 32768) return null;
        JSONObject json = new JSONObject(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
        UpdatePolicy.Metadata metadata = metadata(json);
        UpdatePolicy.validateMetadata(metadata, context.getPackageName(), metadata.tag, file.length());
        String url = json.getString("apkUrl"); UpdatePolicy.requireReleaseAssetUrl(url, owner, repository, metadata.tag, metadata.apkAssetName);
        return new UpdateInfo(metadata, url, "https://github.com/" + owner + "/" + repository + "/releases/tag/" + metadata.tag, json.optString("notes", ""));
    }
    private void discardDownloaded() {
        synchronized (ARTIFACT_LOCK) { UpdateApkProvider.marker(context).delete(); UpdateApkProvider.apk(context).delete(); cancelPendingInstall(); }
    }
    private static void move(File source, File target) throws IOException {
        try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }
    private void requireOpen() throws IOException { if (closed || Thread.currentThread().isInterrupted()) throw new UserFailure("Operazione annullata."); }
    private void notifyUi(Runnable action) { main.post(() -> { if (!closed && !activity.isFinishing() && !activity.isDestroyed()) action.run(); }); }
    private void report(Exception error, boolean downloading) {
        if (closed) return;
        String message;
        if (error instanceof HttpFailure && (((HttpFailure)error).code == 403 || ((HttpFailure)error).code == 429))
            message = "GitHub ha ricevuto troppe richieste. Riprova tra qualche minuto.";
        else if (error instanceof java.net.UnknownHostException || error instanceof java.net.ConnectException || error instanceof java.net.SocketTimeoutException)
            message = "Connessione non disponibile. Puoi continuare a giocare e riprovare più tardi.";
        else if (error instanceof javax.net.ssl.SSLException) message = "Non riesco a stabilire una connessione sicura a GitHub. Riprova più tardi.";
        else if (error instanceof JSONException) message = "La release non contiene dati di aggiornamento validi.";
        else if (error instanceof IllegalArgumentException || error instanceof UserFailure || error instanceof HttpFailure)
            message = error.getMessage();
        else message = downloading ? "Non riesco a completare l'aggiornamento. Riprova più tardi." : "Non riesco a controllare gli aggiornamenti. Riprova più tardi.";
        final String humanMessage = message; notifyUi(() -> listener.onError(humanMessage));
    }
    public void close() {
        closed = true; HttpsURLConnection connection = activeConnection; if (connection != null) connection.disconnect();
        executor.shutdownNow(); main.removeCallbacksAndMessages(null);
    }
    private static final class HttpFailure extends IOException {
        final int code;
        HttpFailure(int code) { super(code == 404 ? "Release non trovata." : "GitHub non è disponibile in questo momento (" + code + ")."); this.code = code; }
    }
    private static final class UserFailure extends IOException {
        UserFailure(String message) { super(message); }
    }
}
