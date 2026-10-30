package org.blackorange.operafast;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps the process alive (with a progress notification) while downloads are running. */
public final class DownloadService extends Service {
    static final int NOTIF_ID = 77;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Downloads.init(this);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, Downloads.buildNotification(this),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, Downloads.buildNotification(this));
            }
        } catch (Exception e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
