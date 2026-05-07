package com.droid.remoteaccess.services;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.DevicePresence;
import com.droid.remoteaccess.others.Methods;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalDiscovery {

    private static final String TAG = "LocalDiscovery";
    private static final String APP = "remote_access";
    private static final String TYPE_REQUEST = "request";
    private static final String TYPE_ANNOUNCE = "announce";
    private static final String TYPE_MESSAGE = "message";
    private static final int PORT = 45781;
    private static final int MAX_PACKET_BYTES = 4096;
    private static final Object LOCK = new Object();
    private static final Map<String, InetAddress> RECENT_ADDRESSES = new ConcurrentHashMap<>();

    private static volatile boolean running;
    private static Thread worker;
    private static DatagramSocket socket;
    private static Context appContext;

    private LocalDiscovery() {
    }

    public static void start(Context context) {
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            if (running && worker != null && worker.isAlive()) {
                sendAnnounceAsync(appContext);
                return;
            }
            running = true;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    listenLoop();
                }
            }, "LocalDiscovery");
            worker.start();
        }
        sendAnnounceAsync(appContext);
    }

    public static void stop() {
        synchronized (LOCK) {
            running = false;
            if (socket != null) {
                socket.close();
                socket = null;
            }
            if (worker != null) {
                worker.interrupt();
                worker = null;
            }
            appContext = null;
        }
    }

    public static void sendDiscoveryRequestAsync(Context context) {
        sendAsync(context, TYPE_REQUEST);
    }

    public static void sendAnnounceAsync(Context context) {
        sendAsync(context, TYPE_ANNOUNCE);
    }

    public static boolean sendMessageToDevice(Context context, String targetId, Bundle data) {
        if (context == null || data == null) {
            return false;
        }
        return send(TYPE_MESSAGE, context.getApplicationContext(), addressForDevice(targetId), data);
    }

    public static boolean sendFileToDevice(Context context, String targetId, File file, String fileName,
                                           String contentType, Bundle data) {
        if (context == null || targetId == null || targetId.isEmpty() || file == null
                || !file.exists() || !file.isFile() || data == null) {
            return false;
        }
        InetAddress targetAddress = addressForDevice(targetId);
        if (targetAddress == null) {
            return false;
        }

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(0);
            serverSocket.setSoTimeout(60000);
            InetAddress localAddress = getLocalAddressFor(targetAddress);
            if (localAddress == null) {
                return false;
            }

            Bundle localData = new Bundle(data);
            localData.putString(Constantes.FILE_ATTACHMENT_URL,
                    "localtcp://" + localAddress.getHostAddress() + ":" + serverSocket.getLocalPort());
            localData.putString(Constantes.FILE_ATTACHMENT_NAME, fileName);
            localData.putString(Constantes.FILE_ATTACHMENT_MIME, contentType);
            localData.putString(Constantes.FILE_ATTACHMENT_SIZE, String.valueOf(file.length()));

            serveFileOnce(serverSocket, file);
            boolean sent = send(TYPE_MESSAGE, context.getApplicationContext(), targetAddress, localData);
            if (!sent) {
                serverSocket.close();
            }
            return sent;
        } catch (Exception ex) {
            Log.d(TAG, "Failed to send local file", ex);
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (Exception ignored) {
                }
            }
            return false;
        }
    }

    private static void listenLoop() {
        DatagramSocket listenSocket = null;
        try {
            listenSocket = new DatagramSocket(PORT);
            listenSocket.setReuseAddress(true);
            listenSocket.setBroadcast(true);
            socket = listenSocket;
            byte[] buffer = new byte[MAX_PACKET_BYTES];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                listenSocket.receive(packet);
                handlePacket(packet);
            }
        } catch (SocketException ex) {
            if (running) {
                Log.d(TAG, "Local discovery socket failed", ex);
            }
        } catch (Exception ex) {
            if (running) {
                Log.d(TAG, "Local discovery failed", ex);
            }
        } finally {
            if (listenSocket != null) {
                listenSocket.close();
            }
        }
    }

    private static void handlePacket(DatagramPacket packet) throws Exception {
        Context context = appContext;
        if (context == null || packet == null || packet.getLength() <= 0) {
            return;
        }

        String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), "UTF-8");
        JSONObject json = new JSONObject(payload);
        if (!APP.equals(json.optString("app", ""))) {
            return;
        }

        String idFrom = json.optString(Constantes.ID_FROM, "");
        if (idFrom.isEmpty() || idFrom.equals(Methods.getIDDevice(context))) {
            return;
        }
        RECENT_ADDRESSES.put(idFrom, packet.getAddress());

        Bundle data = toBundle(json);
        if (TYPE_MESSAGE.equals(json.optString("type", ""))) {
            BrokerMessageHandler.handleMessage(context, "/local", data);
            return;
        }

        DevicePresence.updateFromMessage(context, data);
        BrokerMessageHandler.upsertDiscoveredContact(
                context,
                idFrom,
                json.optString(Constantes.EMAIL_FROM, ""),
                json.optString(Constantes.TOKEN_FROM, ""),
                json.optString(Constantes.DEVICE_FROM, ""));

        if (TYPE_REQUEST.equals(json.optString("type", ""))) {
            sendAnnounceToAsync(context, packet.getAddress());
        }
    }

    private static void sendAsync(final Context context, final String type) {
        if (context == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                send(type, app, null);
            }
        }, "LocalDiscoverySend");
        thread.start();
    }

    private static void sendAnnounceToAsync(final Context context, final InetAddress address) {
        if (context == null || address == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                send(TYPE_ANNOUNCE, app, address);
            }
        }, "LocalDiscoveryReply");
        thread.start();
    }

    private static void send(String type, Context context, InetAddress directAddress) {
        send(type, context, directAddress, null);
    }

    private static boolean send(String type, Context context, InetAddress directAddress, Bundle data) {
        DatagramSocket sendSocket = null;
        try {
            byte[] bytes = buildPayload(type, context, data).getBytes("UTF-8");
            sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);
            if (directAddress != null) {
                sendPacket(sendSocket, bytes, directAddress);
            } else {
                sendPacket(sendSocket, bytes, InetAddress.getByName("255.255.255.255"));
                sendToInterfaceBroadcasts(sendSocket, bytes);
            }
            Log.i(TAG, "Local discovery sent: " + type);
            return true;
        } catch (Exception ex) {
            Log.d(TAG, "Failed to send local discovery: " + type, ex);
            return false;
        } finally {
            if (sendSocket != null) {
                sendSocket.close();
            }
        }
    }

    private static void sendToInterfaceBroadcasts(DatagramSocket sendSocket, byte[] bytes) {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface == null || networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    if (interfaceAddress == null || interfaceAddress.getBroadcast() == null) {
                        continue;
                    }
                    sendPacket(sendSocket, bytes, interfaceAddress.getBroadcast());
                }
            }
        } catch (Exception ex) {
            Log.d(TAG, "Failed to send interface broadcast", ex);
        }
    }

    private static void sendPacket(DatagramSocket sendSocket, byte[] bytes, InetAddress address) throws Exception {
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);
        sendSocket.send(packet);
    }

    private static String buildPayload(String type, Context context, Bundle data) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject json = new JSONObject();
        json.put("app", APP);
        json.put("type", type);
        json.put(Constantes.ID_FROM, Methods.getIDDevice(context));
        json.put(Constantes.EMAIL_FROM, Methods.getEmail(context));
        json.put(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopic(context));
        json.put(Constantes.DEVICE_FROM, Methods.getNameDevice(context));
        json.put(Constantes.CONTACT_TIME, String.valueOf(now));
        json.put(Constantes.PRESENCE_TIME, String.valueOf(now));
        json.put(Constantes.PRESENCE_SCREEN_ON, DevicePresence.isScreenInteractive(context) ? "1" : "0");
        if (data != null) {
            for (String key : data.keySet()) {
                Object value = data.get(key);
                if (value != null) {
                    json.put(key, String.valueOf(value));
                }
            }
        }
        return json.toString();
    }

    private static InetAddress addressForDevice(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return null;
        }
        return RECENT_ADDRESSES.get(deviceId);
    }

    private static InetAddress getLocalAddressFor(InetAddress targetAddress) {
        DatagramSocket probeSocket = null;
        try {
            probeSocket = new DatagramSocket();
            probeSocket.connect(targetAddress, PORT);
            return probeSocket.getLocalAddress();
        } catch (Exception ex) {
            return null;
        } finally {
            if (probeSocket != null) {
                probeSocket.close();
            }
        }
    }

    private static void serveFileOnce(final ServerSocket serverSocket, final File file) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket client = null;
                FileInputStream inputStream = null;
                try {
                    client = serverSocket.accept();
                    inputStream = new FileInputStream(file);
                    OutputStream outputStream = client.getOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                    }
                    outputStream.flush();
                    Log.i(TAG, "Local file served: " + file.getAbsolutePath());
                } catch (Exception ex) {
                    Log.d(TAG, "Failed to serve local file", ex);
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (Exception ignored) {
                        }
                    }
                    if (client != null) {
                        try {
                            client.close();
                        } catch (Exception ignored) {
                        }
                    }
                    try {
                        serverSocket.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "LocalFileServer");
        thread.start();
    }

    private static Bundle toBundle(JSONObject json) {
        Bundle bundle = new Bundle();
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            bundle.putString(key, json.optString(key, ""));
        }
        return bundle;
    }
}
