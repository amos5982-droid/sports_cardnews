package kr.cardnews.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.widget.Toast;

import java.io.OutputStream;

/**
 * 카드뉴스 제작기를 앱처럼 여는 껍데기.
 * 웹 주소(AndroidManifest의 start_url)를 WebView로 열고, 앱에서 막히는 것만 채워 준다.
 *  - 저장(blob:, data: 내려받기)은 사진 앱의 Pictures/카드뉴스, 그 밖의 파일은 Download 폴더에 넣는다
 *  - 새 창(제미나이, 구글 계정 선택)은 기기의 브라우저나 앱으로 연다
 *  - 이미지 고르기(디자인 참고 이미지)를 연결한다
 */
public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private String startUrl = "https://example.invalid/";
    private String host = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Bundle meta = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA).metaData;
            if (meta != null && meta.getString("start_url") != null) startUrl = meta.getString("start_url");
        } catch (Exception e) {
            // 기본 주소 그대로
        }
        host = Uri.parse(startUrl).getHost();
        if (host == null) host = "";

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Saver(), "AndroidSave");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeExternal(request.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // window.open으로 열려는 주소를 받아 기기의 브라우저로 넘긴다
                WebView temp = new WebView(MainActivity.this);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        openExternal(request.getUrl());
                        v.destroy();
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                        if (url != null && !url.equals("about:blank")) {
                            openExternal(Uri.parse(url));
                            v.stopLoading();
                            v.destroy();
                        }
                    }
                });
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mime, long length) {
                String name = URLUtil.guessFileName(url, contentDisposition, mime);
                String type = (mime == null || mime.length() == 0) ? "application/octet-stream" : mime;
                if (url.startsWith("blob:")) {
                    // blob 주소는 페이지 안에서만 읽을 수 있어서, 페이지가 읽어 base64로 넘겨 받는다
                    String js = "(function(){fetch(" + quote(url) + ").then(function(r){return r.blob()}).then(function(b){"
                            + "var f=new FileReader();f.onloadend=function(){AndroidSave.save(String(f.result).split(',')[1]||'',"
                            + "b.type||" + quote(type) + "," + quote(name) + ")};f.readAsDataURL(b)})})()";
                    web.evaluateJavascript(js, null);
                } else if (url.startsWith("data:")) {
                    int comma = url.indexOf(',');
                    if (comma > 0 && url.substring(0, comma).contains("base64")) {
                        String head = url.substring(5, comma);
                        String m = head.indexOf(';') > 0 ? head.substring(0, head.indexOf(';')) : type;
                        new Saver().save(url.substring(comma + 1), m, name);
                    }
                } else {
                    openExternal(Uri.parse(url));
                }
            }
        });

        web.loadUrl(startUrl);
    }

    private static String quote(String v) {
        return "'" + v.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'";
    }

    /** 같은 사이트 안이면 앱 안에서, 아니면 true를 돌려 기기의 브라우저로 연다 */
    private boolean routeExternal(Uri u) {
        String h = u.getHost();
        if (("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && h != null && h.equals(host)) return false;
        openExternal(u);
        return true;
    }

    private void openExternal(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Exception e) {
            toast("열 수 있는 앱이 없어요: " + u);
        }
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    /** 페이지(JavaScript)에서 파일을 받아 기기에 저장한다. 이 앱의 주소만 열리므로 다른 사이트는 쓸 수 없다 */
    public class Saver {
        @JavascriptInterface
        public void save(String base64, String mime, String name) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                boolean image = mime != null && mime.startsWith("image/");
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime == null || mime.length() == 0 ? "application/octet-stream" : mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        image ? Environment.DIRECTORY_PICTURES + "/카드뉴스" : Environment.DIRECTORY_DOWNLOADS);
                ContentResolver cr = getContentResolver();
                Uri table = image ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri out = cr.insert(table, v);
                if (out == null) throw new Exception("저장 위치를 만들지 못했어요");
                OutputStream os = cr.openOutputStream(out);
                try {
                    os.write(data);
                } finally {
                    os.close();
                }
                toast((image ? "사진 앱의 카드뉴스 앨범에 저장했어요: " : "다운로드 폴더에 저장했어요: ") + name);
            } catch (Exception e) {
                toast("저장하지 못했어요: " + e.getMessage());
            }
        }
    }
}
