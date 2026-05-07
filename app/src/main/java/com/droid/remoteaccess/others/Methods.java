package com.droid.remoteaccess.others;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
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
import android.util.Patterns;

import com.droid.remoteaccess.feature.Constantes;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import androidx.core.app.ActivityCompat;

/**
 * Created by Robson on 02/03/2016.
 */
public class Methods {

    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.GET_ACCOUNTS
    };
    public static final int PERMISSION_ALL = 2;
    public static final int PERMISSION_NOTIFICATIONS = 3;

    public static final String GETIDDEVICE = "";
    private static final String PREF_REMOTE_ACCESS_INSTALL_ID = "remote_access_install_id_v1";

    public static String getEmail(Context context) {
        String accountEmail = getPrimaryAccountEmail(context);
        if (accountEmail != null && !accountEmail.isEmpty()) {
            return accountEmail;
        }
        return "";
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
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        return formatDeviceName(manufacturer + " " + model);
    }

    public static String formatDeviceName(String deviceName) {
        if (deviceName == null) {
            return "Aparelho Android";
        }

        String cleaned = deviceName.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return "Aparelho Android";
        }

        String samsungName = formatSamsungDeviceName(cleaned);
        if (!samsungName.isEmpty()) {
            return samsungName;
        }
        return cleaned;
    }

    public static String formatEmailForDisplay(String email) {
        if (email != null && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return email.trim();
        }
        return "";
    }

    private static String getPrimaryAccountEmail(Context context) {
        try {
            if (context == null) {
                return "";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && context.checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
                return "";
            }

            AccountManager accountManager = AccountManager.get(context.getApplicationContext());
            Account[] googleAccounts = accountManager.getAccountsByType("com.google");
            String googleEmail = firstEmailAccount(googleAccounts);
            if (!googleEmail.isEmpty()) {
                return googleEmail;
            }
            return firstEmailAccount(accountManager.getAccounts());
        } catch (Exception ex) {
            Log.d("RemoteAccess", "Não foi possível obter a conta principal", ex);
            return "";
        }
    }

    private static String firstEmailAccount(Account[] accounts) {
        if (accounts == null) {
            return "";
        }
        for (Account account : accounts) {
            if (account != null && account.name != null
                    && Patterns.EMAIL_ADDRESS.matcher(account.name.trim()).matches()) {
                return account.name.trim();
            }
        }
        return "";
    }

    private static String formatSamsungDeviceName(String deviceName) {
        String upper = deviceName.toUpperCase(Locale.US);
        if (!upper.contains("SAMSUNG") && !upper.contains("SM-")) {
            return "";
        }
        String normalized = upper.replaceAll("[^A-Z0-9]", "");

        if (normalized.contains("SMG930")) return "Galaxy S7";
        if (normalized.contains("SMG935")) return "Galaxy S7 Edge";
        if (normalized.contains("SMG950")) return "Galaxy S8";
        if (normalized.contains("SMG955")) return "Galaxy S8+";
        if (normalized.contains("SMG960")) return "Galaxy S9";
        if (normalized.contains("SMG965")) return "Galaxy S9+";
        if (normalized.contains("SMG970")) return "Galaxy S10e";
        if (normalized.contains("SMG973")) return "Galaxy S10";
        if (normalized.contains("SMG975")) return "Galaxy S10+";
        if (normalized.contains("SMG980") || normalized.contains("SMG981")) return "Galaxy S20";
        if (normalized.contains("SMG985") || normalized.contains("SMG986")) return "Galaxy S20+";
        if (normalized.contains("SMG988")) return "Galaxy S20 Ultra";
        if (normalized.contains("SMG991")) return "Galaxy S21";
        if (normalized.contains("SMG996")) return "Galaxy S21+";
        if (normalized.contains("SMG998")) return "Galaxy S21 Ultra";
        if (normalized.contains("SMS901")) return "Galaxy S22";
        if (normalized.contains("SMS906")) return "Galaxy S22+";
        if (normalized.contains("SMS908")) return "Galaxy S22 Ultra";
        if (normalized.contains("SMS911")) return "Galaxy S23";
        if (normalized.contains("SMS916")) return "Galaxy S23+";
        if (normalized.contains("SMS918")) return "Galaxy S23 Ultra";
        if (normalized.contains("SMS921")) return "Galaxy S24";
        if (normalized.contains("SMS926")) return "Galaxy S24+";
        if (normalized.contains("SMS928")) return "Galaxy S24 Ultra";
        if (normalized.contains("SMF900")) return "Galaxy Fold";
        if (normalized.contains("SMF907")) return "Galaxy Fold 5G";
        if (normalized.contains("SMF916")) return "Galaxy Z Fold2";
        if (normalized.contains("SMF926")) return "Galaxy Z Fold3";
        if (normalized.contains("SMF936")) return "Galaxy Z Fold4";
        if (normalized.contains("SMF946")) return "Galaxy Z Fold5";
        if (normalized.contains("SMF956")) return "Galaxy Z Fold6";
        if (upper.contains("FOLD")) return "Galaxy Z Fold";

        return deviceName.replaceFirst("(?i)^samsung\\s+", "Samsung ");
    }

    public static String getIDDevice(Context context) {
        if (context == null) {
            return "ra_00000000000000000000";
        }
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        String installId = preferences.getString(PREF_REMOTE_ACCESS_INSTALL_ID, "");
        if (isValidRemoteAccessInstallId(installId)) {
            return installId;
        }

        synchronized (Methods.class) {
            installId = preferences.getString(PREF_REMOTE_ACCESS_INSTALL_ID, "");
            if (isValidRemoteAccessInstallId(installId)) {
                return installId;
            }
            installId = createRemoteAccessInstallId(appContext);
            preferences.edit().putString(PREF_REMOTE_ACCESS_INSTALL_ID, installId).apply();
            return installId;
        }
    }

    private static boolean isValidRemoteAccessInstallId(String installId) {
        return installId != null && installId.matches("ra_[A-Za-z0-9]{20}");
    }

    private static String createRemoteAccessInstallId(Context context) {
        String seed = UUID.randomUUID().toString().replace("-", "");
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty()) {
                seed = seed + androidId.replaceAll("[^A-Za-z0-9]", "");
            }
        } catch (Exception ignored) {
        }
        String compact = seed.replaceAll("[^A-Za-z0-9]", "");
        if (compact.length() < 20) {
            compact = compact + UUID.randomUUID().toString().replace("-", "");
        }
        return "ra_" + compact.substring(0, 20);
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

