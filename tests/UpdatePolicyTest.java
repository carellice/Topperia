import it.topperia.game.update.UpdatePolicy;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/** Standalone update trust/monotonicity regression tests, without Android or network. */
public final class UpdatePolicyTest {
    private static int assertions;
    private static final String SHA = String.join("", Collections.nCopies(64, "a"));
    private static final String APK = "Topperia-1.1.2.apk";
    private static final String URL = "https://github.com/carellice/Topperia/releases/download/v1.1.2/" + APK;
    public static void main(String[] args) throws Exception {
        UpdatePolicy.Metadata valid = manifest(1, 4, "1.1.2", "it.topperia.game", APK, SHA, 100, "v1.1.2");
        UpdatePolicy.validateMetadata(valid, "it.topperia.game", "v1.1.2", 100); check(true, "valid manifest accepted");
        invalid(() -> validate(manifest(2,4,"1.1.2","it.topperia.game",APK,SHA,100,"v1.1.2")), "unknown schema");
        invalid(() -> validate(manifest(1,0,"1.1.2","it.topperia.game",APK,SHA,100,"v1.1.2")), "zero version");
        invalid(() -> validate(manifest(1,2100000001L,"1.1.2","it.topperia.game",APK,SHA,100,"v1.1.2")), "version overflow");
        invalid(() -> validate(manifest(1,4,"1.1.2-beta","it.topperia.game",APK,SHA,100,"v1.1.2-beta")), "prerelease version");
        invalid(() -> validate(manifest(1,4,"01.1.2","it.topperia.game",APK,SHA,100,"v01.1.2")), "ambiguous semver");
        invalid(() -> validate(manifest(1,4,"1.1.2","other.package",APK,SHA,100,"v1.1.2")), "other application");
        invalid(() -> validate(manifest(1,4,"1.1.2","it.topperia.game","../Topperia.apk",SHA,100,"v1.1.2")), "APK traversal");
        invalid(() -> validate(manifest(1,4,"1.1.2","it.topperia.game",APK,"abcd",100,"v1.1.2")), "short hash");
        invalid(() -> validate(manifest(1,4,"1.1.2","it.topperia.game",APK,SHA,0,"v1.1.2")), "empty APK");
        invalid(() -> validate(manifest(1,4,"1.1.2","it.topperia.game",APK,SHA,UpdatePolicy.MAX_APK_BYTES+1,"v1.1.2")), "APK limit");
        invalid(() -> validate(manifest(1,4,"1.1.2","it.topperia.game",APK,SHA,100,"v1.1.3")), "tag mismatch");
        invalid(() -> UpdatePolicy.validateMetadata(valid,"it.topperia.game","v1.1.3",100), "release mismatch");
        invalid(() -> UpdatePolicy.validateMetadata(valid,"it.topperia.game","v1.1.2",101), "asset size mismatch");
        check(UpdatePolicy.isNewer(4,3), "higher code is an update");
        check(!UpdatePolicy.isNewer(3,3), "same code is not an update");
        check(!UpdatePolicy.isNewer(2,3), "downgrade rejected");
        check(!UpdatePolicy.isNewer(Long.MAX_VALUE,3), "out of range code rejected");
        check(UpdatePolicy.isNewer(2100000000L,3), "maximum supported code accepted");
        check(!UpdatePolicy.isNewer(0,-1), "zero code rejected even for invalid installed state");
        check(!UpdatePolicy.isNewer(4,-1), "invalid installed state rejected");
        UpdatePolicy.requireReleaseAssetUrl(URL,"carellice","Topperia","v1.1.2",APK); check(true,"canonical asset URL accepted");
        invalid(() -> source(URL.replace("https:","http:")), "HTTP rejected");
        invalid(() -> source(URL.replace("github.com","github.com.evil.example")), "lookalike host rejected");
        invalid(() -> source(URL.replace("github.com","evil.example@github.com")), "userinfo rejected");
        invalid(() -> source(URL.replace("carellice/","another/")), "another owner rejected");
        invalid(() -> source(URL.replace("Topperia/","Another/")), "another repository rejected");
        invalid(() -> source(URL.replace("v1.1.2/","v1.1.3/")), "another release rejected");
        invalid(() -> source(URL+"?download=1"), "query rejected on source URL");
        invalid(() -> source(URL+"#fragment"), "fragment rejected");
        invalid(() -> source(URL.replace("github.com","github.com:443")), "explicit port rejected");
        invalid(() -> source(URL.replace(APK,"%2e%2e/"+APK)), "encoded traversal rejected");
        check(UpdatePolicy.trustedAssetRedirect(URL,URL),"same GitHub URL allowed");
        check(UpdatePolicy.trustedAssetRedirect("https://release-assets.githubusercontent.com/asset?id=token",URL),"GitHub asset CDN allowed");
        check(UpdatePolicy.trustedAssetRedirect("https://objects.githubusercontent.com/github-production-release-asset/a",URL),"GitHub object CDN allowed");
        check(!UpdatePolicy.trustedAssetRedirect("http://release-assets.githubusercontent.com/asset",URL),"CDN HTTP rejected");
        check(!UpdatePolicy.trustedAssetRedirect("https://release-assets.githubusercontent.com.evil.test/asset",URL),"CDN lookalike rejected");
        check(!UpdatePolicy.trustedAssetRedirect("https://example.org/asset",URL),"arbitrary redirect rejected");
        check(!UpdatePolicy.trustedAssetRedirect(URL.replace("Topperia/","Other/"),URL),"another repository redirect rejected");
        check(!UpdatePolicy.trustedAssetRedirect("https://github.com/evil/payload",URL),"arbitrary GitHub redirect rejected");
        check(!UpdatePolicy.validAssetName("../../thing.apk"),"path separators forbidden");
        check(!UpdatePolicy.validAssetName("Topperia.exe"),"non APK forbidden");
        check(!UpdatePolicy.validAssetName("Topperia.apk.exe"),"double extension forbidden");
        check(!UpdatePolicy.validAssetName("Topperia%2f.apk"),"encoded separator forbidden");
        check(!UpdatePolicy.validAssetName("Topperia..apk"),"parent sequence forbidden");
        check(UpdatePolicy.sameSignerSet(Arrays.asList(SHA),Arrays.asList(SHA.toUpperCase())),"certificate digest normalized");
        String second = SHA.replace('a','b');
        check(UpdatePolicy.sameSignerSet(Arrays.asList(SHA,second),Arrays.asList(second,SHA)),"multisigner order irrelevant");
        check(!UpdatePolicy.sameSignerSet(Arrays.asList(SHA),Arrays.asList(second)),"different certificate rejected");
        check(!UpdatePolicy.sameSignerSet(Arrays.asList(SHA),Collections.emptyList()),"unsigned APK rejected");
        check(!UpdatePolicy.sameSignerSet(Arrays.asList(SHA,SHA),Arrays.asList(SHA)),"duplicate signers rejected");
        check(!UpdatePolicy.sameSignerSet(null,Arrays.asList(SHA)),"missing signing identity rejected");
        check(UpdatePolicy.sha256("abc".getBytes(StandardCharsets.UTF_8)).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"known SHA256 vector");
        File temp = File.createTempFile("topperia-policy-", ".bin");
        try { Files.write(temp.toPath(),"abc".getBytes(StandardCharsets.UTF_8)); check(UpdatePolicy.sha256(temp).equals(UpdatePolicy.sha256("abc".getBytes(StandardCharsets.UTF_8))),"streamed file hash"); }
        finally { temp.delete(); }
        System.out.println("UpdatePolicyTest: PASS " + assertions + " assertions");
    }
    private static UpdatePolicy.Metadata manifest(int schema,long code,String version,String pkg,String apk,String hash,long size,String tag) {return new UpdatePolicy.Metadata(schema,code,version,pkg,apk,hash,size,tag);}
    private static void validate(UpdatePolicy.Metadata metadata) {UpdatePolicy.validateMetadata(metadata,"it.topperia.game","v1.1.2",100);}
    private static void source(String url) {UpdatePolicy.requireReleaseAssetUrl(url,"carellice","Topperia","v1.1.2",APK);}
    private static void invalid(Runnable action,String message) {boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,message);}
    private static void check(boolean condition,String message) {assertions++;if(!condition)throw new AssertionError(message);}
}
