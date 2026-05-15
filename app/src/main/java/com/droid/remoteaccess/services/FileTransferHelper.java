package com.droid.remoteaccess.services;

import android.Manifest;
import android.content.ClipData;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.droid.remoteaccess.R;
import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.recorder.DroidAudioRecorder;
import com.droid.remoteaccess.recorder.DroidCameraCaptureService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

public final class FileTransferHelper {

    private static final String TAG = "FileTransferHelper";
    private static final String RECEIVED_FOLDER = "Remote Access";
    private static final String AUDIO_MIME_TYPE = "audio/3gpp";
    private static final String VIDEO_MIME_TYPE = "video/mp4";
    private static final String PHOTO_MIME_TYPE = "image/jpeg";
    private static final String TEXT_MIME_TYPE = "text/plain";
    private static final int MAX_INLINE_TEXT_CHARS = 64000;
    private static final long FIREBASE_FILE_DELIVERY_TIMEOUT_MS = 12000;
    private static final String AUDIO_CHANNEL_ID = "received_audio_files_v2";
    private static final String VIDEO_CHANNEL_ID = "received_video_files_v1";
    private static final String PHOTO_CHANNEL_ID = "received_photo_files_v1";
    private static final String MESSAGES_CHANNEL_ID = "received_messages_files_v1";

    private FileTransferHelper() {
    }

    public static boolean uploadLatestAudio(Context context, String tokenTo, String requesterId,
                                            String requesterDevice, String commandId) {
        try {
            File audioFile = DroidAudioRecorder.getLatestAudioFile(context.getApplicationContext());
            if (audioFile == null) {
                Log.w(TAG, "Nenhum áudio gravado para enviar");
                return false;
            }

            Bundle data = new Bundle();
            data.putString(Constantes.MESSAGE, Constantes.FILE_TRANSFER_AUDIO);
            data.putString(Constantes.FILE_TRANSFER_TYPE, "audio");
            data.putString(Constantes.ID_FROM, Methods.getIDDevice(context.getApplicationContext()));
            data.putString(Constantes.EMAIL_FROM, Methods.getEmail(context.getApplicationContext()));
            data.putString(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopic(context.getApplicationContext()));
            data.putString(Constantes.DEVICE_FROM, Methods.getNameDevice(context.getApplicationContext()));
            data.putString(Constantes.ID_TO, requesterId);
            data.putString(Constantes.DEVICE_TO, requesterDevice);
            data.putString(Constantes.COMMAND_ID, commandId);
            data.putString(Constantes.FILE_ATTACHMENT_NAME, audioFile.getName());
            data.putString(Constantes.FILE_ATTACHMENT_SIZE, String.valueOf(audioFile.length()));
            data.putString(Constantes.FILE_ATTACHMENT_MIME, AUDIO_MIME_TYPE);

            if (LocalDiscovery.sendFileToDevice(context.getApplicationContext(), requesterId,
                    audioFile, audioFile.getName(), AUDIO_MIME_TYPE, data)) {
                Log.i(TAG, "Áudio enviado pela rede local: " + audioFile.getAbsolutePath());
                return true;
            }

            if (sendFileViaCloudinary(context, tokenTo, requesterId, audioFile, AUDIO_MIME_TYPE, data, "Áudio")) {
                return true;
            }

            if (CloudinaryUploadClient.isConfigured()) {
                return false;
            }

            BrokerMessaging.publishAttachment(tokenTo, audioFile, audioFile.getName(), AUDIO_MIME_TYPE, data);
            Log.i(TAG, "Áudio enviado: " + audioFile.getAbsolutePath());
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao enviar áudio", ex);
            return false;
        }
    }

    public static boolean uploadMessages(Context context, String tokenTo, String requesterId,
                                         String requesterDevice, String commandId) {
        try {
            String messages = createMessagesExportText(context.getApplicationContext());
            File messagesFile = createMessagesExportFile(context.getApplicationContext(), messages);

            Bundle data = new Bundle();
            data.putString(Constantes.MESSAGE, Constantes.FILE_TRANSFER_MESSAGES);
            data.putString(Constantes.FILE_TRANSFER_TYPE, "messages");
            data.putString(Constantes.ID_FROM, Methods.getIDDevice(context.getApplicationContext()));
            data.putString(Constantes.EMAIL_FROM, Methods.getEmail(context.getApplicationContext()));
            data.putString(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopic(context.getApplicationContext()));
            data.putString(Constantes.DEVICE_FROM, Methods.getNameDevice(context.getApplicationContext()));
            data.putString(Constantes.ID_TO, requesterId);
            data.putString(Constantes.DEVICE_TO, requesterDevice);
            data.putString(Constantes.COMMAND_ID, commandId);
            data.putString(Constantes.FILE_ATTACHMENT_NAME, messagesFile.getName());
            data.putString(Constantes.FILE_ATTACHMENT_SIZE, String.valueOf(messagesFile.length()));
            data.putString(Constantes.FILE_ATTACHMENT_MIME, TEXT_MIME_TYPE);

            if (LocalDiscovery.sendFileToDevice(context.getApplicationContext(), requesterId,
                    messagesFile, messagesFile.getName(), TEXT_MIME_TYPE, data)) {
                Log.i(TAG, "Mensagens enviadas pela rede local: " + messagesFile.getAbsolutePath());
                return true;
            }

            if (sendFileViaCloudinary(context, tokenTo, requesterId, messagesFile, TEXT_MIME_TYPE, data, "Mensagens")) {
                return true;
            }

            data.putString(Constantes.FILE_ATTACHMENT_TEXT,
                    prepareInlineMessages(context.getApplicationContext(), messages));
            data.putString(Constantes.FILE_ATTACHMENT_SIZE,
                    String.valueOf(data.getString(Constantes.FILE_ATTACHMENT_TEXT, "").length()));

            if (FirebaseRemoteTransport.sendMessageToDevice(context.getApplicationContext(), requesterId, data)) {
                Log.i(TAG, "Mensagens enviadas pelo Firebase Database");
                return true;
            }

            BrokerMessaging.publishCommand(tokenTo, data);
            Log.i(TAG, "Mensagens enviadas pelo broker como texto");
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao enviar mensagens", ex);
            return false;
        }
    }

    public static boolean uploadLatestVideo(Context context, String tokenTo, String requesterId,
                                            String requesterDevice, String commandId) {
        File videoFile = DroidCameraCaptureService.getLatestVideoFile(context.getApplicationContext());
        String cameraFacing = DroidCameraCaptureService.getLatestVideoCameraFacing(context.getApplicationContext());
        return uploadFile(context, tokenTo, requesterId, requesterDevice, commandId, videoFile,
                Constantes.FILE_TRANSFER_VIDEO, "video", VIDEO_MIME_TYPE, "Vídeo", cameraFacing);
    }

    public static boolean uploadLatestPhoto(Context context, String tokenTo, String requesterId,
                                            String requesterDevice, String commandId) {
        File photoFile = DroidCameraCaptureService.getLatestPhotoFile(context.getApplicationContext());
        String cameraFacing = DroidCameraCaptureService.getLatestPhotoCameraFacing(context.getApplicationContext());
        return uploadFile(context, tokenTo, requesterId, requesterDevice, commandId, photoFile,
                Constantes.FILE_TRANSFER_PHOTO, "photo", PHOTO_MIME_TYPE, "Foto", cameraFacing);
    }

    private static boolean uploadFile(Context context, String tokenTo, String requesterId, String requesterDevice,
                                      String commandId, File file, String transferMessage, String transferType,
                                      String mimeType, String label, String cameraFacing) {
        try {
            if (file == null) {
                Log.w(TAG, "Nenhum arquivo encontrado para enviar: " + label);
                return false;
            }

            Bundle data = new Bundle();
            data.putString(Constantes.MESSAGE, transferMessage);
            data.putString(Constantes.FILE_TRANSFER_TYPE, transferType);
            data.putString(Constantes.ID_FROM, Methods.getIDDevice(context.getApplicationContext()));
            data.putString(Constantes.EMAIL_FROM, Methods.getEmail(context.getApplicationContext()));
            data.putString(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopic(context.getApplicationContext()));
            data.putString(Constantes.DEVICE_FROM, Methods.getNameDevice(context.getApplicationContext()));
            data.putString(Constantes.ID_TO, requesterId);
            data.putString(Constantes.DEVICE_TO, requesterDevice);
            data.putString(Constantes.COMMAND_ID, commandId);
            data.putString(Constantes.FILE_ATTACHMENT_NAME, file.getName());
            data.putString(Constantes.FILE_ATTACHMENT_SIZE, String.valueOf(file.length()));
            data.putString(Constantes.FILE_ATTACHMENT_MIME, mimeType);
            if (cameraFacing != null && !cameraFacing.isEmpty()) {
                data.putString(Constantes.FILE_CAMERA_FACING, cameraFacing);
            }

            if (LocalDiscovery.sendFileToDevice(context.getApplicationContext(), requesterId,
                    file, file.getName(), mimeType, data)) {
                Log.i(TAG, label + " enviado pela rede local: " + file.getAbsolutePath());
                return true;
            }

            if (sendFileViaCloudinary(context, tokenTo, requesterId, file, mimeType, data, label)) {
                return true;
            }

            if (CloudinaryUploadClient.isConfigured()) {
                return false;
            }

            BrokerMessaging.publishAttachment(tokenTo, file, file.getName(), mimeType, data);
            Log.i(TAG, label + " enviado: " + file.getAbsolutePath());
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao enviar arquivo: " + label, ex);
            return false;
        }
    }

    private static boolean sendFileViaCloudinary(Context context, String tokenTo, String requesterId,
                                                 File file, String mimeType, Bundle data, String label) {
        if (!CloudinaryUploadClient.isConfigured()) {
            return false;
        }

        try {
            CloudinaryUploadClient.UploadResult result = CloudinaryUploadClient.upload(file, mimeType);
            data.putString(Constantes.FILE_ATTACHMENT_URL, result.secureUrl);
            data.putString(Constantes.FILE_ATTACHMENT_SIZE, String.valueOf(result.bytes > 0 ? result.bytes : file.length()));

            boolean transferSaved = FirebaseRemoteTransport.saveTransferToDeviceBlocking(
                    context.getApplicationContext(), requesterId, data, FIREBASE_FILE_DELIVERY_TIMEOUT_MS);
            boolean firebaseSent = FirebaseRemoteTransport.sendMessageToDeviceBlocking(
                    context.getApplicationContext(), requesterId, data, FIREBASE_FILE_DELIVERY_TIMEOUT_MS);
            boolean statusSent = sendAttachedTransferStatus(context, tokenTo, requesterId, data, label);

            boolean brokerSent = false;
            try {
                BrokerMessaging.publishCommand(tokenTo, data);
                brokerSent = true;
            } catch (Exception ex) {
                Log.d(TAG, "Broker não confirmou a URL do Cloudinary: " + label, ex);
            }

            if (transferSaved || firebaseSent || statusSent) {
                Log.i(TAG, label + " enviado pelo Cloudinary: " + file.getAbsolutePath());
                return true;
            }

            if (brokerSent) {
                Log.w(TAG, "Cloudinary subiu o arquivo, mas a URL só foi enviada pelo broker: " + label);
                return false;
            }

            Log.w(TAG, "Cloudinary subiu o arquivo, mas não conseguiu entregar a URL: " + label);
            return false;
        } catch (Exception ex) {
            Log.w(TAG, "Falha ao enviar pelo Cloudinary: " + label, ex);
            return false;
        }
    }

    private static boolean sendAttachedTransferStatus(Context context, String tokenTo, String requesterId,
                                                      Bundle fileData, String label) {
        String responseMessage = getTransferSentResponse(fileData.getString(Constantes.FILE_TRANSFER_TYPE));
        if (responseMessage == null || responseMessage.isEmpty()) {
            return false;
        }

        Bundle responseData = new Bundle(fileData);
        responseData.putString(Constantes.MESSAGE, responseMessage);

        boolean firebaseSent = FirebaseRemoteTransport.sendMessageToDeviceBlocking(
                context.getApplicationContext(), requesterId, responseData, FIREBASE_FILE_DELIVERY_TIMEOUT_MS);
        boolean brokerSent = false;
        try {
            BrokerMessaging.publishResponse(tokenTo, responseData);
            brokerSent = true;
        } catch (Exception ex) {
            Log.d(TAG, "Status com anexo não enviado pelo broker: " + label, ex);
        }
        return firebaseSent || brokerSent;
    }

    private static String getTransferSentResponse(String transferType) {
        if ("audio".equalsIgnoreCase(transferType)) {
            return "r:ua:sent";
        }
        if ("video".equalsIgnoreCase(transferType)) {
            return "r:uv:sent";
        }
        if ("photo".equalsIgnoreCase(transferType)) {
            return "r:up:sent";
        }
        if ("messages".equalsIgnoreCase(transferType)) {
            return "r:um:sent";
        }
        return "";
    }

    public static File downloadAttachment(Context context, Bundle data) throws Exception {
        String url = data.getString(Constantes.FILE_ATTACHMENT_URL);
        String inlineText = data.getString(Constantes.FILE_ATTACHMENT_TEXT);

        String fileName = safeFileName(data.getString(Constantes.FILE_ATTACHMENT_NAME));
        if (fileName.isEmpty()) {
            if ("messages".equalsIgnoreCase(data.getString(Constantes.FILE_TRANSFER_TYPE))) {
                fileName = "historico_notificacoes_" + Methods.getDateTimeFormated() + ".txt";
            } else if ("video".equalsIgnoreCase(data.getString(Constantes.FILE_TRANSFER_TYPE))) {
                fileName = "video_" + Methods.getDateTimeFormated() + ".mp4";
            } else if ("photo".equalsIgnoreCase(data.getString(Constantes.FILE_TRANSFER_TYPE))) {
                fileName = "foto_" + Methods.getDateTimeFormated() + ".jpg";
            } else {
                fileName = "audio_" + Methods.getDateTimeFormated() + ".3gp";
            }
        }

        String deviceFolder = safeFileName(data.getString(Constantes.DEVICE_FROM));
        if (deviceFolder.isEmpty()) {
            deviceFolder = "aparelho";
        }

        String transferType = data.getString(Constantes.FILE_TRANSFER_TYPE);
        File outputDir = getReceivedOutputDir(context, transferType, deviceFolder);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta de recebidos");
        }

        File outputFile = uniqueFile(new File(outputDir, fileName));
        if ((url == null || url.isEmpty()) && inlineText != null) {
            writeTextFile(outputFile, inlineText);
            Log.i(TAG, "Anexo de texto recebido: " + outputFile.getAbsolutePath());
            return outputFile;
        }
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL do anexo vazia");
        }
        if (url.startsWith("localtcp://")) {
            downloadLocalTcpAttachment(url, outputFile);
            Log.i(TAG, "Anexo local baixado: " + outputFile.getAbsolutePath());
            return outputFile;
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        InputStream inputStream = conn.getInputStream();
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        outputStream.close();
        conn.disconnect();

        Log.i(TAG, "Anexo baixado: " + outputFile.getAbsolutePath());
        return outputFile;
    }

    private static File getReceivedOutputDir(Context context, String transferType, String deviceFolder) {
        File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File publicDir = new File(new File(new File(publicDownloads, RECEIVED_FOLDER),
                getReceivedTypeFolder(transferType)), deviceFolder);
        File parent = publicDir.getParentFile();
        if (parent != null && (parent.exists() || parent.mkdirs())) {
            return publicDir;
        }

        File baseDir = context.getExternalFilesDir(null);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        return new File(new File(new File(baseDir, RECEIVED_FOLDER),
                getReceivedTypeFolder(transferType)), deviceFolder);
    }

    private static String getReceivedTypeFolder(String transferType) {
        if ("audio".equalsIgnoreCase(transferType)) {
            return "Audios";
        }
        if ("video".equalsIgnoreCase(transferType)) {
            return "Videos";
        }
        if ("photo".equalsIgnoreCase(transferType)) {
            return "Fotos";
        }
        if ("messages".equalsIgnoreCase(transferType)) {
            return "Notificacoes";
        }
        return "Arquivos";
    }

    private static void downloadLocalTcpAttachment(String localUrl, File outputFile) throws Exception {
        String endpoint = localUrl.replace("localtcp://", "");
        int separator = endpoint.indexOf(':');
        if (separator <= 0 || separator == endpoint.length() - 1) {
            throw new IllegalArgumentException("URL local inválida");
        }
        String host = endpoint.substring(0, separator);
        int port = Integer.parseInt(endpoint.substring(separator + 1));

        Socket socket = new Socket(host, port);
        InputStream inputStream = socket.getInputStream();
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        outputStream.close();
        socket.close();
    }

    public static boolean showAudioReceivedNotification(Context context, File audioFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Não foi possível exibir a notificação porque POST_NOTIFICATIONS não foi concedida");
                return false;
            }

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        AUDIO_CHANNEL_ID,
                        context.getString(R.string.remote_audio_notification_title),
                        NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }

            Uri audioUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    audioFile);

            Intent chooserIntent = createAudioChooserIntent(context, audioUri);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            int notificationId = Math.abs(audioFile.getAbsolutePath().hashCode());
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    chooserIntent,
                    flags);

            Intent dismissIntent = new Intent(context, NotificationActionReceiver.class);
            dismissIntent.setAction(NotificationActionReceiver.ACTION_DISMISS_AUDIO_NOTIFICATION);
            dismissIntent.putExtra(Constantes.NOTIFICATION_ID, notificationId);
            PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    dismissIntent,
                    flags);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, AUDIO_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_remote_access)
                    .setContentTitle(context.getString(R.string.remote_audio_notification_title))
                    .setContentText(context.getString(R.string.remote_audio_notification_text))
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(context.getString(R.string.remote_audio_notification_text) + "\n" + audioFile.getName()))
                    .setContentIntent(pendingIntent)
                    .addAction(R.drawable.ic_notification_close, context.getString(R.string.remote_audio_notification_dismiss), dismissPendingIntent)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

            manager.notify(notificationId, builder.build());
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao exibir notificação de áudio recebido", ex);
            return false;
        }
    }

    public static boolean showMessagesReceivedNotification(Context context, File messagesFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Não foi possível exibir a notificação porque POST_NOTIFICATIONS não foi concedida");
                return false;
            }

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        MESSAGES_CHANNEL_ID,
                        context.getString(R.string.remote_messages_notification_title),
                        NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }

            Uri messagesUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    messagesFile);

            Intent chooserIntent = createTextChooserIntent(context, messagesUri);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            int notificationId = Math.abs(messagesFile.getAbsolutePath().hashCode());
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    chooserIntent,
                    flags);

            Intent dismissIntent = new Intent(context, NotificationActionReceiver.class);
            dismissIntent.setAction(NotificationActionReceiver.ACTION_DISMISS_FILE_NOTIFICATION);
            dismissIntent.putExtra(Constantes.NOTIFICATION_ID, notificationId);
            PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    dismissIntent,
                    flags);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MESSAGES_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_remote_access)
                    .setContentTitle(context.getString(R.string.remote_messages_notification_title))
                    .setContentText(context.getString(R.string.remote_messages_notification_text))
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(context.getString(R.string.remote_messages_notification_text) + "\n" + messagesFile.getName()))
                    .setContentIntent(pendingIntent)
                    .addAction(R.drawable.ic_notification_close, context.getString(R.string.remote_audio_notification_dismiss), dismissPendingIntent)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

            manager.notify(notificationId, builder.build());
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao exibir notificação de histórico de notificações", ex);
            return false;
        }
    }

    public static boolean showVideoReceivedNotification(Context context, File videoFile) {
        return showVideoReceivedNotification(context, videoFile, "");
    }

    public static boolean showVideoReceivedNotification(Context context, File videoFile, String cameraFacing) {
        String resolvedCameraFacing = resolveCameraFacing(cameraFacing, videoFile);
        return showReceivedFileNotification(context, videoFile, VIDEO_CHANNEL_ID,
                getVideoNotificationTitle(context, resolvedCameraFacing),
                context.getString(R.string.remote_video_notification_text),
                createVideoChooserIntent(context, getUriForFile(context, videoFile)));
    }

    public static boolean showPhotoReceivedNotification(Context context, File photoFile) {
        return showPhotoReceivedNotification(context, photoFile, "");
    }

    public static boolean showPhotoReceivedNotification(Context context, File photoFile, String cameraFacing) {
        String resolvedCameraFacing = resolveCameraFacing(cameraFacing, photoFile);
        return showReceivedFileNotification(context, photoFile, PHOTO_CHANNEL_ID,
                getPhotoNotificationTitle(context, resolvedCameraFacing),
                context.getString(R.string.remote_photo_notification_text),
                createPhotoChooserIntent(context, getUriForFile(context, photoFile)));
    }

    private static boolean showReceivedFileNotification(Context context, File file, String channelId,
                                                        int titleRes, int textRes, Intent chooserIntent) {
        return showReceivedFileNotification(context, file, channelId,
                context.getString(titleRes),
                context.getString(textRes),
                chooserIntent);
    }

    private static boolean showReceivedFileNotification(Context context, File file, String channelId,
                                                        String title, String text, Intent chooserIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Não foi possível exibir a notificação porque POST_NOTIFICATIONS não foi concedida");
                return false;
            }

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        title,
                        NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            int notificationId = Math.abs(file.getAbsolutePath().hashCode());
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    chooserIntent,
                    flags);

            Intent dismissIntent = new Intent(context, NotificationActionReceiver.class);
            dismissIntent.setAction(NotificationActionReceiver.ACTION_DISMISS_FILE_NOTIFICATION);
            dismissIntent.putExtra(Constantes.NOTIFICATION_ID, notificationId);
            PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    dismissIntent,
                    flags);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_stat_remote_access)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(text + "\n" + file.getName()))
                    .setContentIntent(pendingIntent)
                    .addAction(R.drawable.ic_notification_close, context.getString(R.string.remote_audio_notification_dismiss), dismissPendingIntent)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

            manager.notify(notificationId, builder.build());
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao exibir notificação de arquivo recebido", ex);
            return false;
        }
    }

    private static String getVideoNotificationTitle(Context context, String cameraFacing) {
        if (isBackCamera(cameraFacing)) {
            return context.getString(R.string.remote_video_notification_title_back);
        }
        if (isFrontCamera(cameraFacing)) {
            return context.getString(R.string.remote_video_notification_title_front);
        }
        return context.getString(R.string.remote_video_notification_title);
    }

    private static String getPhotoNotificationTitle(Context context, String cameraFacing) {
        if (isBackCamera(cameraFacing)) {
            return context.getString(R.string.remote_photo_notification_title_back);
        }
        if (isFrontCamera(cameraFacing)) {
            return context.getString(R.string.remote_photo_notification_title_front);
        }
        return context.getString(R.string.remote_photo_notification_title);
    }

    private static boolean isBackCamera(String cameraFacing) {
        return Constantes.CAMERA_FACING_BACK.equalsIgnoreCase(cameraFacing);
    }

    private static boolean isFrontCamera(String cameraFacing) {
        return Constantes.CAMERA_FACING_FRONT.equalsIgnoreCase(cameraFacing);
    }

    public static String resolveCameraFacing(String cameraFacing, File file) {
        String fromFileName = getCameraFacingFromFileName(file);
        if (!fromFileName.isEmpty()) {
            return fromFileName;
        }
        return normalizeCameraFacing(cameraFacing);
    }

    private static String normalizeCameraFacing(String cameraFacing) {
        if (cameraFacing == null) {
            return "";
        }
        if (cameraFacing.equalsIgnoreCase(Constantes.CAMERA_FACING_BACK)
                || cameraFacing.equalsIgnoreCase("traseira")
                || cameraFacing.equalsIgnoreCase("traseiro")) {
            return Constantes.CAMERA_FACING_BACK;
        }
        if (cameraFacing.equalsIgnoreCase(Constantes.CAMERA_FACING_FRONT)
                || cameraFacing.equalsIgnoreCase("frontal")) {
            return Constantes.CAMERA_FACING_FRONT;
        }
        return "";
    }

    private static String getCameraFacingFromFileName(File file) {
        if (file == null || file.getName() == null) {
            return "";
        }
        String fileName = file.getName().toLowerCase(java.util.Locale.US);
        if (fileName.contains("traseir") || fileName.contains("back")) {
            return Constantes.CAMERA_FACING_BACK;
        }
        if (fileName.contains("frontal") || fileName.contains("front")) {
            return Constantes.CAMERA_FACING_FRONT;
        }
        return "";
    }

    public static void openAudioFile(Context context, File audioFile) {
        try {
            Uri audioUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    audioFile);
            context.startActivity(createAudioChooserIntent(context, audioUri));
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao abrir áudio recebido", ex);
        }
    }

    public static void openTextFile(Context context, File textFile) {
        try {
            Uri textUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    textFile);
            context.startActivity(createTextChooserIntent(context, textUri));
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao abrir histórico de notificações recebido", ex);
        }
    }

    public static void openVideoFile(Context context, File videoFile) {
        try {
            context.startActivity(createVideoChooserIntent(context, getUriForFile(context, videoFile)));
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao abrir vídeo recebido", ex);
        }
    }

    public static void openPhotoFile(Context context, File photoFile) {
        try {
            context.startActivity(createPhotoChooserIntent(context, getUriForFile(context, photoFile)));
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao abrir foto recebida", ex);
        }
    }

    private static Intent createAudioChooserIntent(Context context, Uri audioUri) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(audioUri, AUDIO_MIME_TYPE);
        openIntent.setClipData(ClipData.newRawUri("audio", audioUri));
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooserIntent = Intent.createChooser(openIntent, context.getString(R.string.remote_audio_open_chooser));
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooserIntent;
    }

    private static Intent createTextChooserIntent(Context context, Uri textUri) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(textUri, TEXT_MIME_TYPE);
        openIntent.setClipData(ClipData.newRawUri("messages", textUri));
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooserIntent = Intent.createChooser(openIntent, context.getString(R.string.remote_messages_open_chooser));
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooserIntent;
    }

    private static Intent createVideoChooserIntent(Context context, Uri videoUri) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(videoUri, VIDEO_MIME_TYPE);
        openIntent.setClipData(ClipData.newRawUri("video", videoUri));
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooserIntent = Intent.createChooser(openIntent, context.getString(R.string.remote_video_open_chooser));
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooserIntent;
    }

    private static Intent createPhotoChooserIntent(Context context, Uri photoUri) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(photoUri, PHOTO_MIME_TYPE);
        openIntent.setClipData(ClipData.newRawUri("photo", photoUri));
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooserIntent = Intent.createChooser(openIntent, context.getString(R.string.remote_photo_open_chooser));
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooserIntent;
    }

    private static Uri getUriForFile(Context context, File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file);
    }

    private static File createMessagesExportFile(Context context) throws Exception {
        return createMessagesExportFile(context, createMessagesExportText(context));
    }

    private static String createMessagesExportText(Context context) {
        Persintencia persintencia = new Persintencia(context);
        String messages = persintencia.ObterMensagens(Methods.getIDDevice(context)).toString();
        if (messages.trim().isEmpty()) {
            messages = context.getString(R.string.remote_messages_empty_export) + "\n";
        }
        return messages;
    }

    private static File createMessagesExportFile(Context context, String messages) throws Exception {
        File baseDir = context.getExternalFilesDir(null);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        File outputDir = new File(baseDir, "exports");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta de exportação.");
        }

        File outputFile = uniqueFile(new File(outputDir, "historico_notificacoes_" + Methods.getDateTimeFormated() + ".txt"));
        writeTextFile(outputFile, messages);
        return outputFile;
    }

    private static String prepareInlineMessages(Context context, String messages) {
        if (messages == null) {
            return "";
        }
        if (messages.length() <= MAX_INLINE_TEXT_CHARS) {
            return messages;
        }
        return messages.substring(0, MAX_INLINE_TEXT_CHARS)
                + "\n\n" + context.getString(R.string.remote_messages_truncated_export) + "\n";
    }

    private static void writeTextFile(File outputFile, String text) throws Exception {
        Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
        writer.write('\uFEFF');
        writer.write(repararAcentuacaoQuebrada(text == null ? "" : text));
        writer.close();
    }

    private static String repararAcentuacaoQuebrada(String value) {
        if (value == null || (!value.contains("Ã") && !value.contains("Â") && !value.contains("�"))) {
            return value == null ? "" : value;
        }

        try {
            java.nio.charset.Charset latin1 = java.nio.charset.Charset.forName("ISO-8859-1");
            if (!latin1.newEncoder().canEncode(value)) {
                return value;
            }
            String repaired = new String(value.getBytes("ISO-8859-1"), "UTF-8");
            return calcularPontuacaoAcentuacaoQuebrada(repaired) < calcularPontuacaoAcentuacaoQuebrada(value)
                    ? repaired
                    : value;
        } catch (Exception ignored) {
            return value;
        }
    }

    private static int calcularPontuacaoAcentuacaoQuebrada(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int score = 0;
        String[] markers = {"Ã", "Â", "�"};
        for (String marker : markers) {
            int index = value.indexOf(marker);
            while (index >= 0) {
                score++;
                index = value.indexOf(marker, index + marker.length());
            }
        }
        return score;
    }

    private static File uniqueFile(File file) {
        if (!file.exists()) {
            return file;
        }

        String name = file.getName();
        String baseName = name;
        String extension = "";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = name.substring(0, dotIndex);
            extension = name.substring(dotIndex);
        }

        int index = 1;
        File candidate;
        do {
            candidate = new File(file.getParentFile(), baseName + "_" + index + extension);
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private static String safeFileName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
    }
}
