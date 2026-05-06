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
import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.feature.Localizacao;
import com.droid.remoteaccess.gdrive.CreateFileActivity;
import com.droid.remoteaccess.location.DroidLocation;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.recorder.DroidAudioRecorder;
import com.droid.remoteaccess.recorder.DroidCameraCaptureService;

import java.io.File;

import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public final class BrokerMessageHandler {

    private static final String TAG = "BrokerMessageHandler";
    private static final String PROCESSED_COMMANDS = "processed_command_ids";
    private static final String PROCESSED_RESPONSES = "processed_response_ids";
    private static final String PROCESSED_FILES = "processed_file_ids";
    private static final long AUDIO_START_RESPONSE_TIMEOUT_MS = 9000;


    //private String ChamadaBroadCastPorComandoTexto() {
    //return Methods.chamadaBroadCastPorComandoTexto(getIntent());
    //}

    private BrokerMessageHandler() {
    }


    private static void sendResponseToServer(Context context, String token_to, String message, Localizacao localizacao,
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

            BrokerMessaging.publishResponse(token_to, data);
            Log.i(TAG, "Response sent: " + message);
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
        Contato contato_from = new Contato();
        String responseToken = reply_token;
        if (responseToken == null || responseToken.isEmpty()) {
            responseToken = token_from;
        }
        if ((responseToken == null || responseToken.isEmpty()) && id_from != null && !id_from.isEmpty()) {
            responseToken = BrokerMessaging.getDeviceTopicForId(id_from);
        }
        if (id_from != null && !id_from.isEmpty()) {
            String currentId = Methods.getIDDevice(serviceContext.getApplicationContext());
            if (!id_from.equals(currentId)) {
                persintencia.ApagarContatosMesmoDispositivo(device_from, id_from);
            }

            contato_from.setId(id_from);
            contato_from.setEmail(email_from);
            contato_from.setToken(responseToken);
            contato_from.setDevice(device_from);

            if (persintencia.JaExisteContatoCadastrado(contato_from.getId())) {
                persintencia.AtualizarContato(contato_from);
            } else {
                persintencia.InserirContato(contato_from);
                Intent mIntent = new Intent();
                mIntent.setAction(Constantes.RECEIVERRESPONSELISTACONTATOS);
                mIntent.addCategory(Intent.CATEGORY_DEFAULT);
                mIntent.putExtra(Constantes.MESSAGE, "refresh");
                LocalBroadcastManager.getInstance(serviceContext).sendBroadcast(mIntent);
            }
        }

        if (message != null) {
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
                if (isDuplicateMessage(serviceContext, PROCESSED_RESPONSES, command_id)) {
                    Log.d(TAG, "Ignoring duplicate response: " + command_id);
                    return;
                }
                Log.i(TAG, "Response accepted: " + message);

                Intent mIntent = new Intent();
                mIntent.setAction(Constantes.RECEIVERRESPONSECONTROLEREMOTO);
                mIntent.addCategory(Intent.CATEGORY_DEFAULT);
                mIntent.putExtra(Constantes.MESSAGE, message);

                if (message.equalsIgnoreCase("r:l"))
                {
                    mIntent.putExtra(Constantes.LATITUDE, data.getString(Constantes.LATITUDE));
                    mIntent.putExtra(Constantes.LONGITUDE, data.getString(Constantes.LONGITUDE));
                }
                //
                LocalBroadcastManager.getInstance(serviceContext).sendBroadcast(mIntent);
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
                    Log.i(TAG, "Location command handled");
                    sendResponseToServer(serviceContext, responseToken, "r:" + message, localizacao, id_from, device_from, command_id);

                } else {
                    sendResponseToServer(serviceContext, responseToken, "r:" + message, null, id_from, device_from, command_id);
                }
            }
        }
    }

    private static void startCameraCaptureCommand(Context serviceContext, String responseToken, String requesterId,
                                                  String requesterDevice, String commandId, String message) {
        Intent intentService = new Intent(serviceContext.getApplicationContext(), DroidCameraCaptureService.class);
        intentService.putExtra(Constantes.CHAMADAPORCOMANDOTEXTO, message);
        intentService.putExtra(Constantes.REPLY_TOKEN, responseToken);
        intentService.putExtra(Constantes.ID_FROM, requesterId);
        intentService.putExtra(Constantes.DEVICE_FROM, requesterDevice);
        intentService.putExtra(Constantes.COMMAND_ID, commandId);

        try {
            ContextCompat.startForegroundService(serviceContext.getApplicationContext(), intentService);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to start camera command: " + message, ex);
            String response = "r:" + message + ":error";
            sendResponseToServer(serviceContext, responseToken, response, null, requesterId, requesterDevice, commandId);
        }
    }

    private static void startAudioRecorderCommand(Context serviceContext, String responseToken, String requesterId,
                                                  String requesterDevice, String commandId, String message) {
        Intent intentService = new Intent(serviceContext.getApplicationContext(), DroidAudioRecorder.class);
        intentService.putExtra(Constantes.CHAMADAPORCOMANDOTEXTO, message);
        intentService.putExtra(Constantes.REPLY_TOKEN, responseToken);
        intentService.putExtra(Constantes.ID_FROM, requesterId);
        intentService.putExtra(Constantes.DEVICE_FROM, requesterDevice);
        intentService.putExtra(Constantes.COMMAND_ID, commandId);

        try {
            if (message.equalsIgnoreCase("ar") || message.equalsIgnoreCase("ar5")
                    || message.equalsIgnoreCase("ar10") || message.equalsIgnoreCase("ar15")) {
                DroidAudioRecorder.clearAudioCommandResponse(serviceContext, commandId);
                ContextCompat.startForegroundService(serviceContext.getApplicationContext(), intentService);
                if (message.equalsIgnoreCase("ar")) {
                    scheduleAudioStartWatchdog(serviceContext.getApplicationContext(), responseToken, requesterId,
                            requesterDevice, commandId);
                }
            } else {
                serviceContext.startService(intentService);
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to start audio recorder command: " + message, ex);
            String response = "r:" + message + ":error";
            sendResponseToServer(serviceContext, responseToken, response, null, requesterId, requesterDevice, commandId);
        }
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
        String fileKey = commandId + ":" + fileUrl;
        if (isDuplicateMessage(serviceContext, PROCESSED_FILES, fileKey)) {
            Log.d(TAG, "Ignoring duplicate file response: " + fileKey);
            return;
        }

        try {
            File receivedFile = FileTransferHelper.downloadAttachment(serviceContext.getApplicationContext(), data);
            boolean notificationShown;
            if ("audio".equalsIgnoreCase(fileType)) {
                notificationShown = FileTransferHelper.showAudioReceivedNotification(serviceContext.getApplicationContext(), receivedFile);
            } else if ("video".equalsIgnoreCase(fileType)) {
                notificationShown = FileTransferHelper.showVideoReceivedNotification(serviceContext.getApplicationContext(), receivedFile);
            } else if ("photo".equalsIgnoreCase(fileType)) {
                notificationShown = FileTransferHelper.showPhotoReceivedNotification(serviceContext.getApplicationContext(), receivedFile);
            } else {
                notificationShown = FileTransferHelper.showMessagesReceivedNotification(serviceContext.getApplicationContext(), receivedFile);
            }

            Intent mIntent = new Intent();
            mIntent.setAction(Constantes.RECEIVERRESPONSECONTROLEREMOTO);
            mIntent.addCategory(Intent.CATEGORY_DEFAULT);
            mIntent.putExtra(Constantes.MESSAGE, responseMessage);
            mIntent.putExtra(Constantes.FILE_LOCAL_PATH, receivedFile.getAbsolutePath());
            mIntent.putExtra(Constantes.NOTIFICATION_SHOWN, notificationShown);
            LocalBroadcastManager.getInstance(serviceContext).sendBroadcast(mIntent);
            Log.i(TAG, "File received: " + receivedFile.getAbsolutePath());
        } catch (Exception ex) {
            Log.e(TAG, "Failed to receive file", ex);
        }
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
}
