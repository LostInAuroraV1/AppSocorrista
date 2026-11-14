package br.com.codigoderesgate;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.File;

public class MainActivity extends Activity {
    private WebView web;
    private Updater.Info pending;
    private boolean pageReady = false, openUpdateOnLoad = false;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            getWindow().setStatusBarColor(0xFF0B111B);
            getWindow().setNavigationBarColor(0xFF0B111B);
        } catch (Throwable t) { }
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B111B);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                pageReady = true;
                if (pending != null) pushUpdate();
                if (openUpdateOnLoad) { openUpdateOnLoad = false; js("window.openUpdateScreen&&openUpdateScreen()"); }
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        openUpdateOnLoad = getIntent() != null && getIntent().getBooleanExtra("abrirAtualizacao", false);
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3); } catch (Throwable t) { }
        }
        UpdateJobService.schedule(this);
        checkAsync(false);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && i.getBooleanExtra("abrirAtualizacao", false)) {
            if (pageReady) js("window.openUpdateScreen&&openUpdateScreen()"); else openUpdateOnLoad = true;
        }
    }

    void js(final String code) { runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript(code, null); } }); }

    void pushUpdate() {
        try {
            JSONObject o = new JSONObject();
            o.put("version", pending.version); o.put("notes", pending.notes);
            js("window.onAppUpdate&&onAppUpdate(" + o.toString() + ")");
        } catch (Throwable t) { }
    }

    void checkAsync(final boolean manual) {
        new Thread(new Runnable() { public void run() {
            Updater.Info inf = Updater.fetchNewer(MainActivity.this);
            if (inf != null) {
                pending = inf;
                Updater.notifyOnce(MainActivity.this, inf);
                if (pageReady) pushUpdate();
            } else if (manual) js("window.onAppNoUpdate&&onAppNoUpdate()");
        }}).start();
    }

    void downloadAndInstall() {
        if (pending == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            js("window.onAppUpdateStatus&&onAppUpdateStatus('Permita “instalar apps desconhecidos” para o Código de Resgate e toque em Atualizar agora de novo.')");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        final DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        final String name = "CodigoDeResgate-" + pending.version + ".apk";
        File old = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
        if (old.exists()) old.delete();
        DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(pending.apkUrl))
            .setTitle("Código de Resgate " + pending.version)
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
        final long id = dm.enqueue(rq);
        new Thread(new Runnable() { public void run() {
            while (true) {
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
                if (c == null || !c.moveToFirst()) { if (c != null) c.close(); return; }
                int st = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                long got = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long tot = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                c.close();
                if (st == DownloadManager.STATUS_SUCCESSFUL) {
                    js("window.onAppUpdateStatus&&onAppUpdateStatus('Download concluído. Confirme a instalação.')");
                    Intent in = new Intent(Intent.ACTION_VIEW);
                    in.setDataAndType(dm.getUriForDownloadedFile(id), "application/vnd.android.package-archive");
                    in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(in);
                    return;
                }
                if (st == DownloadManager.STATUS_FAILED) { js("window.onAppUpdateStatus&&onAppUpdateStatus('Falha no download. Verifique a internet e tente de novo.')"); return; }
                if (tot > 0) js("window.onAppUpdateStatus&&onAppUpdateStatus('Baixando… " + (got * 100 / tot) + "%')");
            }
        }}).start();
    }

    class Bridge {
        @JavascriptInterface public String version() { return Updater.currentVersion(MainActivity.this); }
        @JavascriptInterface public boolean enabled() { return !Updater.repo(MainActivity.this).isEmpty(); }
        @JavascriptInterface public void check() { checkAsync(true); }
        @JavascriptInterface public void install() { runOnUiThread(new Runnable() { public void run() { downloadAndInstall(); } }); }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(function(){var m=document.getElementById('mdl');if(m){m.remove();return 'm';}return '';})()",
            new ValueCallback<String>() {
                public void onReceiveValue(String v) {
                    if (v == null || !v.contains("m")) MainActivity.super.onBackPressed();
                }
            });
    }

    @Override protected void onPause() { super.onPause(); if (web != null) web.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
}
