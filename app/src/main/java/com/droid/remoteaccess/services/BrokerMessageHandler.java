/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.droid.remoteaccess.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.feature.Contato;
import com.droid.remoteaccess.activitys.RemoteMediaCommandActivity;
import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.feature.Localizacao;
import com.droid.remoteaccess.gdrive.CreateFileActivity;
import com.droid.remoteaccess.location.DroidLocation;
import com.droid.remoteaccess.others.DevicePresence;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.recorder.DroidAudioRecorder;

import java.io.File;
import java.util.Locale;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public final class BrokerMessageHandler {

    private static final String TAG = "BrokerMessageHandler";
    private static final String PROCESSED_COMMANDS = "processed_command_ids";
    private static final String PROCESSED_RESPONSES = "processed_response_ids";
    private static final String PROCESSED_FILES = "processed_file_ids";
    private static final long AUDIO_START_RESPONSE_TIMEOUT_MS = 9000;
    private static final long MEDIA_FOREGROUND_RESPONSE_TIMEOUT_MS = 8000;


    //private String ChamadaBroadCastPorComandoTexto() {
    //return Methods.chamadaBroadCastPorComandoTexto(getIntent());
    //}

    private BrokerMessageHandler() {
    }


    public static void sendResponseToServer(Context context, String token_to, String message, Localizacao localizacao,
                                            String requesterId, String requesterDevice, String commandId) {

        try {
            Bundle data = new Bundle();

            if (message != null && !message.isEmpty()) {
                data.putString(Constantes.MESSAGE, message);
            }

            data.putString(Constantes.ID_FROM, Methods.getIDDevice(context.getApplicationContext()));
            data.putString(Constantes.EMAIL_FROM, Methods.getEmail(context.getApplicationContext()));
            data.putString(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopic(context.getApplicationContext()));
            data.putString(Constantes.DEVICE_FROM, Methods.getNameDevice(context.getApplicationContext()));
            data.putString(Constantes.ID_TO, requesterId);
            data.putString(Constantes.DEVICE_TO, requesterDevice);
            data.putString(Constantes.COMMAND_ID, commandId);

            if (localizacao != null)
            {
                data.putString(Constantes.LATITUDE, String.valueOf(localizacao.getLatitude()));
                data.putString(Constantes.LONGITUDE, String.valueOf(localizacao.getLongitude()));
            }

            boolean firebaseSent = FirebaseRemoteTransport.sendMessageToDevice(
                    context.getApplicationContext(), requesterId, data);
            boolean localSent = LocalDiscovery.sendMessageToDevice(context.getApplicationContext(), requesterId, data);
            try {
                BrokerMessaging.publishResponse(token_to, data);
                Log.i(TAG, "Response sent: " + message);
            } catch (Exception ex) {
                if (!firebaseSent && !localSent) {
                    throw ex;
                }
                Log.d(TAG, "Response sent by Firebase/local; broker failed: " + message, ex);
            }
        } catch (Exception ex) {
            Log.d(TAG, "Failed to send response: " + message, ex);
        }

    }


    public static void handleMessage(Context serviceContext, String from, Bundle data) {
        String id_from = data.getString(Constantes.ID_FROM);
        String id_to = data.getString(Constantes.ID_TO);
        String email_from = data.getString(Constantes.EMAIL_FROM);
        String token_from = data.getString(Constantes.TOKEN_FROM);
        String reply_token = data.getString(Constantes.REPLY_TOKEN);
        String device_from = data.getString(Constantes.DEVICE_FROM);
        String device_to = data.getString(Constantes.DEVICE_TO);
        String command_id = data.getString(Constantes.COMMAND_ID);
        String message = data.getString(Constantes.MESSAGE);

        Persintencia persintencia = new Persintencia(serviceContext.getApplicationContext());
        String responseToken = reply_token;
        if (responseToken == null || responseToken.isEmpty()) {
            responseToken = token_from;
        }
        if ((responseToken == null || responseToken.isEmpty()) && id_from != null && !id_from.isEmpty()) {
            responseToken = BrokerMessaging.getDeviceTopicForId(id_from);
        }

        if (Constantes.MESSAGE_DISCOVERY_REQUEST.equals(message)) {
            handleDiscoveryRequest(serviceContext, persintencia, data, id_from, email_from,
                    responseToken, device_from, command_id);
            return;
        }

        if (Constantes.MESSAGE_DISCOVERY_RESPONSE.equals(message)) {
            if (!isResponseForThisDevice(serviceContext, id_from, id_to, device_to)) {
                Log.d(TAG, "Ignoring discovery response for another device");
                return;
            }
            if (DevicePresence.updateFromMessage(serviceContext, data)) {
                Log.i(TAG, "Discovery response accepted: " + id_from);
                upsertContact(serviceContext, persintencia, id_from, email_from, responseToken, device_from);
            }
            return;
        }

        if (Constantes.MESSAGE_PRESENCE.equals(message)) {
            if (DevicePresence.updateFromMessage(serviceContext, data)) {
                upsertContact(serviceContext, persintencia, id_from, email_from, responseToken, device_from);
            }
            return;
        }

        if (message == null || message.isEmpty()) {
            if (isFreshContactAnnouncement(data)) {
                DevicePresence.updateFromMessage(serviceContext, data);
                upsertContact(serviceContext, persintencia, id_from, email_from, responseToken, device_from);
            } else {
                Log.d(TAG, "Ignoring stale contact announcement: " + id_from);
            }
            return;
        }

            if (Constantes.FILE_TRANSFER_AUDIO.equals(message)) {
                handleFileResponse(serviceContext, data, id_from, id_to, device_to, command_id,
                        "r:ua", "audio");
                return;
            }

            if (Constantes.FILE_TRANSFER_VIDEO.equals(message)) {
                handleFileResponse(serviceContext, data, id_from, id_to, device_to, command_id,
                        "r:uv", "video");
                return;
            }

            if (Constantes.FILE_TRANSFER_PHOTO.equals(message)) {
                handleFileResponse(serviceContext, data, id_from, id_to, device_to, command_id,
                        "r:up", "photo");
                return;
            }

            if (Constantes.FILE_TRANSFER_MESSAGES.equals(message)) {
                handleFileResponse(serviceContext, data, id_from, id_to, device_to, command_id,
                        "r:um", "messages");
                return;
            }

            if (message.startsWith("r:")) {
                if (!isResponseForThisDevice(serviceContext, id_from, id_to, device_to)) {
                    Log.d(TAG, "Ignoring response for another device: " + message);
                    return;
                }
                String responseKey = getResponseDedupKey(command_id, message, data);
                if (isDuplicateMessage(serviceContext, PROCESSED_RESPONSES, responseKey)) {
                    Log.d(TAG, "Ignoring duplicate response: " + responseKey);
                    return;
                }
                Log.i(TAG, "Response accepted: " + message);

                Intent mIntent = new Intent();
                mIntent.setAction(Constantes.RECEIVERRESPONSECONTROLEREMOTO);
                mIntent.addCategory(Intent.CATEGORY_DEFAULT);
                mIntent.putExtra(Constantes.MESSAGE, message);
                mIntent.putExtra(Constantes.COMMAND_ID, command_id);

                if (message.equalsIgnoreCase("r:l"))
                {
                    mIntent.putExtra(Constantes.LATITUDE, data.getString(Constantes.LATITUDE));
                    mIntent.putExtra(Constantes.LONGITUDE, data.getString(Constantes.LONGITUDE));
                }
                //
                LocalBroadcastManager.getInstance(serviceContext).sendBroadcast(mIntent);
                if (isTransferStatusWithAttachment(message, data)) {
                    handleFileResponse(serviceContext, data, id_from, id_to, device_to, command_id,
                            getTransferCompleteResponse(message), getTransferFileType(message));
                }
            } else {
                if (!isCommandForThisDevice(serviceContext, id_from, id_to, device_to)) {
                    Log.d(TAG, "Ignoring command for another device: " + message);
                    return;
                }
                if (isDuplicateMessage(serviceContext, PROCESSED_COMMANDS, command_id)) {
                    Log.d(TAG, "Ignoring duplicate command: " + command_id);
                    return;
                }
                Log.i(TAG, "Command accepted: " + message);

                if (from != null && from.startsWith("/topics/")) {
                    // message received from some topic.
                } else {
                    // normal downstream message.
                }

                if (isCameraCaptureCommand(message)) {
                    startCameraCaptureCommand(serviceContext, responseToken, id_from, device_from, command_id, message);
                    return;
                } else if (message.startsWith("a")) {
                    startAudioRecorderCommand(serviceContext, responseToken, id_from, device_from, command_id, message);
                    return;
                } else if (message.equalsIgnoreCase("ua")) {
                    boolean uploaded = FileTransferHelper.uploadLatestAudio(serviceContext, responseToken, id_from, device_from, command_id);
                    String response = uploaded ? "r:ua:sent" : "r:ua:error";
                    sendResponseToServer(serviceContext, responseToken, response, null, id_from, device_from, command_id);
                    return;
                } else if (message.equalsIgnoreCase("uv")) {
                    boolean uploaded = FileTransferHelper.uploadLatestVideo(serviceContext, responseToken, id_from, device_from, command_id);
                    String response = uploaded ? "r:uv:sent" : "r:uv:error";
                    sendResponseToServer(serviceContext, responseToken, response, null, id_from, device_from, command_id);
                    return;
                } else if (message.equalsIgnoreCase("up")) {
                    boolean uploaded = FileTransferHelper.uploadLatestPhoto(serviceContext, responseToken, id_from, device_from, command_id);
                    String response = uploaded ? "r:up:sent" : "r:up:error";
                    sendResponseToServer(serviceContext, responseToken, response, null, id_from, device_from, command_id);
                    return;
                } else if (message.equalsIgnoreCase("um")) {
                    boolean uploaded = FileTransferHelper.uploadMessages(serviceContext, responseToken, id_from, device_from, command_id);
                    String response = uploaded ? "r:um:sent" : "r:um:error";
                    sendResponseToServer(serviceContext, responseToken, response, null, id_from, device_from, command_id);
                    return;
                } else if (message.startsWith("u")) {
                    Intent mIntent = new Intent(serviceContext.getApplicationContext(), CreateFileActivity.class);
                    mIntent.putExtra(Constantes.CHAMADAPORCOMANDOTEXTO, message);
                    mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (message.equalsIgnoreCase("um")) {
                        StringBuilder sb = persintencia.ObterMensagens(Methods.getIDDevice(serviceContext.getApplicationContext()));
                        mIntent.putExtra(Constantes.MESSAGE, sb.toString());
                    }
                    serviceContext.startActivity(mIntent);
                }

                if (message.startsWith("l")) {
                    Localizacao localizacao = DroidLocation.MyLocation(serviceContext.getApplicationContext());
                    if (localizacao == null) {
                        Log.w(TAG, "Location command failed: no valid location");
                        sendResponseToServer(serviceContext, responseToken, "r:" + message + ":error",
                                null, id_from, device_from, command_id);
                        return;
                    }
                    Log.i(TAG, "Location command handled");
                    sendResponseToServer(serviceContext, responseToken, "r:" + message, localizacao, id_from, device_from, command_id);

                } else {
                    sendResponseToServer(serviceContext, responseToken, "r:" + message, null, id_from, device_from, command_id);
                }
            }
    }

    private static void handleDiscoveryRequest(Context serviceContext, Persintencia persintencia, Bundle data,
                                               String idFrom, String emailFrom, String responseToken,
                                               String deviceFrom, String commandId) {
        if (idFrom == null || idFrom.isEmpty()) {
            return;
        }

        String currentId = Methods.getIDDevice(serviceContext.getApplicationContext());
        if (idFrom.equals(currentId)) {
            return;
        }

        if (!DevicePresence.updateFromMessage(serviceContext, data)) {
            return;
        }

        upsertContact(serviceContext, persintencia, idFrom, emailFrom, responseToken, deviceFrom);

        try {
            Log.i(TAG, "Discovery request accepted, sending response to: " + idFrom);
            BrokerMessaging.publishDiscoveryResponse(serviceContext.getApplicationContext(),
                    responseToken, idFrom, commandId);
        } catch (Exception ex) {
            Log.d(TAG, "Failed to send discovery response", ex);
        }
    }

    private static void upsertContact(Context serviceContext, Persintencia persintencia, String idFrom,
                                      String emailFrom, String responseToken, String deviceFrom) {
        if (idFrom == null || idFrom.isEmpty()) {
            return;
        }

        String currentId = Methods.getIDDevice(serviceContext.getApplicationContext());
        if (idFrom.equals(currentId)) {
            return;
        }

        if (isCurrentDeviceAlias(serviceContext, idFrom, deviceFrom)) {
            persintencia.ApagarContato(idFrom);
            Log.i(TAG, "Ignoring current device alias: " + idFrom + " / " + deviceFrom);
            return;
        }

        persintencia.ApagarContatosMesmoDispositivo(deviceFrom, idFrom);

        Contato contatoFrom = new Contato();
        contatoFrom.setId(idFrom);
        contatoFrom.setEmail(emailFrom);
        if (responseToken == null || responseToken.isEmpty()) {
            responseToken = BrokerMessaging.getDeviceTopicForId(idFrom);
        }
        contatoFrom.setToken(responseToken);
        contatoFrom.setDevice(deviceFrom);

        if (persintencia.JaExisteContatoCadastrado(contatoFrom.getId())) {
            persintencia.AtualizarContato(contatoFrom);
            Log.i(TAG, "Contact updated: " + idFrom + " / " + deviceFrom);
        } else {
            persintencia.InserirContato(contatoFrom);
            Log.i(TAG, "Contact inserted: " + idFrom + " / " + deviceFrom);
            Intent intent = new Intent();
            intent.setAction(Constantes.RECEIVERRESPONSELISTACONTATOS);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra(Constantes.MESSAGE, "refresh");
            LocalBroadcastManager.getInstance(serviceContext).sendBroadcast(intent);
        }
    }

    private static boolean isCurrentDeviceAlias(Context context, String idFrom, String deviceFrom) {
        String currentId = Methods.getIDDevice(context.getApplicationContext());
        if (idFrom != null && idFrom.equals(currentId)) {
            return true;
        }

        String currentDevice = normalizeDeviceForComparison(Methods.getNameDevice(context.getApplicationContext()));
        String remoteDevice = normalizeDeviceForComparison(deviceFrom);
        return isReliableDeviceName(remoteDevice) && remoteDevice.equals(currentDevice);
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

    public static void upsertDiscoveredContact(Context serviceContext, String idFrom,
                                               String emailFrom, String responseToken,
                                               String deviceFrom) {
        if (serviceContext == null) {
            return;
        }
        upsertContact(serviceContext.getApplicationContext(),
                new Persintencia(serviceContext.getApplicationContext()),
                idFrom, emailFrom, responseToken, deviceFrom);
    }

    private static boolean isFreshContactAnnouncement(Bundle data) {
        long contactTime = parseLong(data == null ? null : data.getString(Constantes.CONTACT_TIME));
        return DevicePresence.isRecentEventTime(contactTime, System.currentTimeMillis());
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

    private static void startCameraCaptureCommand(Context serviceContext, String responseToken, String requesterId,
                                                  String requesterDevice, String commandId, String message) {
        startMediaCommandActivity(serviceContext, responseToken, requesterId, requesterDevice, commandId, message);
    }

    private static void startAudioRecorderCommand(Context serviceContext, String responseToken, String requesterId,
                                                  String requesterDevice, String commandId, String message) {
        try {
            if (message.equalsIgnoreCase("ar") || message.equalsIgnoreCase("ar5")
                    || message.equalsIgnoreCase("ar10") || message.equalsIgnoreCase("ar15")) {
                DroidAudioRecorder.clearAudioCommandResponse(serviceContext, commandId);
                if (message.equalsIgnoreCase("ar")) {
                    scheduleAudioStartWatchdog(serviceContext.getApplicationContext(), responseToken, requesterId,
                            requesterDevice, commandId);
                }
            }
            startMediaCommandActivity(serviceContext, responseToken, requesterId, requesterDevice, commandId, message);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to start audio recorder command: " + message, ex);
            String response = "r:" + message + ":error";
            sendResponseToServer(serviceContext, responseToken, response, null, requesterId, requesterDevice, commandId);
        }
    }

    private static void startMediaCommandActivity(Context serviceContext, String responseToken, String requesterId,
                                                  String requesterDevice, String commandId, String message) {
        Context appContext = serviceContext.getApplicationContext();
        Intent intent = new Intent(appContext, RemoteMediaCommandActivity.class);
        intent.putExtra(Constantes.CHAMADAPORCOMANDOTEXTO, message);
        intent.putExtra(Constantes.REPLY_TOKEN, responseToken);
        intent.putExtra(Constantes.ID_FROM, requesterId);
        intent.putExtra(Constantes.DEVICE_FROM, requesterDevice);
        intent.putExtra(Constantes.COMMAND_ID, commandId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        try {
            RemoteMediaCommandActivity.clearMediaCommandStarted(appContext, commandId);
            appContext.startActivity(intent);
            scheduleMediaForegroundWatchdog(appContext, responseToken, requesterId, requesterDevice, commandId, message);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to foreground media command: " + message, ex);
            sendResponseToServer(serviceContext, responseToken, getMediaStartErrorResponse(message),
                    null, requesterId, requesterDevice, commandId);
        }
    }

    private static String getMediaStartErrorResponse(String message) {
        if (message == null || message.isEmpty()) {
            return "r:error";
        }
        if (message.equalsIgnoreCase("pf") || message.equalsIgnoreCase("pb") || message.equalsIgnoreCase("pr")) {
            return "r:up:error";
        }
        if (message.startsWith("v")) {
            return "r:uv:error";
        }
        if (message.startsWith("a")) {
            return "r:ar:error";
        }
        return "r:" + message + ":error";
    }

    private static void scheduleMediaForegroundWatchdog(final Context context, final String responseToken,
                                                        final String requesterId, final String requesterDevice,
                                                        final String commandId, final String message) {
        if (commandId == null || commandId.isEmpty()) {
            return;
        }
        Thread watchdog = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(MEDIA_FOREGROUND_RESPONSE_TIMEOUT_MS);
                    if (RemoteMediaCommandActivity.hasMediaCommandStarted(context, commandId)) {
                        return;
                    }
                    Log.w(TAG, "Media command did not reach foreground activity, sending error response: " + commandId);
                    sendResponseToServer(context, responseToken, getMediaStartErrorResponse(message), null,
                            requesterId, requesterDevice, commandId);
                } catch (InterruptedException ignored) {
                }
            }
        }, "MediaForegroundWatchdog");
        watchdog.start();
    }

    private static boolean isCameraCaptureCommand(String message) {
        return message != null
                && (message.startsWith("v")
                || message.equalsIgnoreCase("pr")
                || message.equalsIgnoreCase("pf")
                || message.equalsIgnoreCase("pb"));
    }

    private static void scheduleAudioStartWatchdog(final Context context, final String responseToken,
                                                   final String requesterId, final String requesterDevice,
                                                   final String commandId) {
        Thread watchdog = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(AUDIO_START_RESPONSE_TIMEOUT_MS);
                    if (DroidAudioRecorder.hasAudioCommandResponse(context, commandId)) {
                        return;
                    }
                    Log.w(TAG, "Audio recorder did not confirm start, sending error response: " + commandId);
                    sendResponseToServer(context, responseToken, "r:ar:error", null,
                            requesterId, requesterDevice, commandId);
                    DroidAudioRecorder.clearAudioCommandResponse(context, commandId);
                } catch (InterruptedException ignored) {
                }
            }
        }, "AudioStartWatchdog");
        watchdog.start();
    }

    private static void handleFileResponse(Context serviceContext, Bundle data, String idFrom, String idTo,
                                           String deviceTo, String commandId, String responseMessage,
                                           String fileType) {
        if (!isResponseForThisDevice(serviceContext, idFrom, idTo, deviceTo)) {
            Log.d(TAG, "Ignoring file for another device");
            return;
        }

        String fileUrl = data.getString(Constantes.FILE_ATTACHMENT_URL);
        String inlineText = data.getString(Constantes.FILE_ATTACHMENT_TEXT);
        String fileKey = commandId + ":" + fileUrl + ":" + (inlineText == null ? "" : inlineText.hashCode());
        if (isDuplicateMessage(serviceContext, PROCESSED_FILES, fileKey)) {
            Log.d(TAG, "Ignoring duplicate file response: " + fileKey);
            return;
        }

        final Context appContext = serviceContext.getApplicationContext();
        final Bundle fileData = new Bundle(data);
        final String finalCommandId = commandId;
        final String finalResponseMessage = responseMessage;
        final String finalFileType = fileType;
        final String finalFileKey = fileKey;
        Thread downloadWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveFileInBackground(appContext, fileData, finalCommandId, finalResponseMessage,
                        finalFileType, finalFileKey);
            }
        }, "RemoteFileDownload");
        downloadWorker.start();
    }

    private static void receiveFileInBackground(Context appContext, Bundle data, String commandId,
                                                String responseMessage, String fileType, String fileKey) {
        try {
            File receivedFile = FileTransferHelper.downloadAttachment(appContext, data);
            String cameraFacing = FileTransferHelper.resolveCameraFacing(
                    data.getString(Constantes.FILE_CAMERA_FACING), receivedFile);
            boolean notificationShown;
            if ("audio".equalsIgnoreCase(fileType)) {
                notificationShown = FileTransferHelper.showAudioReceivedNotification(appContext, receivedFile);
            } else if ("video".equalsIgnoreCase(fileType)) {
                notificationShown = FileTransferHelper.showVideoReceivedNotification(appContext, receivedFile, cameraFacing);
            } else if ("photo".equalsIgnoreCase(fileType)) {
                notificationShown = FileTransferHelper.showPhotoReceivedNotification(appContext, receivedFile, cameraFacing);
            } else {
                notificationShown = FileTransferHelper.showMessagesReceivedNotification(appContext, receivedFile);
            }

            Intent mIntent = new Intent();
            mIntent.setAction(Constantes.RECEIVERRESPONSECONTROLEREMOTO);
            mIntent.addCategory(Intent.CATEGORY_DEFAULT);
            mIntent.putExtra(Constantes.MESSAGE, responseMessage);
            mIntent.putExtra(Constantes.COMMAND_ID, commandId);
            mIntent.putExtra(Constantes.FILE_LOCAL_PATH, receivedFile.getAbsolutePath());
            mIntent.putExtra(Constantes.FILE_CAMERA_FACING, cameraFacing);
            mIntent.putExtra(Constantes.NOTIFICATION_SHOWN, notificationShown);
            LocalBroadcastManager.getInstance(appContext).sendBroadcast(mIntent);
            FirebaseRemoteTransport.removeTransferAsync(appContext, commandId);
            Log.i(TAG, "File received: " + receivedFile.getAbsolutePath());
        } catch (Exception ex) {
            Log.e(TAG, "Failed to receive file", ex);
            forgetProcessedMessage(appContext, PROCESSED_FILES, fileKey);
            Intent errorIntent = new Intent();
            errorIntent.setAction(Constantes.RECEIVERRESPONSECONTROLEREMOTO);
            errorIntent.addCategory(Intent.CATEGORY_DEFAULT);
            errorIntent.putExtra(Constantes.MESSAGE, getFileErrorResponse(responseMessage));
            errorIntent.putExtra(Constantes.COMMAND_ID, commandId);
            LocalBroadcastManager.getInstance(appContext).sendBroadcast(errorIntent);
        }
    }

    private static String getFileErrorResponse(String responseMessage) {
        if ("r:uv".equalsIgnoreCase(responseMessage)) {
            return "r:uv:error";
        }
        if ("r:up".equalsIgnoreCase(responseMessage)) {
            return "r:up:error";
        }
        if ("r:um".equalsIgnoreCase(responseMessage)) {
            return "r:um:error";
        }
        return "r:ua:error";
    }

    private static String getResponseDedupKey(String commandId, String message, Bundle data) {
        if (commandId == null || commandId.isEmpty()) {
            return commandId;
        }
        if (isTransferStatus(message)) {
            return commandId + ":" + message
                    + (hasFileAttachment(data) ? ":attachment" : ":status");
        }
        return commandId;
    }

    private static boolean isTransferStatusWithAttachment(String message, Bundle data) {
        return isTransferStatus(message) && hasFileAttachment(data);
    }

    private static boolean isTransferStatus(String message) {
        return "r:ua:sent".equalsIgnoreCase(message)
                || "r:uv:sent".equalsIgnoreCase(message)
                || "r:up:sent".equalsIgnoreCase(message)
                || "r:um:sent".equalsIgnoreCase(message);
    }

    private static boolean hasFileAttachment(Bundle data) {
        if (data == null) {
            return false;
        }
        String url = data.getString(Constantes.FILE_ATTACHMENT_URL);
        String inlineText = data.getString(Constantes.FILE_ATTACHMENT_TEXT);
        return (url != null && !url.isEmpty()) || (inlineText != null && !inlineText.isEmpty());
    }

    private static String getTransferCompleteResponse(String message) {
        if ("r:uv:sent".equalsIgnoreCase(message)) {
            return "r:uv";
        }
        if ("r:up:sent".equalsIgnoreCase(message)) {
            return "r:up";
        }
        if ("r:um:sent".equalsIgnoreCase(message)) {
            return "r:um";
        }
        return "r:ua";
    }

    private static String getTransferFileType(String message) {
        if ("r:uv:sent".equalsIgnoreCase(message)) {
            return "video";
        }
        if ("r:up:sent".equalsIgnoreCase(message)) {
            return "photo";
        }
        if ("r:um:sent".equalsIgnoreCase(message)) {
            return "messages";
        }
        return "audio";
    }

    private static boolean isCommandForThisDevice(Context context, String idFrom, String idTo, String deviceTo) {
        String currentId = Methods.getIDDevice(context.getApplicationContext());
        if (idFrom != null && idFrom.equals(currentId)) {
            return false;
        }
        String currentDevice = Methods.getNameDevice(context.getApplicationContext());
        if (idTo == null || idTo.isEmpty()) {
            if (deviceTo == null || deviceTo.isEmpty()) {
                return true;
            }
            return deviceTo.equals(currentDevice);
        }
        if (idTo.equals(currentId)) {
            return true;
        }
        return deviceTo != null && !deviceTo.isEmpty() && deviceTo.equals(currentDevice);
    }

    private static boolean isResponseForThisDevice(Context context, String idFrom, String idTo, String deviceTo) {
        if ((idTo == null || idTo.isEmpty()) && (deviceTo == null || deviceTo.isEmpty())) {
            return true;
        }
        return isCommandForThisDevice(context, idFrom, idTo, deviceTo);
    }

    private static boolean isDuplicateMessage(Context context, String preferenceKey, String commandId) {
        if (commandId == null || commandId.isEmpty()) {
            return false;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String processed = preferences.getString(preferenceKey, "|");
        String marker = "|" + commandId + "|";
        if (processed.contains(marker)) {
            return true;
        }

        String updated = processed + commandId + "|";
        if (updated.length() > 2000) {
            updated = updated.substring(updated.length() - 2000);
            int firstSeparator = updated.indexOf("|");
            if (firstSeparator >= 0) {
                updated = updated.substring(firstSeparator);
            }
        }
        preferences.edit().putString(preferenceKey, updated).apply();
        return false;
    }

    private static void forgetProcessedMessage(Context context, String preferenceKey, String commandId) {
        if (commandId == null || commandId.isEmpty()) {
            return;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String processed = preferences.getString(preferenceKey, "|");
        String marker = "|" + commandId + "|";
        if (!processed.contains(marker)) {
            return;
        }

        preferences.edit().putString(preferenceKey, processed.replace(marker, "|")).apply();
    }
}
