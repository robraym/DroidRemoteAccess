package com.droid.remoteaccess.recorder;

/**
 * Created by Robson on 08/03/2016.
 */

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.droid.remoteaccess.R;
import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.services.BrokerMessaging;
import com.droid.remoteaccess.services.FileTransferHelper;

import java.io.File;
import java.io.IOException;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class DroidAudioRecorder extends Service {
    private static final String LOG_TAG = "DroidAudioRecorder";
    private static final String PREF_LAST_AUDIO_FILE = "last_audio_file";
    private static final String PREF_AUDIO_COMMAND_RESPONSE_PREFIX = "audio_command_response_";
    private static final String AUDIO_FOLDER = "RemoteAccess";
    private static final String CHANNEL_ID = "audio_recording";
    private static final int NOTIFICATION_ID = 1002;
    private static final long AUDIO_5_SECONDS_MS = 5000;
    private static final long AUDIO_10_SECONDS_MS = 10000;
    private static final long AUDIO_15_SECONDS_MS = 15000;

    private final Object recorderLock = new Object();
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;
    private String mFileName = null;
    private boolean recording = false;

    private void onRecord(boolean start) {
        if (start) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void onPlay(boolean start) {
        if (start) {
            startPlaying();
        } else {
            stopPlaying();
        }
    }

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed", e);
        }
    }

    private void stopPlaying() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    private boolean startRecording() {
        synchronized (recorderLock) {
            if (recording) {
                Log.i(LOG_TAG, "Audio recording already running");
                return true;
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w(LOG_TAG, "Cannot record audio because RECORD_AUDIO permission is not granted");
                return false;
            }

            mFileName = buildAudioFilePath();
            if (mFileName == null || mFileName.isEmpty()) {
                Log.e(LOG_TAG, "Cannot record audio because output path is unavailable");
                return false;
            }

            MediaRecorder recorder = new MediaRecorder();
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setOutputFile(mFileName);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.prepare();
                recorder.start();
                mRecorder = recorder;
                recording = true;
                saveLatestAudioFile(this, mFileName);
                Log.i(LOG_TAG, "Audio recording started: " + mFileName);
                return true;
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to start audio recording", e);
                safeReleaseRecorder(recorder);
                return false;
            }
        }
    }

    private boolean stopRecording() {
        synchronized (recorderLock) {
            if (mRecorder == null) {
                recording = false;
                Log.i(LOG_TAG, "Audio recording is not running");
                return false;
            }

            boolean stopped = true;
            try {
                mRecorder.stop();
                Log.i(LOG_TAG, "Audio recording stopped: " + mFileName);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Audio recorder stop failed, releasing recorder", e);
                stopped = false;
            } finally {
                safeReleaseRecorder(mRecorder);
                mRecorder = null;
                recording = false;
            }
            return stopped;
        }
    }

    private String buildAudioFilePath() {
        File outputDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (outputDir != null) {
            outputDir = new File(outputDir, AUDIO_FOLDER);
        }
        if (outputDir == null) {
            File filesDir = getFilesDir();
            if (filesDir != null) {
                outputDir = new File(filesDir, AUDIO_FOLDER);
            }
        }
        if (outputDir == null) {
            return null;
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            return null;
        }
        return new File(outputDir, "audio_" + Methods.getDateTimeFormated() + ".3gp").getAbsolutePath();
    }

    public static File getLatestAudioFile(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String latestPath = preferences.getString(PREF_LAST_AUDIO_FILE, "");
        if (latestPath != null && !latestPath.isEmpty()) {
            File latestFile = new File(latestPath);
            if (latestFile.exists() && latestFile.isFile() && latestFile.length() > 0) {
                return latestFile;
            }
        }
        return findNewestAudioFile(context);
    }

    private static File findNewestAudioFile(Context context) {
        File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (outputDir != null) {
            outputDir = new File(outputDir, AUDIO_FOLDER);
        }
        if (outputDir == null || !outputDir.exists()) {
            return null;
        }

        File[] files = outputDir.listFiles();
        if (files == null) {
            return null;
        }

        File newest = null;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".3gp") && file.length() > 0) {
                if (newest == null || file.lastModified() > newest.lastModified()) {
                    newest = file;
                }
            }
        }
        return newest;
    }

    private static void saveLatestAudioFile(Context context, String path) {
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit()
                .putString(PREF_LAST_AUDIO_FILE, path)
                .apply();
    }

    private void safeReleaseRecorder(MediaRecorder recorder) {
        if (recorder == null) {
            return;
        }
        try {
            recorder.reset();
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Audio recorder reset failed", e);
        }
        try {
            recorder.release();
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Audio recorder release failed", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final String command = intent.getStringExtra(Constantes.CHAMADAPORCOMANDOTEXTO);
        if (command == null || command.isEmpty()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final long timedDurationMs = getTimedRecordingDurationMs(command);
        final boolean needsForegroundRecording = command.equalsIgnoreCase("ar") || timedDurationMs > 0;
        if (needsForegroundRecording && !startRecordingForeground()) {
            Thread responseWorker = new Thread(new Runnable() {
                @Override
                public void run() {
                    sendAudioCommandResponse(intent, "r:ar:error");
                    stopSelf(startId);
                }
            }, "DroidAudioRecorderErrorResponse");
            responseWorker.start();
            return START_NOT_STICKY;
        }

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                if (command.equalsIgnoreCase("as")) {
                    boolean stopped = Parar();
                    sendAudioCommandResponse(intent, stopped ? "r:as:stopped" : "r:as:idle");
                    stopRecordingForeground();
                    stopSelf(startId);
                } else if (timedDurationMs > 0) {
                    boolean recorded = GravarPorTempo(timedDurationMs);
                    if (recorded) {
                        boolean uploaded = uploadLatestAudio(intent);
                        sendAudioCommandResponse(intent, uploaded ? "r:ua:sent" : "r:ua:error");
                    } else {
                        sendAudioCommandResponse(intent, "r:ar:error");
                    }
                    stopRecordingForeground();
                    stopSelf(startId);
                } else if (command.equalsIgnoreCase("ar")) {
                    boolean started = Gravar();
                    sendAudioCommandResponse(intent, started ? "r:ar:recording" : "r:ar:error");
                    if (!started) {
                        stopRecordingForeground();
                        stopSelf(startId);
                    }
                } else {
                    stopSelf(startId);
                }
            }
        }, "DroidAudioRecorderCommand");
        worker.start();

        return START_STICKY;
    }

    private long getTimedRecordingDurationMs(String command) {
        if (command == null) {
            return 0;
        }
        if (command.equalsIgnoreCase("ar5")) {
            return AUDIO_5_SECONDS_MS;
        }
        if (command.equalsIgnoreCase("ar10")) {
            return AUDIO_10_SECONDS_MS;
        }
        if (command.equalsIgnoreCase("ar15")) {
            return AUDIO_15_SECONDS_MS;
        }
        return 0;
    }

    private boolean uploadLatestAudio(Intent requestIntent) {
        String responseToken = requestIntent.getStringExtra(Constantes.REPLY_TOKEN);
        String requesterId = requestIntent.getStringExtra(Constantes.ID_FROM);
        String requesterDevice = requestIntent.getStringExtra(Constantes.DEVICE_FROM);
        String commandId = requestIntent.getStringExtra(Constantes.COMMAND_ID);
        return FileTransferHelper.uploadLatestAudio(getApplicationContext(), responseToken,
                requesterId, requesterDevice, commandId);
    }

    private boolean Gravar() {
        return startRecording();
    }

    private boolean GravarPorTempo(long durationMs) {
        boolean started = startRecording();
        if (!started) {
            return false;
        }
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return stopRecording();
    }

    private boolean Parar() {
        return stopRecording();
    }

    private boolean startRecordingForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.remote_audio_recording_channel),
                        NotificationManager.IMPORTANCE_LOW);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_remote_access)
                    .setContentTitle(getString(R.string.remote_audio_recording_title))
                    .setContentText(getString(R.string.remote_audio_recording_text))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Failed to start foreground audio recording service", ex);
            return false;
        }
    }

    private void stopRecordingForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ex) {
            Log.w(LOG_TAG, "Failed to stop foreground audio recording service", ex);
        }
    }

    private void sendAudioCommandResponse(Intent requestIntent, String responseMessage) {
        try {
            String responseToken = requestIntent.getStringExtra(Constantes.REPLY_TOKEN);
            String requesterId = requestIntent.getStringExtra(Constantes.ID_FROM);
            String requesterDevice = requestIntent.getStringExtra(Constantes.DEVICE_FROM);
            String commandId = requestIntent.getStringExtra(Constantes.COMMAND_ID);
            if (responseToken == null || responseToken.isEmpty()) {
                Log.w(LOG_TAG, "Cannot send audio response because response token is empty: " + responseMessage);
                return;
            }

            android.os.Bundle data = new android.os.Bundle();
            data.putString(Constantes.MESSAGE, responseMessage);
            data.putString(Constantes.ID_FROM, Methods.getIDDevice(getApplicationContext()));
            data.putString(Constantes.EMAIL_FROM, Methods.getEmail(getApplicationContext()));
            data.putString(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopic(getApplicationContext()));
            data.putString(Constantes.DEVICE_FROM, Methods.getNameDevice(getApplicationContext()));
            data.putString(Constantes.ID_TO, requesterId);
            data.putString(Constantes.DEVICE_TO, requesterDevice);
            data.putString(Constantes.COMMAND_ID, commandId);

            BrokerMessaging.publishResponse(responseToken, data);
            markAudioCommandResponded(getApplicationContext(), commandId);
            Log.i(LOG_TAG, "Audio command response sent: " + responseMessage);
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Failed to send audio command response: " + responseMessage, ex);
        }
    }

    public static void clearAudioCommandResponse(Context context, String commandId) {
        if (commandId == null || commandId.isEmpty()) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit()
                .remove(PREF_AUDIO_COMMAND_RESPONSE_PREFIX + commandId)
                .apply();
    }

    public static boolean hasAudioCommandResponse(Context context, String commandId) {
        if (commandId == null || commandId.isEmpty()) {
            return false;
        }
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getBoolean(PREF_AUDIO_COMMAND_RESPONSE_PREFIX + commandId, false);
    }

    private static void markAudioCommandResponded(Context context, String commandId) {
        if (commandId == null || commandId.isEmpty()) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit()
                .putBoolean(PREF_AUDIO_COMMAND_RESPONSE_PREFIX + commandId, true)
                .apply();
    }

    @Override
    public void onDestroy() {
        stopRecording();
        stopRecordingForeground();
        stopPlaying();
        super.onDestroy();
    }
}
