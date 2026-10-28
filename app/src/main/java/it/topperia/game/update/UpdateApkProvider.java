package it.topperia.game.update;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Grants the Android installer read access to one verified private APK, never to arbitrary files. */
public final class UpdateApkProvider extends ContentProvider {
    static final String APK_FILE = "topperia-update.apk", MARKER_FILE = "verified-update.json";
    static File directory(android.content.Context context) { return new File(context.getFilesDir(), "updates"); }
    static File apk(android.content.Context context) { return new File(directory(context), APK_FILE); }
    static File marker(android.content.Context context) { return new File(directory(context), MARKER_FILE); }
    static Uri uri(android.content.Context context) { return Uri.parse("content://" + context.getPackageName() + ".updates/apk/latest.apk"); }

    @Override public boolean onCreate() { return true; }
    private void requireUri(Uri uri) {
        if (getContext() == null || !uri(getContext()).equals(uri)) throw new SecurityException("Percorso non autorizzato.");
    }
    @Override public String getType(Uri uri) { requireUri(uri); return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        requireUri(uri);
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor result = new MatrixCursor(columns, 1); Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "Topperia.apk";
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = apk(getContext()).length();
        }
        result.addRow(row); return result;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireUri(uri); if (!"r".equals(mode)) throw new SecurityException("L'aggiornamento è di sola lettura.");
        try {
            File apk = apk(getContext()), marker = marker(getContext());
            if (!apk.isFile() || !marker.isFile() || marker.length() > 32768 || apk.length() > UpdatePolicy.MAX_APK_BYTES)
                throw new FileNotFoundException("Aggiornamento non disponibile.");
            JSONObject info = new JSONObject(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
            if (!getContext().getPackageName().equals(info.getString("packageName"))
                    || info.getLong("sizeBytes") != apk.length()
                    || !info.getString("sha256").equals(UpdatePolicy.sha256(apk)))
                throw new FileNotFoundException("Aggiornamento non verificato.");
            return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception invalid) {
            FileNotFoundException error = new FileNotFoundException("Aggiornamento non verificato."); error.initCause(invalid); throw error;
        }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new SecurityException("Sola lettura."); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new SecurityException("Sola lettura."); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new SecurityException("Sola lettura."); }
}
