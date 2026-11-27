package com.moviebrain.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class AutomationService extends Service {
    static volatile boolean running = false;
    private PowerManager.WakeLock wl;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String text = intent != null ? intent.getStringExtra("text") : null;
        if (text == null || text.isEmpty()) text = "Movie Brain chal raha hai";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("mb_run", "Movie Brain chal raha hai", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "mb_run")
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Movie Brain")
                .setContentText(text).setContentIntent(pi).setOngoing(true);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, n);
        if (wl == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "moviebrain:auto");
            wl.setReferenceCounted(false);
        }
        if (!wl.isHeld()) wl.acquire(10L * 60 * 60 * 1000);
        running = true;
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (wl != null && wl.isHeld()) wl.release();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
