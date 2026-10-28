package it.topperia.game.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Pure validation rules shared by the network updater and its standalone tests. */
public final class UpdatePolicy {
    public static final long MAX_APK_BYTES = 64L * 1024L * 1024L;
    private static final String VERSION = "(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)";
    private UpdatePolicy() { }

    public static final class Metadata {
        public final int schemaVersion;
        public final long versionCode;
        public final String versionName, packageName, apkAssetName, sha256, tag;
        public final long sizeBytes;
        public Metadata(int schemaVersion, long versionCode, String versionName, String packageName,
                        String apkAssetName, String sha256, long sizeBytes, String tag) {
            this.schemaVersion = schemaVersion;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.packageName = packageName;
            this.apkAssetName = apkAssetName;
            this.sha256 = sha256 == null ? null : sha256.toLowerCase(Locale.ROOT);
            this.sizeBytes = sizeBytes;
            this.tag = tag;
        }
    }

    public static void validateMetadata(Metadata metadata, String expectedPackage, String releaseTag,
                                        long assetSize) {
        if (metadata == null || metadata.schemaVersion != 1) fail("Formato dell'aggiornamento non supportato.");
        if (metadata.versionCode <= 0 || metadata.versionCode > 2100000000L) fail("Numero di versione non valido.");
        if (metadata.versionName == null || metadata.versionName.length() > 64 || !metadata.versionName.matches(VERSION))
            fail("Nome della versione non valido.");
        if (!expectedPackage.equals(metadata.packageName)) fail("L'aggiornamento non appartiene a Topperia.");
        if (!validAssetName(metadata.apkAssetName)) fail("Nome del file APK non valido.");
        if (metadata.sha256 == null || !metadata.sha256.matches("[0-9a-f]{64}")) fail("Impronta dell'aggiornamento non valida.");
        if (!(("v" + metadata.versionName).equals(metadata.tag) && metadata.tag.equals(releaseTag)))
            fail("La versione non corrisponde alla release GitHub.");
        if (metadata.sizeBytes <= 0 || metadata.sizeBytes > MAX_APK_BYTES || metadata.sizeBytes != assetSize)
            fail("Dimensione dell'aggiornamento non valida.");
    }

    public static boolean isNewer(long incomingCode, long installedCode) {
        return installedCode >= 0 && incomingCode > 0 && incomingCode > installedCode && incomingCode <= 2100000000L;
    }

    public static boolean validAssetName(String value) {
        return value != null && value.length() <= 128 && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk")
                && !value.contains("..");
    }

    public static String releaseAssetUrl(String owner, String repository, String tag, String assetName) {
        validateRepository(owner, repository);
        if (tag == null || !tag.matches("v" + VERSION) || !("update.json".equals(assetName) || validAssetName(assetName)))
            fail("Percorso della release non valido.");
        return "https://github.com/" + owner + "/" + repository + "/releases/download/" + tag + "/" + assetName;
    }

    public static void validateRepository(String owner, String repository) {
        if (owner == null || !owner.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}")
                || repository == null || !repository.matches("[A-Za-z0-9_.-]{1,100}")
                || repository.equals(".") || repository.equals("..")) fail("Repository GitHub non configurata.");
    }

    public static void requireReleaseAssetUrl(String url, String owner, String repository, String tag, String assetName) {
        URI candidate = httpsUri(url);
        URI expected = URI.create(releaseAssetUrl(owner, repository, tag, assetName));
        if (!"github.com".equalsIgnoreCase(candidate.getHost()) || !expected.getRawPath().equals(candidate.getRawPath())
                || candidate.getRawQuery() != null) fail("Il download non proviene dalla release di Topperia.");
    }

    /** Redirects are limited to GitHub's asset delivery infrastructure, always over HTTPS. */
    public static boolean trustedAssetRedirect(String url, String originalAssetUrl) {
        try {
            URI value = httpsUri(url);
            String host = value.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("github.com")) {
                URI original = httpsUri(originalAssetUrl);
                return original.getRawPath().equals(value.getRawPath()) && value.getRawQuery() == null;
            }
            return host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com")
                    || host.equals("github-releases.githubusercontent.com");
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static URI httpsUri(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getFragment() != null || uri.isOpaque()) fail("Indirizzo di aggiornamento non sicuro.");
            return uri;
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Indirizzo di aggiornamento non valido.", invalid); }
    }

    public static boolean sameSignerSet(Collection<String> installed, Collection<String> downloaded) {
        if (installed == null || downloaded == null || installed.isEmpty() || downloaded.isEmpty()) return false;
        Set<String> expected = signerSet(installed), actual = signerSet(downloaded);
        return expected != null && actual != null && expected.equals(actual);
    }
    private static Set<String> signerSet(Collection<String> values) {
        Set<String> result = new TreeSet<>();
        for (String value : values) {
            if (value == null || !value.matches("(?i)[a-f0-9]{64}")) return null;
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result.size() == values.size() ? result : null;
    }

    public static String sha256(File file) throws IOException {
        MessageDigest digest = digest();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = input.read(buffer)) >= 0) if (count > 0) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }
    public static String sha256(byte[] bytes) { return hex(digest().digest(bytes)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
