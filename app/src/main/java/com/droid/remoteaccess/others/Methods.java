package com.droid.remoteaccess.others;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.droid.remoteaccess.feature.Constantes;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.core.app.ActivityCompat;

/**
 * Created by Robson on 02/03/2016.
 */
public class Methods {

    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };
    public static final int PERMISSION_ALL = 2;
    public static final int PERMISSION_NOTIFICATIONS = 3;

    public static final String GETIDDEVICE = "";

    public static String getEmail(Context context) {
        return getNameDevice(context) + " (" + getIDDevice(context) + ")";
    }

    public static String getAccount(Context context) {
        String account = "padrao";
        try {
            String email = getEmail(context);
            String[] accounts = email.split("@");
            account = accounts[0];
        } catch (Exception ex) {
        }
        return account;
    }

    public static String getDateTimeFormated() {
        SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyyMMdd_hhmmss");
        return simpleFormat.format(new Date(System.currentTimeMillis()));
    }

    public static String getNameDevice(Context context) {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public static String getIDDevice(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            return "0000000000";
        }
        return androidId;
    }

    public static void showMessage(final Activity activity, String mensagem) {
        AlertDialog.Builder alerta = new AlertDialog.Builder(activity);
        alerta.setMessage(mensagem);
        alerta.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // activity.finish();
            }
        });
        alerta.show();
    }

    public static int obtemQualidadeCamera(final Context context, Constantes.EnumTypeViewCam typeViewCam) {
        int qualid = 0; // QUALITY_LOW
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            if (typeViewCam == Constantes.EnumTypeViewCam.FacingFront) {
                qualid = Integer.parseInt(sp.getString("ltp_qualidadeCameraFrontal", "0"));
            } else qualid = Integer.parseInt(sp.getString("ltp_qualidadeCameraTraseira", "0"));

        } catch (Exception ex) {
            Log.d("DroidVideo", ex.getMessage());
        }
        return qualid;

    }

    public static int obtemLocalGravacao(final Context context) {
        int local = 0; // Interno
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            local = Integer.parseInt(sp.getString("ltp_localGravacaoVideo", "0"));
        } catch (Exception ex) {
            Log.d("DroidVideo", ex.getMessage());
        }
        return local;

    }

    public static boolean exibeTelaInicial(final Context context) {
        boolean spf = false;
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            spf = sp.getBoolean("spf_exibeAoIniciar", true);
        } catch (Exception ex) {
            Log.d("DroidVideo", ex.getMessage());
        }
        return spf;

    }

    public static String obtemDescricaoPreferencias(final Context context, String valor_selecionado, int nome_lista, int lista_valor) {
        String nome_selecionado = "";

        String[] array_lista = context.getResources().getStringArray(nome_lista);
        String[] array_lista_valores = context.getResources().getStringArray(lista_valor);

        for (int i = 0; i < array_lista_valores.length; i++) {
            if (array_lista_valores[i].equals(valor_selecionado)) {
                nome_selecionado = array_lista[i].toString();
                break;
            }
        }
        return nome_selecionado;
    }

    public static String chamadaBroadCastPorComandoTexto(Intent intent) {
        String chamadaPorCmdTxt = "";
        try {

            chamadaPorCmdTxt = intent.getStringExtra(Constantes.CHAMADAPORCOMANDOTEXTO);

        } catch (Exception ex) {

        }
        return chamadaPorCmdTxt;
    }

    public static String GetPathStorage()
    {
        // SandBox
        return System.getenv("EXTERNAL_STORAGE");
    }

    public static boolean AskPermissionGrand(Activity activity, Context appContext) {
        boolean retorno = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> missingPermissions = new ArrayList<>();
            for (String permission : PERMISSIONS) {
                if (appContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission);
                }
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && appContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (!missingPermissions.isEmpty()) {
                ActivityCompat.requestPermissions(activity,
                        missingPermissions.toArray(new String[0]),
                        PERMISSION_ALL);
                retorno = false;
            }
        }
        return retorno;
    }

    public static boolean AskNotificationPermission(Activity activity, Context appContext) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        if (appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                PERMISSION_NOTIFICATIONS);
        return false;
    }

}

