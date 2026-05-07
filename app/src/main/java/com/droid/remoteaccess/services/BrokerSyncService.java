package com.droid.remoteaccess.services;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.droid.remoteaccess.R;
import com.droid.remoteaccess.others.DevicePresence;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public class BrokerSyncService extends Service {

    private static final String TAG = "BrokerSyncService";
    private static final String LAST_STREAM_ID = "broker_last_stream_id_discovery_v2";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "broker_sync";

    private volatile boolean running;
    private Thread worker;
    private Thread presenceWorker;
    private HttpURLConnection currentConnection;
    private BroadcastReceiver screenStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
        running = true;
        registerScreenStateReceiver();
        LocalDiscovery.start(getApplicationContext());
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                syncLoop();
            }
        }, TAG);
        worker.start();
    }

    private android.app.Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.sync_service_message))
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat();
        running = true;
        registerScreenStateReceiver();
        LocalDiscovery.start(getApplicationContext());
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    syncLoop();
                }
            }, TAG);
            worker.start();
        }
        return START_STICKY;
    }

    private void registerScreenStateReceiver() {
        if (screenStateReceiver != null) {
            return;
        }
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? "" : intent.getAction();
                Log.d(TAG, "Screen state changed: " + action);
                LocalDiscovery.sendAnnounceAsync(getApplicationContext());
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenStateReceiver, filter);
    }

    private void startPresenceWorker() {
        if (presenceWorker != null && presenceWorker.isAlive()) {
            return;
        }
        presenceWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        DevicePresence.publishCurrent(getApplicationContext());
                    } catch (Exception ex) {
                        Log.d(TAG, "Falha ao publicar presença", ex);
                    }
                    sleep(DevicePresence.HEARTBEAT_INTERVAL_MS);
                }
            }
        }, "DevicePresenceHeartbeat");
        presenceWorker.start();
    }

    private void startForegroundCompat() {
        android.app.Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (hasLocationPermission()) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            try {
                startForeground(NOTIFICATION_ID, notification, serviceType);
            } catch (SecurityException ex) {
                Log.w(TAG, "Location foreground type not available, using data sync only", ex);
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            }
            return;
        }
        startForeground(NOTIFICATION_ID, notification);
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (currentConnection != null) {
            currentConnection.disconnect();
        }
        if (worker != null) {
            worker.interrupt();
        }
        if (presenceWorker != null) {
            presenceWorker.interrupt();
        }
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (Exception ignored) {
            }
            screenStateReceiver = null;
        }
        LocalDiscovery.stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void syncLoop() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        while (running) {
            try {
                String since = preferences.getString(LAST_STREAM_ID, "");
                if ("all".equalsIgnoreCase(since)) {
                    since = "";
                }
                currentConnection = BrokerMessaging.openStream(this, since);
                BufferedReader reader = new BufferedReader(new InputStreamReader(currentConnection.getInputStream(), "UTF-8"));
                String line;
                while (running && (line = reader.readLine()) != null) {
                    BrokerMessaging.StreamEvent event = BrokerMessaging.parseStreamEvent(line);
                    if (event.id != null && !event.id.isEmpty()) {
                        preferences.edit().putString(LAST_STREAM_ID, event.id).apply();
                    }
                    if (event.message == null) {
                        continue;
                    }
                    BrokerMessageHandler.handleMessage(this, fromTopic(event.topic), event.message);
                }
                reader.close();
            } catch (Exception e) {
                Log.d(TAG, "Falha ao sincronizar broker", e);
                sleep(5000);
            } finally {
                if (currentConnection != null) {
                    currentConnection.disconnect();
                    currentConnection = null;
                }
            }
        }
    }

    private String fromTopic(String topic) {
        if (BrokerMessaging.GLOBAL_TOPIC.equals(topic)) {
            return "/topics/global";
        }
        return topic;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
