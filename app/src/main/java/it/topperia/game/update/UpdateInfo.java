package it.topperia.game.update;

/** A validated release. Construction stays inside the updater package. */
public final class UpdateInfo {
    public final long versionCode, sizeBytes;
    public final String versionName, tag, apkAssetName, sha256, apkUrl, releaseUrl, notes;
    final UpdatePolicy.Metadata metadata;
    UpdateInfo(UpdatePolicy.Metadata metadata, String apkUrl, String releaseUrl, String notes) {
        this.metadata = metadata; this.versionCode = metadata.versionCode; this.sizeBytes = metadata.sizeBytes;
        this.versionName = metadata.versionName; this.tag = metadata.tag; this.apkAssetName = metadata.apkAssetName;
        this.sha256 = metadata.sha256; this.apkUrl = apkUrl; this.releaseUrl = releaseUrl;
        this.notes = notes == null ? "" : notes;
    }
}
