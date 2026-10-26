import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/** APK에 서명하고(v1+v2) 서명이 맞는지 확인한다. 사용법: java Sign 키저장소 비밀번호 별칭 입력.apk 출력.apk */
public class Sign {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        FileInputStream in = new FileInputStream(a[0]);
        ks.load(in, a[1].toCharArray());
        in.close();
        PrivateKey key = (PrivateKey) ks.getKey(a[2], a[1].toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(a[2]);
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("cardnews", key, Arrays.asList(cert)).build();
        new ApkSigner.Builder(Arrays.asList(cfg))
                .setInputApk(new File(a[3]))
                .setOutputApk(new File(a[4]))
                .setMinSdkVersion(29)
                .setV1SigningEnabled(false) // 안드로이드 7 이상은 v2만으로 충분해요(이 앱은 10 이상)
                .setV2SigningEnabled(true)
                .build()
                .sign();
        ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[4])).build().verify();
        System.out.println("서명 확인: " + (r.isVerified() ? "통과" : "실패") + " v2=" + r.isVerifiedUsingV2Scheme());
        for (ApkVerifier.IssueWithParams e : r.getErrors()) System.out.println("오류: " + e);
        if (!r.isVerified()) System.exit(1);
    }
}
