package com.droid.remoteaccess.others;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.droid.remoteaccess.R;
import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.services.BrokerMessaging;

public final class DevicePresence {

    public static final long HEARTBEAT_INTERVAL_MS = 30000;
    public static final String STATE_ONLINE = "online";
    public static final String STATE_OFFLINE = "offline";
    public static final String STATE_UNKNOWN = "unknown";

    private static final String TAG = "DevicePresence";
    private static final long ONLINE_MAX_AGE_MS = 75000;
    private static final String PREF_LAST_SEEN_PREFIX = "presence_last_seen_";
    private static final String PREF_SCREEN_ON_PREFIX = "presence_screen_on_";

    private DevicePresence() {
    }

    public static void publishCurrentAsync(final Context context) {
        if (context == null) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    publishCurrent(context.getApplicationContext());
                } catch (Exception ex) {
                    Log.d(TAG, "Falha ao publicar presença", ex);
                }
            }
        }, "DevicePresencePublish");
        thread.start();
    }

    public static void publishCurrent(Context context) throws Exception {
        if (context == null) {
            return;
        }
        BrokerMessaging.publishPresence(context.getApplicationContext(), isScreenInteractive(context));
    }

    public static void updateFromMessage(Context context, android.os.Bundle data) {
        if (context == null || data == null) {
            return;
        }
        String deviceId = data.getString(Constantes.ID_FROM);
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }

        boolean screenOn = parseBoolean(data.getString(Constantes.PRESENCE_SCREEN_ON));
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit()
                .putLong(lastSeenKey(deviceId), System.currentTimeMillis())
                .putBoolean(screenOnKey(deviceId), screenOn)
                .apply();

        Intent intent = new Intent(Constantes.RECEIVERPRESENCESTATUS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(Constantes.ID_FROM, deviceId);
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(intent);
    }

    public static String getStatusText(Context context, String deviceId) {
        String state = getStatusState(context, deviceId);
        if (STATE_ONLINE.equals(state)) {
            return context.getString(R.string.device_presence_online_now);
        }
        if (STATE_OFFLINE.equals(state)) {
            return context.getString(R.string.device_presence_offline);
        }
        return context.getString(R.string.device_presence_unknown);
    }

    public static String getStatusState(Context context, String deviceId) {
        if (context == null || deviceId == null || deviceId.isEmpty()) {
            return STATE_UNKNOWN;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        long lastSeen = preferences.getLong(lastSeenKey(deviceId), 0L);
        if (lastSeen <= 0L) {
            return STATE_UNKNOWN;
        }
        boolean fresh = System.currentTimeMillis() - lastSeen <= ONLINE_MAX_AGE_MS;
        boolean screenOn = preferences.getBoolean(screenOnKey(deviceId), false);
        return fresh && screenOn ? STATE_ONLINE : STATE_OFFLINE;
    }

    public static int getStatusColorResId(Context context, String deviceId) {
        String state = getStatusState(context, deviceId);
        if (STATE_ONLINE.equals(state)) {
            return R.color.presenceOnline;
        }
        if (STATE_OFFLINE.equals(state)) {
            return R.color.presenceOffline;
        }
        return R.color.presenceUnknown;
    }

    public static boolean isScreenInteractive(Context context) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager == null || powerManager.isInteractive();
        } catch (Exception ex) {
            return true;
        }
    }

    private static boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static String lastSeenKey(String deviceId) {
        return PREF_LAST_SEEN_PREFIX + deviceId;
    }

    private static String screenOnKey(String deviceId) {
        return PREF_SCREEN_ON_PREFIX + deviceId;
    }
}
