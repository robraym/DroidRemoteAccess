package com.droid.remoteaccess.services;

import android.content.Context;
import android.os.Bundle;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.Methods;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class BrokerMessaging {

    public static final String GLOBAL_TOPIC = "droidremoteaccess_global_v2";
    private static final String BASE_URL = "https://ntfy.sh/";
    private static final String TOPIC_PREFIX = "droidremoteaccess_";

    private BrokerMessaging() {
    }

    public static String getDeviceTopic(Context context) {
        return getDeviceTopicForId(Methods.getIDDevice(context));
    }

    public static String getDeviceTopicForId(String deviceId) {
        return TOPIC_PREFIX + sanitizeTopic(deviceId);
    }

    public static void publishContact(Context context, String id, String email, String token, String device) throws Exception {
        JSONObject data = new JSONObject();
        data.put(Constantes.ID_FROM, id);
        data.put(Constantes.EMAIL_FROM, email);
        data.put(Constantes.TOKEN_FROM, token);
        data.put(Constantes.DEVICE_FROM, device);
        publish(GLOBAL_TOPIC, data);
    }

    public static void publishToToken(String token, Bundle data) throws Exception {
        if (token == null || token.isEmpty()) {
            return;
        }
        publish(sanitizeTopic(token), toJson(data));
    }

    public static void publishCommand(String token, Bundle data) throws Exception {
        Exception firstError = null;
        try {
            publishToToken(token, data);
        } catch (Exception ex) {
            firstError = ex;
        }

        try {
            publish(GLOBAL_TOPIC, toJson(data));
            return;
        } catch (Exception ex) {
            if (firstError == null) {
                firstError = ex;
            }
        }

        if (firstError != null) {
            throw firstError;
        }
    }

    public static void publishResponse(String token, Bundle data) throws Exception {
        publishCommand(token, data);
    }

    public static void publishAttachment(String token, File file, String fileName, String contentType, Bundle data) throws Exception {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token de destino vazio");
        }
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Arquivo para envio não encontrado");
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + sanitizeTopic(token)).openConnection();
        conn.setRequestMethod("PUT");
        String safeFileName = sanitizeHeader(fileName);
        String messagePayload = sanitizeHeader(toJson(data).toString());
        conn.setRequestProperty("Filename", safeFileName);
        conn.setRequestProperty("X-Filename", safeFileName);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Message", messagePayload);
        conn.setRequestProperty("X-Message", messagePayload);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);

        FileInputStream inputStream = new FileInputStream(file);
        OutputStream outputStream = conn.getOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        outputStream.close();

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("Broker attachment HTTP " + responseCode);
        }
        conn.disconnect();
    }

    public static PollResult poll(String topic, String since) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL)
                .append(sanitizeTopic(topic))
                .append("/json?poll=1");
        if (since != null && !since.isEmpty()) {
            urlBuilder.append("&since=").append(URLEncoder.encode(since, "UTF-8"));
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlBuilder.toString()).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);

        List<Bundle> messages = new ArrayList<>();
        String lastId = since;
        InputStream inputStream = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            JSONObject event = new JSONObject(line);
            String id = event.optString("id", "");
            if (!id.isEmpty()) {
                lastId = id;
            }
            if (!"message".equals(event.optString("event"))) {
                continue;
            }
            Bundle message = toMessageBundle(event);
            if (message != null) {
                messages.add(message);
            }
        }
        reader.close();
        conn.disconnect();
        return new PollResult(messages, lastId);
    }

    public static HttpURLConnection openStream(Context context, String since) throws Exception {
        String topics = sanitizeTopic(GLOBAL_TOPIC) + "," + sanitizeTopic(getDeviceTopic(context));
        StringBuilder urlBuilder = new StringBuilder(BASE_URL)
                .append(topics)
                .append("/json");
        if (since != null && !since.isEmpty()) {
            urlBuilder.append("?since=").append(URLEncoder.encode(since, "UTF-8"));
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlBuilder.toString()).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(0);
        return conn;
    }

    public static StreamEvent parseStreamEvent(String line) throws Exception {
        JSONObject event = new JSONObject(line);
        String id = event.optString("id", "");
        if (!"message".equals(event.optString("event"))) {
            return new StreamEvent(id, event.optString("topic", ""), null);
        }
        return new StreamEvent(id, event.optString("topic", ""), toMessageBundle(event));
    }

    private static void publish(String topic, JSONObject data) throws Exception {
        byte[] bytes = data.toString().getBytes("UTF-8");
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + sanitizeTopic(topic)).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        OutputStream outputStream = conn.getOutputStream();
        outputStream.write(bytes);
        outputStream.close();
        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("Broker HTTP " + responseCode);
        }
        conn.disconnect();
    }

    private static String sanitizeTopic(String value) {
        return value.replace("ntfy:", "").replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ");
    }

    private static JSONObject toJson(Bundle bundle) throws Exception {
        JSONObject json = new JSONObject();
        if (bundle == null) {
            return json;
        }
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value != null) {
                json.put(key, String.valueOf(value));
            }
        }
        return json;
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

    private static Bundle toMessageBundle(JSONObject event) throws Exception {
        String messageText = event.optString("message", "");
        Bundle bundle = null;
        if (messageText.startsWith("{")) {
            try {
                bundle = toBundle(new JSONObject(messageText));
            } catch (Exception ignored) {
                bundle = null;
            }
        } else if (event.has("attachment")) {
            bundle = new Bundle();
            bundle.putString(Constantes.MESSAGE, messageText);
        }

        JSONObject attachment = event.optJSONObject("attachment");
        if (attachment != null) {
            if (bundle == null) {
                bundle = new Bundle();
            }
            bundle.putString(Constantes.FILE_ATTACHMENT_URL, attachment.optString("url", ""));
            bundle.putString(Constantes.FILE_ATTACHMENT_NAME, attachment.optString("name", ""));
            bundle.putString(Constantes.FILE_ATTACHMENT_MIME, attachment.optString("type", ""));
            bundle.putString(Constantes.FILE_ATTACHMENT_SIZE, attachment.optString("size", ""));
        }

        return bundle;
    }

    public static final class PollResult {
        public final List<Bundle> messages;
        public final String lastId;

        PollResult(List<Bundle> messages, String lastId) {
            this.messages = messages;
            this.lastId = lastId;
        }
    }

    public static final class StreamEvent {
        public final String id;
        public final String topic;
        public final Bundle message;

        StreamEvent(String id, String topic, Bundle message) {
            this.id = id;
            this.topic = topic;
            this.message = message;
        }
    }
}
