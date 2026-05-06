package com.droid.remoteaccess.services;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.droid.remoteaccess.feature.Constantes;

public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_DISMISS_AUDIO_NOTIFICATION = "com.droid.remoteaccess.DISMISS_AUDIO_NOTIFICATION";
    public static final String ACTION_DISMISS_FILE_NOTIFICATION = "com.droid.remoteaccess.DISMISS_FILE_NOTIFICATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || (!ACTION_DISMISS_AUDIO_NOTIFICATION.equals(intent.getAction())
                && !ACTION_DISMISS_FILE_NOTIFICATION.equals(intent.getAction()))) {
            return;
        }

        int notificationId = intent.getIntExtra(Constantes.NOTIFICATION_ID, 0);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(notificationId);
        }
    }
}
