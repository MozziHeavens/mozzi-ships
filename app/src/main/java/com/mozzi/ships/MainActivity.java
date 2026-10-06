package com.mozzi.ships;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    static MainActivity actual;
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        actual = this;
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#3FB7E8"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Puente(), "Ships");
        setContentView(web);
        pantallaCompleta();
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        actual = this;
        pantallaCompleta();
        js("window.alVolver && window.alVolver()");
    }

    @Override
    protected void onPause() {
        super.onPause();
        js("window.alSalir && window.alSalir()");
    }

    @Override
    protected void onDestroy() {
        if (actual == this) actual = null;
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void pantallaCompleta() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    void js(final String code) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    static String q(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }

    void avisarResultado(String id, boolean ok, String msg) {
        js("window.resultadoInstalacion && window.resultadoInstalacion(" + q(id) + "," + ok + "," + q(msg) + ")");
    }

    void instalarApk(String id, String archivo) {
        PackageInstaller.Session sesion = null;
        try {
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            long total = -1;
            try (AssetFileDescriptor fd = getAssets().openFd("ships/" + archivo)) {
                total = fd.getLength();
            } catch (Exception ignorar) { }
            if (total > 0) params.setSize(total);

            int sid = pi.createSession(params);
            sesion = pi.openSession(sid);
            try (InputStream in = getAssets().open("ships/" + archivo);
                 OutputStream out = sesion.openWrite("base.apk", 0, total)) {
                byte[] buf = new byte[1 << 16];
                int n;
                long hecho = 0;
                int ultimo = -1;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    hecho += n;
                    if (total > 0) {
                        int pct = (int) (hecho * 100 / total);
                        if (pct != ultimo) {
                            ultimo = pct;
                            js("window.progresoInstalacion && window.progresoInstalacion(" + q(id) + "," + pct + ")");
                        }
                    }
                }
                sesion.fsync(out);
            }

            Intent i = new Intent(this, InstallReceiver.class);
            i.putExtra("id", id);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(this, sid, i, flags);
            sesion.commit(pend.getIntentSender());
            sesion.close();
        } catch (Exception e) {
            if (sesion != null) {
                try { sesion.abandon(); } catch (Exception ignorar) { }
            }
            avisarResultado(id, false, "Error al preparar: " + e.getMessage());
        }
    }

    class Puente {
        @JavascriptInterface
        public boolean instalado(String pkg) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean abrir(String pkg) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        }

        @JavascriptInterface
        public String abis() {
            return String.join(",", Build.SUPPORTED_ABIS);
        }

        @JavascriptInterface
        public String infoShips() {
            try (InputStream in = getAssets().open("ships/info.json")) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return bo.toString("UTF-8");
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public boolean puedeInstalar() {
            return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        }

        @JavascriptInterface
        public void pedirPermiso() {
            if (Build.VERSION.SDK_INT >= 26) {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }

        @JavascriptInterface
        public void instalar(final String id, final String archivo) {
            new Thread(() -> instalarApk(id, archivo)).start();
        }
    }
}
