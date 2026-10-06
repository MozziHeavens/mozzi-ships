package com.mozzi.ships;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public class InstallReceiver extends BroadcastReceiver {
    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String id = intent.getStringExtra("id");

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmar = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmar != null) {
                confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(confirmar);
            }
            MainActivity a = MainActivity.actual;
            if (a != null) a.js("window.esperandoConfirmacion && window.esperandoConfirmacion(" + MainActivity.q(id) + ")");
            return;
        }

        boolean ok = status == PackageInstaller.STATUS_SUCCESS;
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) msg = "conflicto";
        else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) msg = "cancelado";
        else if (status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) msg = "incompatible";
        else if (status == PackageInstaller.STATUS_FAILURE_STORAGE) msg = "espacio";

        MainActivity a = MainActivity.actual;
        if (a != null) a.avisarResultado(id, ok, msg);
    }
}
