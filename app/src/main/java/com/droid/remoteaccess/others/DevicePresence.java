package com.droid.remoteaccess.others;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.droid.remoteaccess.R;
import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.services.BrokerMessaging;

public final class DevicePresence {

    public static final long HEARTBEAT_INTERVAL_MS = 300000;
    public static final String STATE_ONLINE = "online";
    public static final String STATE_OFFLINE = "offline";
    public static final String STATE_UNKNOWN = "unknown";

    private static final String TAG = "DevicePresence";
    private static final long ONLINE_MAX_AGE_MS = 180000;
    private static final long MAX_ACCEPTED_EVENT_AGE_MS = 600000;
    private static final long MAX_ACCEPTED_FUTURE_EVENT_MS = 120000;
    private static final Object SCREEN_PUBLISH_LOCK = new Object();
    private static final String PREF_LAST_SEEN_PREFIX = "presence_last_seen_";
    private static final String PREF_EVENT_TIME_PREFIX = "presence_event_time_";
    private static final String PREF_SCREEN_ON_PREFIX = "presence_screen_on_";
    private static final String PREF_OFFLINE_SINCE_PREFIX = "presence_offline_since_";
    private static int screenPublishGeneration = 0;

    private DevicePresence() {
    }

    public static void publishCurrentAsync(final Context context) {
        publishCurrentAsync(context, 0L);
    }

    public static void publishCurrentAsync(final Context context, final long delayMs) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sleep(delayMs);
                    publishCurrent(appContext);
                } catch (Exception ex) {
                    Log.d(TAG, "Falha ao publicar presença", ex);
                }
            }
        }, "DevicePresencePublish");
        thread.start();
    }

    public static void publishScreenStateAsync(final Context context, final boolean screenOn, final long delayMs) {
        publishScreenStateSeries(context, screenOn, delayMs);
    }

    public static void publishScreenStateSeries(final Context context, final boolean screenOn, final long... delaysMs) {
        if (context == null) {
            return;
        }
        final int generation = nextScreenPublishGeneration();
        if (delaysMs == null || delaysMs.length == 0) {
            publishScreenStateAsync(context, screenOn, 0L, generation);
            return;
        }
        for (long delayMs : delaysMs) {
            publishScreenStateAsync(context, screenOn, delayMs, generation);
        }
    }

    private static void publishScreenStateAsync(final Context context, final boolean screenOn, final long delayMs,
                                                final int generation) {
        final Context appContext = context.getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sleep(delayMs);
                    if (!isCurrentScreenPublishGeneration(generation)) {
                        return;
                    }
                    if (delayMs > 0L && isScreenInteractive(appContext) != screenOn) {
                        return;
                    }
                    BrokerMessaging.publishPresence(appContext, screenOn);
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

    public static boolean updateFromMessage(Context context, android.os.Bundle data) {
        if (context == null || data == null) {
            return false;
        }
        String deviceId = data.getString(Constantes.ID_FROM);
        if (deviceId == null || deviceId.isEmpty()) {
            return false;
        }

        boolean screenOn = parseBoolean(data.getString(Constantes.PRESENCE_SCREEN_ON));
        long now = System.currentTimeMillis();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        long eventTime = parsePresenceTime(data.getString(Constantes.PRESENCE_TIME));
        if (!isRecentEventTime(eventTime, now)) {
            Log.d(TAG, "Ignorando presença velha ou inválida: " + deviceId);
            return false;
        }
        long previousEventTime = preferences.getLong(eventTimeKey(deviceId), 0L);
        if (previousEventTime > 0L && eventTime < previousEventTime) {
            Log.d(TAG, "Ignorando presença antiga: " + deviceId);
            return false;
        }

        long effectiveSeenTime = Math.min(eventTime, now);
        long offlineSince = preferences.getLong(offlineSinceKey(deviceId), 0L);
        SharedPreferences.Editor editor = preferences.edit()
                .putLong(lastSeenKey(deviceId), effectiveSeenTime)
                .putLong(eventTimeKey(deviceId), eventTime)
                .putBoolean(screenOnKey(deviceId), screenOn);
        if (screenOn) {
            editor.remove(offlineSinceKey(deviceId));
        } else if (offlineSince <= 0L) {
            editor.putLong(offlineSinceKey(deviceId), effectiveSeenTime);
        }
        editor.apply();

        Intent intent = new Intent(Constantes.RECEIVERPRESENCESTATUS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(Constantes.ID_FROM, deviceId);
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(intent);
        return true;
    }

    public static String getStatusText(Context context, String deviceId) {
        String state = getStatusState(context, deviceId);
        if (STATE_ONLINE.equals(state)) {
            return context.getString(R.string.device_presence_online_now);
        }
        if (STATE_OFFLINE.equals(state)) {
            return context.getString(R.string.device_presence_offline_elapsed,
                    getOfflineDurationText(context, deviceId));
        }
        String withoutContactDuration = getWithoutContactDurationText(context, deviceId);
        if (!withoutContactDuration.isEmpty()) {
            return context.getString(R.string.device_presence_without_contact_elapsed,
                    withoutContactDuration);
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
        long ageMs = System.currentTimeMillis() - lastSeen;
        boolean screenOn = preferences.getBoolean(screenOnKey(deviceId), false);
        if (!screenOn) {
            return ageMs <= ONLINE_MAX_AGE_MS ? STATE_OFFLINE : STATE_UNKNOWN;
        }
        return ageMs <= ONLINE_MAX_AGE_MS ? STATE_ONLINE : STATE_UNKNOWN;
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

    public static boolean shouldShowInLiveList(Context context, String deviceId) {
        if (context == null || deviceId == null || deviceId.isEmpty()) {
            return false;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        long lastSeen = preferences.getLong(lastSeenKey(deviceId), 0L);
        return lastSeen > 0L && System.currentTimeMillis() - lastSeen <= ONLINE_MAX_AGE_MS;
    }

    public static boolean isScreenInteractive(Context context) {
        if (context == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            try {
                DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                if (displayManager != null) {
                    Display[] displays = displayManager.getDisplays();
                    for (Display display : displays) {
                        int state = display.getState();
                        if (state == Display.STATE_ON || state == Display.STATE_VR) {
                            return true;
                        }
                    }
                    return false;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                return powerManager.isInteractive();
            }
            return powerManager.isScreenOn();
        } catch (Exception ex) {
            return true;
        }
    }

    private static boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static long parsePresenceTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            return 0L;
        }
    }

    public static boolean isRecentEventTime(long eventTime, long now) {
        return eventTime > 0L
                && eventTime >= now - MAX_ACCEPTED_EVENT_AGE_MS
                && eventTime <= now + MAX_ACCEPTED_FUTURE_EVENT_MS;
    }

    private static int nextScreenPublishGeneration() {
        synchronized (SCREEN_PUBLISH_LOCK) {
            screenPublishGeneration++;
            return screenPublishGeneration;
        }
    }

    private static boolean isCurrentScreenPublishGeneration(int generation) {
        synchronized (SCREEN_PUBLISH_LOCK) {
            return generation == screenPublishGeneration;
        }
    }

    private static String getOfflineDurationText(Context context, String deviceId) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        long offlineSince = preferences.getLong(offlineSinceKey(deviceId), 0L);
        if (offlineSince <= 0L) {
            offlineSince = preferences.getLong(lastSeenKey(deviceId), 0L);
        }
        return formatDuration(context, offlineSince);
    }

    private static String getWithoutContactDurationText(Context context, String deviceId) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        long lastSeen = preferences.getLong(lastSeenKey(deviceId), 0L);
        return formatDuration(context, lastSeen);
    }

    private static String formatDuration(Context context, long sinceMs) {
        if (context == null || sinceMs <= 0L) {
            return "";
        }
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - sinceMs);
        long minutes = elapsedMs / 60000L;
        if (minutes < 1L) {
            return context.getString(R.string.device_presence_less_than_minute);
        }
        if (minutes < 60L) {
            return context.getResources().getQuantityString(
                    R.plurals.device_presence_minutes, (int) minutes, minutes);
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return context.getResources().getQuantityString(
                    R.plurals.device_presence_hours, (int) hours, hours);
        }
        long days = hours / 24L;
        return context.getResources().getQuantityString(
                R.plurals.device_presence_days, (int) days, days);
    }

    private static void sleep(long delayMs) {
        if (delayMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
        }
    }

    private static String lastSeenKey(String deviceId) {
        return PREF_LAST_SEEN_PREFIX + deviceId;
    }

    private static String eventTimeKey(String deviceId) {
        return PREF_EVENT_TIME_PREFIX + deviceId;
    }

    private static String screenOnKey(String deviceId) {
        return PREF_SCREEN_ON_PREFIX + deviceId;
    }

    private static String offlineSinceKey(String deviceId) {
        return PREF_OFFLINE_SINCE_PREFIX + deviceId;
    }
}
