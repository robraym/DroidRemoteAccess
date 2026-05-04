package com.droid.remoteaccess.services;

import android.content.Context;
import android.os.Bundle;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.Methods;

import org.json.JSONObject;

import java.io.BufferedReader;
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
            JSONObject payload = new JSONObject(event.optString("message", "{}"));
            messages.add(toBundle(payload));
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
        JSONObject payload = new JSONObject(event.optString("message", "{}"));
        return new StreamEvent(id, event.optString("topic", ""), toBundle(payload));
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
