package com.droid.remoteaccess.services;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.DevicePresence;
import com.droid.remoteaccess.others.Methods;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FirebaseRemoteTransport {

    private static final String TAG = "FirebaseRemote";
    private static final String ROOT = "remoteAccess";
    private static final Object LOCK = new Object();

    private static boolean persistenceConfigured;
    private static boolean started;
    private static DatabaseReference devicesRef;
    private static DatabaseReference inboxRef;
    private static DatabaseReference transferInboxRef;
    private static ValueEventListener devicesListener;
    private static ChildEventListener inboxListener;
    private static ChildEventListener transferInboxListener;
    private static Context appContext;

    private FirebaseRemoteTransport() {
    }

    public static void start(Context context) {
        if (context == null || !isAvailable(context)) {
            return;
        }
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            if (started) {
                registerDeviceAsync(appContext);
                return;
            }
            started = true;
            DatabaseReference root = FirebaseDatabase.getInstance().getReference(ROOT);
            devicesRef = root.child("devices");
            inboxRef = root.child("mailboxes").child(Methods.getIDDevice(appContext));
            transferInboxRef = root.child("transfers").child(Methods.getIDDevice(appContext));
            attachDevicesListener(appContext);
            attachInboxListener(appContext);
            attachTransferInboxListener(appContext);
            registerDeviceAsync(appContext);
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            if (devicesRef != null && devicesListener != null) {
                devicesRef.removeEventListener(devicesListener);
            }
            if (inboxRef != null && inboxListener != null) {
                inboxRef.removeEventListener(inboxListener);
            }
            if (transferInboxRef != null && transferInboxListener != null) {
                transferInboxRef.removeEventListener(transferInboxListener);
            }
            devicesRef = null;
            inboxRef = null;
            transferInboxRef = null;
            devicesListener = null;
            inboxListener = null;
            transferInboxListener = null;
            started = false;
            appContext = null;
        }
    }

    public static void registerDeviceAsync(final Context context) {
        if (context == null || !isAvailable(context)) {
            return;
        }
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                registerDevice(app);
            }
        }, "FirebaseRegisterDevice");
        thread.start();
    }

    public static void updatePresenceAsync(final Context context) {
        registerDeviceAsync(context);
    }

    public static boolean sendMessageToDevice(Context context, String targetId, Bundle data) {
        if (context == null || targetId == null || targetId.isEmpty() || data == null || !isAvailable(context)) {
            return false;
        }
        try {
            Map<String, Object> message = toMap(data);
            message.put("createdAt", ServerValue.TIMESTAMP);
            FirebaseDatabase.getInstance()
                    .getReference(ROOT)
                    .child("mailboxes")
                    .child(targetId)
                    .push()
                    .setValue(message);
            Log.i(TAG, "Firebase message queued: " + data.getString(Constantes.MESSAGE));
            return true;
        } catch (Exception ex) {
            Log.d(TAG, "Failed to queue Firebase message", ex);
            return false;
        }
    }

    public static boolean sendMessageToDeviceBlocking(Context context, String targetId,
                                                       Bundle data, long timeoutMs) {
        if (context == null || targetId == null || targetId.isEmpty() || data == null || !isAvailable(context)) {
            return false;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Map<String, Object> message = toMap(data);
            message.put("createdAt", ServerValue.TIMESTAMP);
            FirebaseDatabase.getInstance()
                    .getReference(ROOT)
                    .child("mailboxes")
                    .child(targetId)
                    .push()
                    .setValue(message)
                    .addOnCompleteListener(executor, task -> {
                        success.set(task.isSuccessful());
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Firebase message failed: "
                                    + data.getString(Constantes.MESSAGE), task.getException());
                        }
                        latch.countDown();
                    });

            boolean completed = latch.await(Math.max(1000, timeoutMs), TimeUnit.MILLISECONDS);
            if (!completed) {
                Log.w(TAG, "Firebase message timed out: " + data.getString(Constantes.MESSAGE));
                return false;
            }
            Log.i(TAG, "Firebase message delivered: " + data.getString(Constantes.MESSAGE));
            return success.get();
        } catch (Exception ex) {
            Log.d(TAG, "Failed to deliver Firebase message", ex);
            return false;
        } finally {
            executor.shutdown();
        }
    }

    public static boolean saveTransferToDeviceBlocking(Context context, String targetId,
                                                       Bundle data, long timeoutMs) {
        if (context == null || targetId == null || targetId.isEmpty() || data == null || !isAvailable(context)) {
            return false;
        }
        String commandId = data.getString(Constantes.COMMAND_ID);
        if (commandId == null || commandId.isEmpty()) {
            return false;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Map<String, Object> transfer = toMap(data);
            transfer.put("createdAt", ServerValue.TIMESTAMP);
            FirebaseDatabase.getInstance()
                    .getReference(ROOT)
                    .child("transfers")
                    .child(targetId)
                    .child(commandId)
                    .setValue(transfer)
                    .addOnCompleteListener(executor, task -> {
                        success.set(task.isSuccessful());
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Firebase transfer failed: "
                                    + data.getString(Constantes.MESSAGE), task.getException());
                        }
                        latch.countDown();
                    });

            boolean completed = latch.await(Math.max(1000, timeoutMs), TimeUnit.MILLISECONDS);
            if (!completed) {
                Log.w(TAG, "Firebase transfer timed out: " + data.getString(Constantes.MESSAGE));
                return false;
            }
            Log.i(TAG, "Firebase transfer saved: " + data.getString(Constantes.MESSAGE));
            return success.get();
        } catch (Exception ex) {
            Log.d(TAG, "Failed to save Firebase transfer", ex);
            return false;
        } finally {
            executor.shutdown();
        }
    }

    public static void removeTransferAsync(Context context, String commandId) {
        if (context == null || commandId == null || commandId.isEmpty() || !isAvailable(context)) {
            return;
        }
        try {
            FirebaseDatabase.getInstance()
                    .getReference(ROOT)
                    .child("transfers")
                    .child(Methods.getIDDevice(context.getApplicationContext()))
                    .child(commandId)
                    .removeValue();
        } catch (Exception ex) {
            Log.d(TAG, "Failed to remove Firebase transfer", ex);
        }
    }

    public static boolean isAvailable(Context context) {
        try {
            if (context == null) {
                return false;
            }
            Context app = context.getApplicationContext();
            if (FirebaseApp.getApps(app).isEmpty()) {
                FirebaseApp.initializeApp(app);
            }
            if (FirebaseApp.getApps(app).isEmpty()) {
                return false;
            }
            configurePersistenceOnce();
            return true;
        } catch (Exception ex) {
            Log.d(TAG, "Firebase ainda não configurado", ex);
            return false;
        }
    }

    private static void configurePersistenceOnce() {
        synchronized (LOCK) {
            if (persistenceConfigured) {
                return;
            }
            try {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            } catch (Exception ignored) {
            }
            persistenceConfigured = true;
        }
    }

    private static void registerDevice(Context context) {
        try {
            String id = Methods.getIDDevice(context);
            boolean screenOn = DevicePresence.isScreenInteractive(context);
            Map<String, Object> values = new HashMap<>();
            values.put(Constantes.ID_FROM, id);
            values.put(Constantes.EMAIL_FROM, Methods.getEmail(context));
            values.put(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopic(context));
            values.put(Constantes.DEVICE_FROM, Methods.getNameDevice(context));
            values.put(Constantes.CONTACT_TIME, String.valueOf(System.currentTimeMillis()));
            values.put(Constantes.PRESENCE_TIME, String.valueOf(System.currentTimeMillis()));
            values.put(Constantes.PRESENCE_SCREEN_ON, screenOn ? "1" : "0");
            values.put("serverLastSeen", ServerValue.TIMESTAMP);

            DatabaseReference deviceRef = FirebaseDatabase.getInstance()
                    .getReference(ROOT)
                    .child("devices")
                    .child(id);
            deviceRef.onDisconnect().removeValue();
            deviceRef.updateChildren(values);
            Log.i(TAG, "Firebase device registered");
        } catch (Exception ex) {
            Log.d(TAG, "Failed to register Firebase device", ex);
        }
    }

    private static void attachDevicesListener(final Context context) {
        devicesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Bundle> newestByDevice = new HashMap<>();
                Set<String> activeIds = new HashSet<>();
                long now = System.currentTimeMillis();
                String currentId = Methods.getIDDevice(context);
                for (DataSnapshot child : snapshot.getChildren()) {
                    Bundle data = toBundle(child);
                    String id = data.getString(Constantes.ID_FROM);
                    if (id == null || id.isEmpty() || id.equals(currentId)) {
                        continue;
                    }
                    if (isCurrentDeviceAlias(context, data)) {
                        continue;
                    }

                    long eventTime = parseLong(data.getString(Constantes.PRESENCE_TIME));
                    if (!DevicePresence.isRecentEventTime(eventTime, now)) {
                        continue;
                    }

                    activeIds.add(id);
                    String deviceKey = normalizeDeviceForComparison(data.getString(Constantes.DEVICE_FROM));
                    if (!isReliableDeviceName(deviceKey)) {
                        deviceKey = id;
                    }

                    Bundle previous = newestByDevice.get(deviceKey);
                    if (previous == null || getSortTime(data) >= getSortTime(previous)) {
                        newestByDevice.put(deviceKey, data);
                    }
                }

                for (Bundle data : newestByDevice.values()) {
                    if (DevicePresence.updateFromMessage(context, data)) {
                        BrokerMessageHandler.upsertDiscoveredContact(
                                context,
                                data.getString(Constantes.ID_FROM),
                                data.getString(Constantes.EMAIL_FROM),
                                data.getString(Constantes.TOKEN_FROM),
                                data.getString(Constantes.DEVICE_FROM));
                    }
                }
                new Persintencia(context.getApplicationContext())
                        .ApagarContatosAusentes(activeIds, currentId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.d(TAG, "Firebase devices listener cancelled: " + error.getMessage());
            }
        };
        devicesRef.addValueEventListener(devicesListener);
    }

    private static boolean isCurrentDeviceAlias(Context context, Bundle data) {
        if (context == null || data == null) {
            return false;
        }
        String id = data.getString(Constantes.ID_FROM);
        String currentId = Methods.getIDDevice(context.getApplicationContext());
        if (id != null && id.equals(currentId)) {
            return true;
        }

        String remoteDevice = normalizeDeviceForComparison(data.getString(Constantes.DEVICE_FROM));
        String currentDevice = normalizeDeviceForComparison(Methods.getNameDevice(context.getApplicationContext()));
        return isReliableDeviceName(remoteDevice) && remoteDevice.equals(currentDevice);
    }

    private static long getSortTime(Bundle data) {
        if (data == null) {
            return 0L;
        }
        return Math.max(
                Math.max(parseLong(data.getString(Constantes.PRESENCE_TIME)),
                        parseLong(data.getString(Constantes.CONTACT_TIME))),
                parseLong(data.getString("serverLastSeen")));
    }

    private static long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static String normalizeDeviceForComparison(String value) {
        if (value == null) {
            return "";
        }
        return Methods.formatDeviceName(value)
                .toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]+", "");
    }

    private static boolean isReliableDeviceName(String normalizedDevice) {
        return normalizedDevice != null
                && !normalizedDevice.isEmpty()
                && !"APARELHOANDROID".equals(normalizedDevice);
    }

    private static void attachInboxListener(final Context context) {
        inboxListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Bundle data = toBundle(snapshot);
                String idFrom = data.getString(Constantes.ID_FROM);
                if (idFrom != null && idFrom.equals(Methods.getIDDevice(context))) {
                    snapshot.getRef().removeValue();
                    return;
                }
                BrokerMessageHandler.handleMessage(context, "/firebase", data);
                snapshot.getRef().removeValue();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.d(TAG, "Firebase inbox listener cancelled: " + error.getMessage());
            }
        };
        inboxRef.addChildEventListener(inboxListener);
    }

    private static void attachTransferInboxListener(final Context context) {
        transferInboxListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Bundle data = toBundle(snapshot);
                String idFrom = data.getString(Constantes.ID_FROM);
                if (idFrom != null && idFrom.equals(Methods.getIDDevice(context))) {
                    snapshot.getRef().removeValue();
                    return;
                }
                BrokerMessageHandler.handleMessage(context, "/firebase-transfer", data);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.d(TAG, "Firebase transfer listener cancelled: " + error.getMessage());
            }
        };
        transferInboxRef.addChildEventListener(transferInboxListener);
    }

    private static Map<String, Object> toMap(Bundle bundle) {
        Map<String, Object> map = new HashMap<>();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value != null) {
                map.put(key, String.valueOf(value));
            }
        }
        return map;
    }

    private static Bundle toBundle(DataSnapshot snapshot) {
        Bundle bundle = new Bundle();
        for (DataSnapshot child : snapshot.getChildren()) {
            Object value = child.getValue();
            bundle.putString(child.getKey(), value == null ? "" : String.valueOf(value));
        }
        return bundle;
    }
}
