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
import com.google.android.gms.gcm.GcmPubSub;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class RegistrationIntentService extends IntentService {

    private static final String TAG = "RegIntentService";
    private static final String[] TOPICS = {"global"};

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
                InstanceID instanceID = InstanceID.getInstance(this);
                token = instanceID.getToken(Constantes.SENDER_ID,
                        GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                Log.i(TAG, "GCM Registration Token: " + token);

                contato_from.setId(id_from);
                contato_from.setEmail(email);
                contato_from.setToken(token);
                contato_from.setDevice(device);

                sendRegistrationToServer(contato_from, "", message);
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
                token = contato_to.getToken();
                sendRegistrationToServer(contato_from, token, message);
            }

            subscribeTopics(token);
            sharedPreferences.edit().putBoolean(Constantes.SENT_TOKEN_TO_SERVER, true).apply();

        } catch (Exception e) {
            Log.d(TAG, "Failed to complete token refresh", e);
            sharedPreferences.edit().putBoolean(Constantes.SENT_TOKEN_TO_SERVER, false).apply();
        }
        Intent registrationComplete = new Intent(Constantes.REGISTRATION_COMPLETE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(registrationComplete);
    }

    private void sendRegistrationToServer(Contato contato_from, String token_to, String message) throws Exception {
        // Prepare JSON containing the GCM message content. What to send and where to send.
        JSONObject jGcmData = new JSONObject();
        JSONObject jData = new JSONObject();

        jData.put(Constantes.ID_FROM, contato_from.getId());
        jData.put(Constantes.EMAIL_FROM, contato_from.getEmail());
        jData.put(Constantes.TOKEN_FROM, contato_from.getToken());
        jData.put(Constantes.DEVICE_FROM, contato_from.getDevice());

        if (message != null && !message.isEmpty()) {
            jData.put("message", message);
        }

        if (token_to == null || token_to.isEmpty()) {
            jGcmData.put("to", "/topics/global"); // para todos que tem o aplicativo
        } else jGcmData.put("to", token_to); // para um aparelho especifico

        // What to send in GCM message.
        jGcmData.put("data", jData);

        // Create connection to send GCM Message request.
        URL url = new URL("https://android.googleapis.com/gcm/send");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "key=" + Constantes.API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        // Send GCM message content.
        OutputStream outputStream = conn.getOutputStream();
        outputStream.write(jGcmData.toString().getBytes());

        // Read GCM response.
        InputStream inputStream = conn.getInputStream();
        String resp = IOUtils.toString(inputStream);
        System.out.println(resp);
        System.out.println("Check your device/emulator for notification or logcat for " +
                "confirmation of the receipt of the GCM message.");

    }

    /**
     * Subscribe to any GCM topics of interest, as defined by the TOPICS constant.
     *
     * @param token GCM token
     * @throws IOException if unable to reach the GCM PubSub service
     */
    // [START subscribe_topics]
    private void subscribeTopics(String token) throws IOException {
        GcmPubSub pubSub = GcmPubSub.getInstance(this);
        for (String topic : TOPICS) {
            pubSub.subscribe(token, "/topics/" + topic, null);
        }
    }
    // [END subscribe_topics]

}
