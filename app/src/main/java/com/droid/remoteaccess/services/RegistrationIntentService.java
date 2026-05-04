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
            Contato contato_to = new Contato();

            String id_from = intent.getStringExtra(Constantes.ID_FROM);
            String id_to = intent.getStringExtra(Constantes.ID_TO);
            String message = intent.getStringExtra("message");

            String token = "";

            if (id_from == null || id_from.isEmpty())
            {
                id_from = Methods.getIDDevice(context);
                String email = Methods.getEmail(context);
                String device = Methods.getNameDevice(context);
                token = BrokerMessaging.getDeviceTopic(context);
                Log.i(TAG, "Broker topic: " + token);

                contato_from.setId(id_from);
                contato_from.setEmail(email);
                contato_from.setToken(token);
                contato_from.setDevice(device);

                BrokerMessaging.publishContact(context, contato_from.getId(), contato_from.getEmail(), contato_from.getToken(), contato_from.getDevice());
                if (persintencia.JaExisteContatoCadastrado(contato_from.getId())) {
                    persintencia.AtualizarContato(contato_from);
                }
                else {
                    persintencia.InserirContato(contato_from);
                }
            }
            else
            {
                contato_from = persintencia.ObterContato(id_from);
                contato_to = persintencia.ObterContato(id_to);
                token = BrokerMessaging.getDeviceTopicForId(id_to);
                android.os.Bundle data = new android.os.Bundle();
                data.putString(Constantes.ID_FROM, contato_from.getId());
                data.putString(Constantes.EMAIL_FROM, contato_from.getEmail());
                data.putString(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopicForId(id_from));
                data.putString(Constantes.DEVICE_FROM, contato_from.getDevice());
                data.putString(Constantes.MESSAGE, message);
                BrokerMessaging.publishToToken(token, data);
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
