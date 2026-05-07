/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.droid.remoteaccess.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.feature.Contato;
import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.others.Methods;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class RegistrationIntentService extends IntentService {

    private static final String TAG = "RegIntentService";

    public RegistrationIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            Context context = getBaseContext();
            Persintencia persintencia = new Persintencia(getBaseContext());
            Contato contato_from = new Contato();

            String id_from = intent.getStringExtra(Constantes.ID_FROM);
            String id_to = intent.getStringExtra(Constantes.ID_TO);
            String message = intent.getStringExtra("message");
            String commandId = intent.getStringExtra(Constantes.COMMAND_ID);

            String token = "";

            if (id_from == null || id_from.isEmpty())
            {
                id_from = Methods.getIDDevice(context);
                String email = Methods.getEmail(context);
                String device = Methods.getNameDevice(context);
                token = BrokerMessaging.getDeviceTopic(context);
                Log.i(TAG, "Broker topic ready");

                contato_from.setId(id_from);
                contato_from.setEmail(email);
                contato_from.setToken(token);
                contato_from.setDevice(device);

                if (persintencia.JaExisteContatoCadastrado(contato_from.getId())) {
                    persintencia.AtualizarContato(contato_from);
                }
                else {
                    persintencia.InserirContato(contato_from);
                }

                try {
                    FirebaseRemoteTransport.registerDeviceAsync(context);
                    LocalDiscovery.sendAnnounceAsync(context);
                    BrokerMessaging.publishContact(context, id_from, email, token, device);
                    Log.i(TAG, "Contact announcement sent");
                } catch (Exception ex) {
                    Log.d(TAG, "Contact announcement failed; discovery will retry later", ex);
                }
            }
            else
            {
                if (id_to == null || id_to.isEmpty()) {
                    throw new IllegalArgumentException("Destino não informado");
                }

                String senderId = id_from;
                if (senderId == null || senderId.isEmpty()) {
                    senderId = Methods.getIDDevice(context);
                }

                Contato contatoTo = persintencia.ObterContato(id_to);
                String deviceTo = "";
                if (contatoTo != null && contatoTo.getDevice() != null) {
                    deviceTo = contatoTo.getDevice();
                }

                token = BrokerMessaging.getDeviceTopicForId(id_to);
                String replyToken = BrokerMessaging.getDeviceTopicForId(senderId);
                android.os.Bundle data = new android.os.Bundle();
                data.putString(Constantes.ID_FROM, senderId);
                data.putString(Constantes.ID_TO, id_to);
                data.putString(Constantes.EMAIL_FROM, Methods.getEmail(context));
                data.putString(Constantes.TOKEN_FROM, replyToken);
                data.putString(Constantes.REPLY_TOKEN, replyToken);
                data.putString(Constantes.DEVICE_FROM, Methods.getNameDevice(context));
                data.putString(Constantes.DEVICE_TO, deviceTo);
                if (commandId == null || commandId.isEmpty()) {
                    commandId = senderId + "_" + System.currentTimeMillis();
                }
                data.putString(Constantes.COMMAND_ID, commandId);
                data.putString(Constantes.MESSAGE, message);
                boolean firebaseSent = FirebaseRemoteTransport.sendMessageToDevice(context, id_to, data);
                boolean localSent = LocalDiscovery.sendMessageToDevice(context, id_to, data);
                try {
                    BrokerMessaging.publishCommand(token, data);
                    Log.i(TAG, "Command sent: " + message);
                } catch (Exception ex) {
                    if (!firebaseSent && !localSent) {
                        throw ex;
                    }
                    Log.d(TAG, "Command sent by Firebase/local; broker failed: " + message, ex);
                }
            }

            sharedPreferences.edit().putBoolean(Constantes.SENT_TOKEN_TO_SERVER, true).apply();

        } catch (Exception e) {
            Log.d(TAG, "Failed to complete token refresh", e);
            sharedPreferences.edit().putBoolean(Constantes.SENT_TOKEN_TO_SERVER, false).apply();
        }
        Intent registrationComplete = new Intent(Constantes.REGISTRATION_COMPLETE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(registrationComplete);
    }

}
