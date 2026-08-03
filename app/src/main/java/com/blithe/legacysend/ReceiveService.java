package com.blithe.legacysend;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.blithe.legacysend.ui.MainActivity;

public final class ReceiveService extends Service {
    private static final String CHANNEL_ID = "legacysend-receive";

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "局域网接收服务",
                    NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("旧版互传正在运行")
                .setContentText("正在等待局域网文件")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        startForeground(53317, notification);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ((LegacySendApp) getApplication()).startReceiving();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
