package com.droid.remoteaccess.recorder;

/**
 * Created by Robson on 08/03/2016.
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.Methods;

import java.io.IOException;

import androidx.annotation.Nullable;


public class DroidAudioRecorder extends Service {
    private static final String LOG_TAG = "DroidAudioRecorder";
    private static String mFileName = null;

    private MediaRecorder mRecorder = null;


    private MediaPlayer mPlayer = null;
    private Intent mIntentService;
    private String chamadaPorComandoTexto;

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
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
    }

    private void startRecording() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    public DroidAudioRecorder() {
        mFileName = Methods.GetPathStorage();
        mFileName += "/audio.mp3";
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        mIntentService = intent;

        if (ComandoPorTexto("as")) {
            Parar();
        } else if (ComandoPorTexto("ar")) {
            Gravar();
        }
    }

    private void Gravar() {
        if(mRecorder == null) {
            startRecording();
        }
    }

    private void Parar() {
        if(mRecorder != null) {
            stopRecording();
        }
    }

    private boolean ComandoPorTexto(String cmd) {
        boolean ret = false;
        chamadaPorComandoTexto = mIntentService.getStringExtra(Constantes.CHAMADAPORCOMANDOTEXTO);
        if (chamadaPorComandoTexto != null) {
            ret = chamadaPorComandoTexto.equalsIgnoreCase(cmd);
        }

        return ret;
    }

    @Override
    public void onDestroy() {

        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        super.onDestroy();
    }
}

