package com.droid.remoteaccess.others;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

public final class DeviceNameResolver {

    private static final String TAG = "DeviceNameResolver";
    private static final String ASSET_FILE = "device-models.json";
    private static final String CACHE_NAME = "device_name_resolver";
    private static final String CACHE_JSON = "device_models_json";
    private static final String REMOTE_URL =
            "https://raw.githubusercontent.com/robraym/DroidRemoteAccess/master/app/src/main/assets/device-models.json";

    private DeviceNameResolver() {
    }

    public static boolean shouldOfferLookup(String rawDeviceName) {
        String raw = safe(rawDeviceName);
        if (raw.isEmpty() || !looksTechnical(raw)) {
            return false;
        }

        String formatted = Methods.formatDeviceName(raw);
        return looksTechnical(formatted);
    }

    public static String resolve(Context context, String rawDeviceName) {
        String raw = safe(rawDeviceName);
        if (raw.isEmpty()) {
            return "";
        }

        String formatted = Methods.formatDeviceName(raw);
        if (!looksTechnical(formatted)) {
            return formatted;
        }

        String normalized = normalize(raw);
        String resolved = findInJson(loadCachedJson(context), normalized);
        if (!resolved.isEmpty()) {
            return resolved;
        }

        resolved = findInJson(loadAssetJson(context), normalized);
        if (!resolved.isEmpty()) {
            return resolved;
        }

        String remoteJson = fetchRemoteJson();
        if (!remoteJson.isEmpty()) {
            cacheJson(context, remoteJson);
            resolved = findInJson(remoteJson, normalized);
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        return "";
    }

    private static boolean looksTechnical(String deviceName) {
        String normalized = normalize(deviceName);
        String upper = safe(deviceName).toUpperCase(Locale.US);
        return upper.contains("SM-") || normalized.matches(".*SM[A-Z0-9]{5,}.*");
    }

    private static String findInJson(String json, String normalizedModel) {
        if (json == null || json.trim().isEmpty() || normalizedModel.isEmpty()) {
            return "";
        }

        try {
            JSONObject root = new JSONObject(json);
            String exact = findBestMatch(root.optJSONObject("models"), normalizedModel);
            if (!exact.isEmpty()) {
                return exact;
            }
            return findBestMatch(root.optJSONObject("prefixes"), normalizedModel);
        } catch (Exception ex) {
            Log.d(TAG, "Nao foi possivel ler a base de modelos", ex);
            return "";
        }
    }

    private static String findBestMatch(JSONObject mapping, String normalizedModel) {
        if (mapping == null) {
            return "";
        }

        String bestName = "";
        int bestKeyLength = 0;
        Iterator<String> keys = mapping.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String normalizedKey = normalize(key);
            if (normalizedKey.isEmpty()) {
                continue;
            }
            if ((normalizedModel.equals(normalizedKey)
                    || normalizedModel.contains(normalizedKey)
                    || normalizedKey.contains(normalizedModel))
                    && normalizedKey.length() > bestKeyLength) {
                bestKeyLength = normalizedKey.length();
                bestName = safe(mapping.optString(key));
            }
        }
        return bestName;
    }

    private static String loadCachedJson(Context context) {
        if (context == null) {
            return "";
        }
        SharedPreferences preferences = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE);
        return preferences.getString(CACHE_JSON, "");
    }

    private static String loadAssetJson(Context context) {
        if (context == null) {
            return "";
        }
        try (InputStream stream = context.getAssets().open(ASSET_FILE)) {
            return readStream(stream);
        } catch (Exception ex) {
            Log.d(TAG, "Base local de modelos nao encontrada", ex);
            return "";
        }
    }

    private static String fetchRemoteJson() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(REMOTE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "RemoteAccessAndroid");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return "";
            }
            try (InputStream stream = connection.getInputStream()) {
                return readStream(stream);
            }
        } catch (Exception ex) {
            Log.d(TAG, "Nao foi possivel buscar modelos online", ex);
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void cacheJson(Context context, String json) {
        if (context == null || json == null || json.trim().isEmpty()) {
            return;
        }
        try {
            new JSONObject(json);
            context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(CACHE_JSON, json)
                    .apply();
        } catch (Exception ex) {
            Log.d(TAG, "Base online invalida", ex);
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String normalize(String value) {
        return safe(value).toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
