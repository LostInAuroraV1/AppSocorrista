package br.com.codigoderesgate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Consulta a última versão publicada nas Releases do GitHub. */
public class Updater {
    public static class Info { public String version, notes, apkUrl; }
    static final String CHANNEL = "atualizacoes";

    static String repo(Context c) {
        try { return c.getString(R.string.github_repo).trim(); } catch (Throwable t) { return ""; }
    }

    static String currentVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Throwable t) { return "0"; }
    }

    /** Retorna a versão nova, ou null se não houver (ou sem internet). Rodar fora da thread principal. */
    static Info fetchNewer(Context c) {
        String repo = repo(c);
        if (repo.isEmpty() || !repo.contains("/")) return null;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
            con.setRequestProperty("Accept", "application/vnd.github+json");
            con.setConnectTimeout(10000); con.setReadTimeout(15000);
            if (con.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l);
            JSONObject j = new JSONObject(sb.toString());
            String tag = j.optString("tag_name", "").replaceFirst("^[vV]", "");
            if (compare(tag, currentVersion(c)) <= 0) return null;
            JSONArray assets = j.optJSONArray("assets");
            String url = null;
            if (assets != null) for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").toLowerCase().endsWith(".apk")) { url = a.optString("browser_download_url"); break; }
            }
            if (url == null) return null;
            Info inf = new Info(); inf.version = tag; inf.notes = j.optString("body", ""); inf.apkUrl = url;
            return inf;
        } catch (Throwable t) { return null; }
        finally { if (con != null) con.disconnect(); }
    }

    /** Compara versões no formato 1.2.3. */
    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }
    static int num(String s) { try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (Throwable t) { return 0; } }

    /** Notifica uma única vez por versão. */
    static void notifyOnce(Context c, Info inf) {
        SharedPreferences sp = c.getSharedPreferences("upd", 0);
        if (inf.version.equals(sp.getString("notified", ""))) return;
        sp.edit().putString("notified", inf.version).apply();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Aviso quando houver uma nova versão do jogo");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class).putExtra("abrirAtualizacao", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
         .setContentTitle("Código de Resgate " + inf.version + " disponível")
         .setContentText("Toque para atualizar agora.")
         .setContentIntent(pi).setAutoCancel(true);
        try { nm.notify(42, b.build()); } catch (Throwable t) { }
    }
}
