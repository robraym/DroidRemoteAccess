package com.droid.remoteaccess.gdrive;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.droid.remoteaccess.R;
import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.recorder.DroidAudioRecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class CreateFileActivity extends Activity {

    private static final String TAG = "CreateFileActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exportCommandFile();
        finish();
    }

    private void exportCommandFile() {
        try {
            String command = getIntent().getStringExtra(Constantes.CHAMADAPORCOMANDOTEXTO);
            File outputDir = new File(getExternalFilesDir(null), "exports");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IllegalStateException("Não foi possível criar a pasta de exportação.");
            }

            if ("um".equalsIgnoreCase(command)) {
                writeMessages(outputDir);
            } else if ("ua".equalsIgnoreCase(command)) {
                File audioFile = DroidAudioRecorder.getLatestAudioFile(this);
                if (audioFile == null) {
                    throw new IllegalStateException("Nenhum áudio gravado encontrado.");
                }
                copyFile(audioFile.getAbsolutePath(), new File(outputDir, "audio_" + Methods.getDateTimeFormated() + ".3gp"));
            } else if ("uv".equalsIgnoreCase(command)) {
                copyFile(Methods.GetPathStorage() + "/video.mp4", new File(outputDir, "video_" + Methods.getDateTimeFormated() + ".mp4"));
            }
        } catch (Exception e) {
            Log.d(TAG, "Falha ao exportar arquivo local", e);
            Toast.makeText(this, R.string.export_error_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void writeMessages(File outputDir) throws Exception {
        File outputFile = new File(outputDir, "historico_notificacoes_" + Methods.getDateTimeFormated() + ".txt");
        Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
        writer.write(getIntent().getStringExtra(Constantes.MESSAGE));
        writer.close();
    }

    private void copyFile(String sourcePath, File outputFile) throws Exception {
        FileInputStream inputStream = new FileInputStream(sourcePath);
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        inputStream.close();
        outputStream.close();
    }
}
