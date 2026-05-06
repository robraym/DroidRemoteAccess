package com.droid.remoteaccess.activitys;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.feature.Contato;
import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.R;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.services.BrokerSyncService;
import com.droid.remoteaccess.services.FileTransferHelper;
import com.droid.remoteaccess.services.RegistrationIntentService;

import java.io.File;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Created by Robson on 06/03/2016.
 */
public class DroidControleRemoto extends AppCompatActivity {

    private static final long RESPONSE_TIMEOUT_MS = 30000;
    private static final long MEDIA_RESPONSE_TIMEOUT_MS = 75000;

    private Context context;
    private TextView tv_controlando;
    private TextView tv_status;
    private TextView tv_latitude;
    private TextView tv_longitude;
    private Button btn_gravar_video_frontal;
    private Button btn_gravar_video_traseiro;
    private Button btn_tirar_foto_frontal;
    private Button btn_tirar_foto_traseira;
    private Button btn_gravar_audio_5;
    private Button btn_gravar_audio_10;
    private Button btn_gravar_audio_15;
    private Button btn_mensagens;
    private Button btn_localizacao;

    private Persintencia persintencia;
    private Contato contato;
    private String token;
    private String idFrom;
    private String idTo;
    private ReceiverResponseControleRemoto receiver;
    private Handler responseTimeoutHandler;
    private Runnable responseTimeoutRunnable;
    private Button pendingButton;
    private CharSequence pendingButtonOriginalText;

    public DroidControleRemoto() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.telacontroleremoto);

        context = getBaseContext();
        Methods.AskNotificationPermission(this, getApplicationContext());
        tv_controlando = (TextView) findViewById(R.id.telacontroleremoto_tv_controlando);
        tv_status = (TextView) findViewById(R.id.telacontroleremoto_tv_status);
        tv_latitude = (TextView) findViewById(R.id.telacontroleremoto_tv_latitude);
        tv_longitude = (TextView) findViewById(R.id.telacontroleremoto_tv_Longitude);
        idFrom = getIntent().getStringExtra(Constantes.ID_FROM);
        idTo = getIntent().getStringExtra(Constantes.ID_TO);
        responseTimeoutHandler = new Handler(Looper.getMainLooper());

        persintencia = new Persintencia(getBaseContext());
        Contato contato = persintencia.ObterContato(idTo);
        if (contato != null) {
            token = contato.getToken();
            String deviceName = contato.getDevice();
            if (deviceName == null || deviceName.isEmpty()) {
                deviceName = contato.getEmail();
            }
            tv_controlando.setText(deviceName);
        } else {
            token = "";
            tv_controlando.setText(R.string.remote_unknown_device);
        }
        ContextCompat.startForegroundService(this, new Intent(this, BrokerSyncService.class));

        btn_gravar_video_frontal = (Button) findViewById(R.id.btn_gravar_video_frontal);
        btn_gravar_video_frontal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("vf5", btn_gravar_video_frontal);
            }
        });

        btn_gravar_video_traseiro = (Button) findViewById(R.id.btn_gravar_video_traseiro);
        btn_gravar_video_traseiro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("vb5", btn_gravar_video_traseiro);
            }
        });

        btn_tirar_foto_frontal = (Button) findViewById(R.id.btn_tirar_foto_frontal);
        btn_tirar_foto_frontal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("pf", btn_tirar_foto_frontal);
            }
        });

        btn_tirar_foto_traseira = (Button) findViewById(R.id.btn_tirar_foto_traseira);
        btn_tirar_foto_traseira.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("pb", btn_tirar_foto_traseira);
            }
        });

        btn_gravar_audio_5 = (Button) findViewById(R.id.btn_gravar_audio_5);
        btn_gravar_audio_5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("ar5", btn_gravar_audio_5);
            }
        });

        btn_gravar_audio_10 = (Button) findViewById(R.id.btn_gravar_audio_10);
        btn_gravar_audio_10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("ar10", btn_gravar_audio_10);
            }
        });

        btn_gravar_audio_15 = (Button) findViewById(R.id.btn_gravar_audio_15);
        btn_gravar_audio_15.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("ar15", btn_gravar_audio_15);
            }
        });

        btn_mensagens = (Button) findViewById(R.id.btn_mensagens);
        btn_mensagens.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("um", btn_mensagens);
            }
        });

        btn_localizacao = (Button) findViewById(R.id.btn_localizacao);
        btn_localizacao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnviarMensagem("l", btn_localizacao);
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Constantes.RECEIVERRESPONSECONTROLEREMOTO);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        //
        receiver = new ReceiverResponseControleRemoto();
        //
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);

    }

    @Override
    protected void onDestroy() {
        cancelResponseTimeout();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onDestroy();
    }

    private void EnabledButton (String message)
    {
        Button button = getButtonForResponse(message);
        restoreButton(button);
        updateStatusForResponse(message);

    }

    private void EnviarMensagem(String message, Button btn)
    {
        if (pendingButton != null) {
            Toast.makeText(DroidControleRemoto.this, R.string.remote_command_wait_current, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(DroidControleRemoto.this, RegistrationIntentService.class);
            intent.putExtra(Constantes.ID_FROM, idFrom);
            intent.putExtra(Constantes.ID_TO, idTo);
            intent.putExtra(Constantes.MESSAGE, message);
            startService(intent);
            markButtonWaiting(message, btn);
            scheduleResponseTimeout(message, btn);
        } catch (Exception e) {
            restoreButton(btn);
            e.printStackTrace();
        }

    }

    private void markButtonWaiting(String message, Button btn) {
        pendingButton = btn;
        pendingButtonOriginalText = btn.getText();
        btn.setSelected(true);
        btn.setEnabled(false);
        btn.setText(getWaitingButtonText(message));
        setStatus(R.string.remote_status_waiting);
    }

    private String getWaitingButtonText(String message) {
        if (message.equalsIgnoreCase("vf5") || message.equalsIgnoreCase("vb5") || message.equalsIgnoreCase("vr5")) {
            return getString(R.string.remote_button_recording_5s);
        }
        if (message.equalsIgnoreCase("ar5")) {
            return getString(R.string.remote_button_recording_audio_5s);
        }
        if (message.equalsIgnoreCase("ar10")) {
            return getString(R.string.remote_button_recording_audio_10s);
        }
        if (message.equalsIgnoreCase("ar15")) {
            return getString(R.string.remote_button_recording_audio_15s);
        }
        if (message.equalsIgnoreCase("ar") || message.equalsIgnoreCase("vr")) {
            return getString(R.string.remote_button_recording);
        }
        if (message.equalsIgnoreCase("pf") || message.equalsIgnoreCase("pb") || message.equalsIgnoreCase("pr")) {
            return getString(R.string.remote_button_taking_photo);
        }
        if (message.equalsIgnoreCase("as") || message.equalsIgnoreCase("vs")) {
            return getString(R.string.remote_button_stopping);
        }
        if (message.equalsIgnoreCase("ua") || message.equalsIgnoreCase("uv")
                || message.equalsIgnoreCase("up") || message.equalsIgnoreCase("um")) {
            return getString(R.string.remote_button_sending);
        }
        if (message.equalsIgnoreCase("l")) {
            return getString(R.string.remote_button_locating);
        }
        return getString(R.string.remote_button_waiting);
    }

    private Button getButtonForResponse(String message) {
        if (message.startsWith("r:ua")) {
            if (isPendingAudioButton()) {
                return pendingButton;
            }
            return null;
        } else if (message.startsWith("r:ar")) {
            if (isPendingAudioButton()) {
                return pendingButton;
            }
            return btn_gravar_audio_5;
        } else if (message.startsWith("r:uv")) {
            if (isPendingVideoButton()) {
                return pendingButton;
            }
            return null;
        } else if (message.startsWith("r:vr")) {
            if (isPendingVideoButton()) {
                return pendingButton;
            }
            return btn_gravar_video_frontal;
        } else if (message.startsWith("r:up")) {
            if (isPendingPhotoButton()) {
                return pendingButton;
            }
            return null;
        } else if (message.startsWith("r:pr")) {
            if (isPendingPhotoButton()) {
                return pendingButton;
            }
            return btn_tirar_foto_frontal;
        } else if (message.startsWith("r:um")) {
            return btn_mensagens;
        } else if (message.startsWith("r:l")) {
            return btn_localizacao;
        }
        return null;
    }

    private boolean isPendingVideoButton() {
        return pendingButton == btn_gravar_video_frontal || pendingButton == btn_gravar_video_traseiro;
    }

    private boolean isPendingPhotoButton() {
        return pendingButton == btn_tirar_foto_frontal || pendingButton == btn_tirar_foto_traseira;
    }

    private boolean isPendingAudioButton() {
        return pendingButton == btn_gravar_audio_5
                || pendingButton == btn_gravar_audio_10
                || pendingButton == btn_gravar_audio_15;
    }

    private void restoreButton(Button btn) {
        if (btn == null) {
            return;
        }
        btn.setEnabled(true);
        btn.setSelected(false);
        if (pendingButton == btn && pendingButtonOriginalText != null) {
            btn.setText(pendingButtonOriginalText);
            pendingButton = null;
            pendingButtonOriginalText = null;
        }
    }

    private void setStatus(int stringResId) {
        if (tv_status != null) {
            tv_status.setText(stringResId);
        }
    }

    private void updateStatusForResponse(String message) {
        if (message.contentEquals("r:vr:ready")) {
            setStatus(R.string.remote_status_video_ready);
        } else if (message.contentEquals("r:vr:recording") || message.contentEquals("r:vr")) {
            setStatus(R.string.remote_status_recording);
        } else if (message.startsWith("r:vr") && message.endsWith(":error")) {
            setStatus(R.string.remote_status_video_error);
        } else if (message.contentEquals("r:vs:stopped") || message.contentEquals("r:vs")) {
            setStatus(R.string.remote_status_stopped);
        } else if (message.contentEquals("r:vs:idle")) {
            setStatus(R.string.remote_video_recording_idle);
        } else if (message.contentEquals("r:uv:error")) {
            setStatus(R.string.remote_status_video_error);
        } else if (message.contentEquals("r:uv:sent")) {
            setStatus(R.string.remote_video_transfer_waiting);
        } else if (message.contentEquals("r:uv")) {
            setStatus(R.string.remote_status_video_received);
        } else if (message.contentEquals("r:pr:taken") || message.contentEquals("r:pr")) {
            setStatus(R.string.remote_status_photo_taken);
        } else if (message.contentEquals("r:pr:error")) {
            setStatus(R.string.remote_status_photo_error);
        } else if (message.contentEquals("r:up:error")) {
            setStatus(R.string.remote_status_photo_error);
        } else if (message.contentEquals("r:up:sent")) {
            setStatus(R.string.remote_photo_transfer_waiting);
        } else if (message.contentEquals("r:up")) {
            setStatus(R.string.remote_status_photo_received);
        } else if (message.contentEquals("r:ar:ready")) {
            setStatus(R.string.remote_status_audio_ready);
        } else if (message.contentEquals("r:ar:recording") || message.contentEquals("r:ar")) {
            setStatus(R.string.remote_status_recording);
        } else if (message.startsWith("r:ar") && message.endsWith(":error")) {
            setStatus(R.string.remote_status_audio_error);
        } else if (message.contentEquals("r:as:stopped") || message.contentEquals("r:as")) {
            setStatus(R.string.remote_status_stopped);
        } else if (message.contentEquals("r:as:idle")) {
            setStatus(R.string.remote_status_audio_idle);
        } else if (message.contentEquals("r:ua:error")) {
            setStatus(R.string.remote_status_audio_error);
        } else if (message.contentEquals("r:ua:sent")) {
            setStatus(R.string.remote_audio_transfer_waiting);
        } else if (message.contentEquals("r:ua")) {
            setStatus(R.string.remote_status_audio_received);
        } else if (message.contentEquals("r:um:error")) {
            setStatus(R.string.remote_status_messages_error);
        } else if (message.contentEquals("r:um:sent")) {
            setStatus(R.string.remote_messages_transfer_waiting);
        } else if (message.contentEquals("r:um")) {
            setStatus(R.string.remote_status_messages_received);
        } else if (message.contentEquals("r:l")) {
            setStatus(R.string.remote_status_location_received);
        } else {
            setStatus(R.string.remote_status_ready);
        }
    }

    private void scheduleResponseTimeout(String message, final Button btn) {
        cancelResponseTimeout();
        responseTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                restoreButton(btn);
                setStatus(R.string.remote_status_timeout);
                Toast.makeText(DroidControleRemoto.this, R.string.remote_command_timeout, Toast.LENGTH_SHORT).show();
            }
        };
        responseTimeoutHandler.postDelayed(responseTimeoutRunnable, getResponseTimeoutMs(message));
    }

    private long getResponseTimeoutMs(String message) {
        if (message != null && (message.startsWith("v") || message.startsWith("p") || message.startsWith("a"))) {
            return MEDIA_RESPONSE_TIMEOUT_MS;
        }
        return RESPONSE_TIMEOUT_MS;
    }

    private void cancelResponseTimeout() {
        if (responseTimeoutHandler != null && responseTimeoutRunnable != null) {
            responseTimeoutHandler.removeCallbacks(responseTimeoutRunnable);
            responseTimeoutRunnable = null;
        }
    }

    public class ReceiverResponseControleRemoto extends BroadcastReceiver
    {

        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(Constantes.MESSAGE);
            if (message == null || message.isEmpty()) {
                return;
            }
            cancelResponseTimeout();
            if (message.contentEquals("r:l"))
            {
                //tv_latitude.setText(String.valueOf(intent.getDoubleExtra(Constantes.LATITUDE, 0.0)));
                //tv_longitude.setText(String.valueOf(intent.getDoubleExtra(Constantes.LONGITUDE, 0.0)));

                String latitude = intent.getStringExtra(Constantes.LATITUDE);
                String longitude = intent.getStringExtra(Constantes.LONGITUDE);
                tv_latitude.setText(latitude);
                tv_longitude.setText(longitude);

                if (latitude == null || longitude == null || latitude.isEmpty() || longitude.isEmpty()) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_location_open_error, Toast.LENGTH_SHORT).show();
                    EnabledButton(message);
                    return;
                }

                String query = latitude.replace(",", ".").trim() + "," + longitude.replace(",", ".").trim();
                Uri mapUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query));
                Intent mIntent = new Intent(Intent.ACTION_VIEW, mapUri);
                startActivity(mIntent);
            } else if (message.startsWith("r:vr")) {
                if (message.startsWith("r:vr") && message.endsWith(":error")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_video_recording_failed, Toast.LENGTH_LONG).show();
                } else if (message.contentEquals("r:vr:ready")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_video_ready_to_send, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_video_recording_started, Toast.LENGTH_SHORT).show();
                }
            } else if (message.startsWith("r:vs")) {
                if (message.contentEquals("r:vs:idle")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_video_recording_idle, Toast.LENGTH_SHORT).show();
                } else if (message.contentEquals("r:vs:error")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_video_recording_failed, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_video_recording_stopped, Toast.LENGTH_SHORT).show();
                }
            } else if (message.startsWith("r:uv")) {
                String filePath = intent.getStringExtra(Constantes.FILE_LOCAL_PATH);
                if (filePath != null && !filePath.isEmpty()) {
                    File videoFile = new File(filePath);
                    boolean notified = intent.getBooleanExtra(Constantes.NOTIFICATION_SHOWN, false);
                    Toast.makeText(DroidControleRemoto.this,
                            getString(R.string.remote_video_received, filePath),
                            Toast.LENGTH_LONG).show();
                    if (!notified) {
                        showVideoReceivedDialog(videoFile);
                    }
                } else if (message.contentEquals("r:uv:error")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_video_not_available, Toast.LENGTH_LONG).show();
                } else if (message.contentEquals("r:uv:sent")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_video_transfer_waiting, Toast.LENGTH_SHORT).show();
                }
            } else if (message.startsWith("r:pr")) {
                if (message.contentEquals("r:pr:error")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_photo_failed, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_photo_taken, Toast.LENGTH_SHORT).show();
                }
            } else if (message.startsWith("r:up")) {
                String filePath = intent.getStringExtra(Constantes.FILE_LOCAL_PATH);
                if (filePath != null && !filePath.isEmpty()) {
                    File photoFile = new File(filePath);
                    boolean notified = intent.getBooleanExtra(Constantes.NOTIFICATION_SHOWN, false);
                    Toast.makeText(DroidControleRemoto.this,
                            getString(R.string.remote_photo_received, filePath),
                            Toast.LENGTH_LONG).show();
                    if (!notified) {
                        showPhotoReceivedDialog(photoFile);
                    }
                } else if (message.contentEquals("r:up:error")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_photo_not_available, Toast.LENGTH_LONG).show();
                } else if (message.contentEquals("r:up:sent")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_photo_transfer_waiting, Toast.LENGTH_SHORT).show();
                }
            } else if (message.startsWith("r:ar")) {
                if (message.startsWith("r:ar") && message.endsWith(":error")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_audio_recording_failed, Toast.LENGTH_LONG).show();
                } else if (message.contentEquals("r:ar:ready")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_audio_ready_to_send, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_audio_recording_started, Toast.LENGTH_SHORT).show();
                }
            } else if (message.startsWith("r:as")) {
                if (message.contentEquals("r:as:idle")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_audio_recording_idle, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_audio_recording_stopped, Toast.LENGTH_SHORT).show();
                }
            } else if (message.startsWith("r:ua")) {
                String filePath = intent.getStringExtra(Constantes.FILE_LOCAL_PATH);
                if (filePath != null && !filePath.isEmpty()) {
                    File audioFile = new File(filePath);
                    boolean notified = intent.getBooleanExtra(Constantes.NOTIFICATION_SHOWN, false);
                    Toast.makeText(DroidControleRemoto.this,
                            getString(R.string.remote_audio_received, filePath),
                            Toast.LENGTH_LONG).show();
                    if (!notified) {
                        showAudioReceivedDialog(audioFile);
                    }
                } else if (message.contentEquals("r:ua:error")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_audio_not_available, Toast.LENGTH_LONG).show();
                } else if (message.contentEquals("r:ua:sent")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_audio_transfer_waiting, Toast.LENGTH_SHORT).show();
                }
            } else if (message.startsWith("r:um")) {
                String filePath = intent.getStringExtra(Constantes.FILE_LOCAL_PATH);
                if (filePath != null && !filePath.isEmpty()) {
                    File messagesFile = new File(filePath);
                    boolean notified = intent.getBooleanExtra(Constantes.NOTIFICATION_SHOWN, false);
                    Toast.makeText(DroidControleRemoto.this,
                            getString(R.string.remote_messages_received, filePath),
                            Toast.LENGTH_LONG).show();
                    if (!notified) {
                        showMessagesReceivedDialog(messagesFile);
                    }
                } else if (message.contentEquals("r:um:error")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_messages_not_available, Toast.LENGTH_LONG).show();
                } else if (message.contentEquals("r:um:sent")) {
                    Toast.makeText(DroidControleRemoto.this, R.string.remote_messages_transfer_waiting, Toast.LENGTH_SHORT).show();
                }
            }
            EnabledButton(message);

        }
    }

    private void showAudioReceivedDialog(final File audioFile) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_audio_notification_title)
                .setMessage(getString(R.string.remote_audio_ready_dialog_message, audioFile.getName()))
                .setPositiveButton(R.string.remote_audio_open, (dialog, which) ->
                        FileTransferHelper.openAudioFile(DroidControleRemoto.this, audioFile))
                .setNegativeButton(R.string.remote_audio_notification_dismiss, null)
                .show();
    }

    private void showVideoReceivedDialog(final File videoFile) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_video_notification_title)
                .setMessage(getString(R.string.remote_video_ready_dialog_message, videoFile.getName()))
                .setPositiveButton(R.string.remote_video_open, (dialog, which) ->
                        FileTransferHelper.openVideoFile(DroidControleRemoto.this, videoFile))
                .setNegativeButton(R.string.remote_audio_notification_dismiss, null)
                .show();
    }

    private void showPhotoReceivedDialog(final File photoFile) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_photo_notification_title)
                .setMessage(getString(R.string.remote_photo_ready_dialog_message, photoFile.getName()))
                .setPositiveButton(R.string.remote_photo_open, (dialog, which) ->
                        FileTransferHelper.openPhotoFile(DroidControleRemoto.this, photoFile))
                .setNegativeButton(R.string.remote_audio_notification_dismiss, null)
                .show();
    }

    private void showMessagesReceivedDialog(final File messagesFile) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remote_messages_notification_title)
                .setMessage(getString(R.string.remote_messages_ready_dialog_message, messagesFile.getName()))
                .setPositiveButton(R.string.remote_messages_open, (dialog, which) ->
                        FileTransferHelper.openTextFile(DroidControleRemoto.this, messagesFile))
                .setNegativeButton(R.string.remote_audio_notification_dismiss, null)
                .show();
    }
}

