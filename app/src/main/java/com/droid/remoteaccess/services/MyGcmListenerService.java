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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.feature.Contato;
import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.R;
import com.droid.remoteaccess.activitys.DroidListaContatos;
import com.droid.remoteaccess.feature.Localizacao;
import com.droid.remoteaccess.gdrive.CreateFileActivity;
import com.droid.remoteaccess.location.DroidLocation;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.recorder.DroidAudioRecorder;
import com.droid.remoteaccess.recorder.DroidHeadService;
import com.google.android.gms.gcm.GcmListenerService;

import androidx.core.app.NotificationCompat;

public class MyGcmListenerService extends GcmListenerService {


    //private String ChamadaBroadCastPorComandoTexto() {
    //return Methods.chamadaBroadCastPorComandoTexto(getIntent());
    //}


    private static void sendResponseToServer(Context context, String token_to, String message, Localizacao localizacao) {

        try {
            Bundle data = new Bundle();

            if (message != null && !message.isEmpty()) {
                data.putString(Constantes.MESSAGE, message);
            }

            if (localizacao != null)
            {
                data.putString(Constantes.LATITUDE, String.valueOf(localizacao.getLatitude()));
                data.putString(Constantes.LONGITUDE, String.valueOf(localizacao.getLongitude()));
            }

            BrokerMessaging.publishToToken(token_to, data);
        } catch (Exception ex) {

        }

    }


    private static final String TAG = "MyGcmListenerService";
    private static final String CHANNEL_ID = "remote_access_messages";

    /**
     * Called when message is received.
     *
     * @param from SenderID of the sender.
     * @param data Data bundle containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(String from, Bundle data) {
        handleMessage(this, from, data);
    }

    public static void handleMessage(Context serviceContext, String from, Bundle data) {
        String id_from = data.getString(Constantes.ID_FROM);
        String email_from = data.getString(Constantes.EMAIL_FROM);
        String token_from = data.getString(Constantes.TOKEN_FROM);
        String device_from = data.getString(Constantes.DEVICE_FROM);
        String message = data.getString(Constantes.MESSAGE);

        //sendNotification(message);

        Persintencia persintencia = new Persintencia(serviceContext.getApplicationContext());
        Contato contato_from = new Contato();
        String responseToken = token_from;
        if ((responseToken == null || responseToken.isEmpty()) && id_from != null && !id_from.isEmpty()) {
            responseToken = BrokerMessaging.getDeviceTopicForId(id_from);
        }
        if (id_from != null && !id_from.isEmpty()) {
            contato_from.setId(id_from);
            contato_from.setEmail(email_from);
            contato_from.setToken(token_from);
            contato_from.setDevice(device_from);
            responseToken = token_from;

            if (persintencia.JaExisteContatoCadastrado(contato_from.getId())) {
                persintencia.AtualizarContato(contato_from);
            } else {
                persintencia.InserirContato(contato_from);
                Intent mIntent = new Intent();
                mIntent.setAction(Constantes.RECEIVERRESPONSELISTACONTATOS);
                mIntent.addCategory(Intent.CATEGORY_DEFAULT);
                mIntent.putExtra(Constantes.MESSAGE, "refresh");
                serviceContext.sendBroadcast(mIntent);
            }
        }

        if (message != null) {
            if (message.startsWith("r:")) {
                //sendNotification(message);

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
                serviceContext.sendBroadcast(mIntent);
            } else {

                if (from.startsWith("/topics/")) {
                    // message received from some topic.
                } else {
                    // normal downstream message.
                }

                Intent intentService;

                if (message.startsWith("v")) {
                    intentService = new Intent(serviceContext.getApplicationContext(), DroidHeadService.class);
                    intentService.putExtra(Constantes.CHAMADAPORCOMANDOTEXTO, message);
                    serviceContext.startService(intentService);
                } else if (message.startsWith("a")) {
                    intentService = new Intent(serviceContext.getApplicationContext(), DroidAudioRecorder.class);
                    intentService.putExtra(Constantes.CHAMADAPORCOMANDOTEXTO, message);
                    serviceContext.startService(intentService);
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
                    sendResponseToServer(serviceContext, responseToken, "r:" + message, localizacao);

                } else {
                    sendResponseToServer(serviceContext, responseToken, "r:" + message, null);
                }
            }
            // [END_EXCLUDE]
        }
    }
    // [END receive_message]

    /**
     * Create and show a simple notification containing the received GCM message.
     *
     * @param message GCM message received.
     */



    private void sendNotification(String message) {
        Intent intent = new Intent(this, DroidListaContatos.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("GCM Message")
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }


}
