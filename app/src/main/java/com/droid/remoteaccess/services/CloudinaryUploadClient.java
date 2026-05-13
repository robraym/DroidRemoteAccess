package com.droid.remoteaccess.services;

import android.util.Log;

import com.droid.remoteaccess.BuildConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class CloudinaryUploadClient {

    private static final String TAG = "CloudinaryUpload";
    private static final String API_BASE = "https://api.cloudinary.com/v1_1/";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 180000;

    private CloudinaryUploadClient() {
    }

    public static boolean isConfigured() {
        return !isBlank(BuildConfig.CLOUDINARY_CLOUD_NAME)
                && !isBlank(BuildConfig.CLOUDINARY_UPLOAD_PRESET);
    }

    public static UploadResult upload(File file, String contentType) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Cloudinary não configurado");
        }
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Arquivo para upload não encontrado");
        }

        String boundary = "----RemoteAccessCloudinary" + System.currentTimeMillis();
        URL url = new URL(API_BASE + BuildConfig.CLOUDINARY_CLOUD_NAME + "/"
                + getResourceType(contentType) + "/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("Accept", "application/json");

        byte[] presetPart = formFieldBytes(boundary, "upload_preset", BuildConfig.CLOUDINARY_UPLOAD_PRESET);
        byte[] fileHeader = fileHeaderBytes(boundary, "file", file, contentType);
        byte[] closing = utf8Bytes("--" + boundary + "--\r\n");
        byte[] fileEnding = utf8Bytes("\r\n");
        long contentLength = (long) presetPart.length
                + fileHeader.length
                + file.length()
                + fileEnding.length
                + closing.length;
        conn.setFixedLengthStreamingMode(contentLength);

        OutputStream outputStream = conn.getOutputStream();
        outputStream.write(presetPart);
        outputStream.write(fileHeader);
        writeFileBytes(outputStream, file);
        outputStream.write(fileEnding);
        outputStream.write(closing);
        outputStream.close();

        int responseCode = conn.getResponseCode();
        String responseBody = readResponseBody(conn, responseCode);
        conn.disconnect();

        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("Cloudinary HTTP " + responseCode + ": " + responseBody);
        }

        JSONObject json = new JSONObject(responseBody);
        String secureUrl = json.optString("secure_url", "");
        if (secureUrl.isEmpty()) {
            secureUrl = json.optString("url", "");
        }
        if (secureUrl.isEmpty()) {
            throw new IllegalStateException("Cloudinary não retornou URL do arquivo");
        }

        long bytes = json.optLong("bytes", file.length());
        String resourceType = json.optString("resource_type", "");
        Log.i(TAG, "Arquivo enviado ao Cloudinary: " + file.getName());
        return new UploadResult(secureUrl, bytes, resourceType);
    }

    private static String getResourceType(String contentType) {
        if (contentType == null) {
            return "auto";
        }
        String normalized = contentType.toLowerCase();
        if (normalized.startsWith("image/")) {
            return "image";
        }
        if (normalized.startsWith("video/") || normalized.startsWith("audio/")) {
            return "video";
        }
        if (normalized.startsWith("text/")) {
            return "raw";
        }
        return "auto";
    }

    private static byte[] formFieldBytes(String boundary, String name, String value) throws Exception {
        return utf8Bytes("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + (value == null ? "" : value)
                + "\r\n");
    }

    private static byte[] fileHeaderBytes(String boundary, String name, File file, String contentType) throws Exception {
        return utf8Bytes("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name
                + "\"; filename=\"" + sanitizeHeader(file.getName()) + "\"\r\n"
                + "Content-Type: " + (isBlank(contentType) ? "application/octet-stream" : contentType)
                + "\r\n\r\n");
    }

    private static void writeFileBytes(OutputStream outputStream, File file) throws Exception {
        FileInputStream inputStream = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
    }

    private static String readResponseBody(HttpURLConnection conn, int responseCode) throws Exception {
        InputStream inputStream = responseCode >= 200 && responseCode < 300
                ? conn.getInputStream()
                : conn.getErrorStream();
        if (inputStream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        return body.toString();
    }

    private static byte[] utf8Bytes(String value) throws Exception {
        return value.getBytes("UTF-8");
    }

    private static String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ").replace("\"", "'");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class UploadResult {
        public final String secureUrl;
        public final long bytes;
        public final String resourceType;

        UploadResult(String secureUrl, long bytes, String resourceType) {
            this.secureUrl = secureUrl;
            this.bytes = bytes;
            this.resourceType = resourceType;
        }
    }
}
