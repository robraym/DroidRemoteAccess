package com.droid.remoteaccess.activitys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.R;
import com.droid.remoteaccess.services.RegistrationIntentService;
import com.droid.remoteaccess.others.Methods;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Created by Robson on 06/03/2016.
 */

public class DroidRegistro extends AppCompatActivity {

    private Button btn_registrar;
    private Context context;
    private Persintencia persintencia;
    private BroadcastReceiver mRegistrationBroadcastReceiver;
    private TextView mInformationTextView;
    private ProgressBar mRegistrationProgressBar;
    private LinearLayout ll_registro;
    private LinearLayout rl_aguarde;
    private boolean registrationReceiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getBaseContext();
        persintencia = new Persintencia(context);

        if (ContatoCadastradoValido()) {
            ChamaListaContatos();
            finish();
            return;
        }

        setContentView(R.layout.telaregistro);

        btn_registrar = (Button) findViewById(R.id.telaregistro_btn_registrar);
        mInformationTextView = (TextView) findViewById(R.id.informationTextView);
        mRegistrationProgressBar = (ProgressBar) findViewById(R.id.registrationProgressBar);



        ll_registro = (LinearLayout) findViewById(R.id.telaregistro_ll_registro);
        rl_aguarde = (LinearLayout) findViewById(R.id.telaregistro_rl_aguarde);

        mRegistrationBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(context);
                boolean sentToken = sharedPreferences
                        .getBoolean(Constantes.SENT_TOKEN_TO_SERVER, false);
                if (sentToken) {
                    ChamaListaContatos();
                    Toast.makeText(context, getString(R.string.registration_success_message), Toast.LENGTH_SHORT).show();
                    abrirAcessoNotificacoesSeNecessario();
                    finish();
                } else {
                    Toast.makeText(context, getString(R.string.token_error_message), Toast.LENGTH_SHORT).show();
                    ll_registro.setVisibility(View.VISIBLE);
                    rl_aguarde.setVisibility(View.INVISIBLE);
                }
            }
        };

        btn_registrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iniciarRegistro();
            }
        });

        if (Methods.AskPermissionGrand(this, context.getApplicationContext())) {
            iniciarRegistro();
        }

    }

    private void iniciarRegistro() {
        if (ContatoCadastrado()) {
            finish();
            ChamaListaContatos();
            return;
        }
        ll_registro.setVisibility(View.INVISIBLE);
        rl_aguarde.setVisibility(View.VISIBLE);
    }

    private void ChamaListaContatos() {

        Intent mIntent = new Intent(context, DroidListaContatos.class);
        startActivity(mIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRegistrationBroadcastReceiver != null && !registrationReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                    new IntentFilter(Constantes.REGISTRATION_COMPLETE));
            registrationReceiverRegistered = true;
        }

    }

    @Override
    protected void onPause() {
        if (mRegistrationBroadcastReceiver != null && registrationReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegistrationBroadcastReceiver);
            registrationReceiverRegistered = false;
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != Methods.PERMISSION_ALL) {
            return;
        }

        boolean granted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }

        if (granted) {
            iniciarRegistro();
        } else {
            Toast.makeText(this, R.string.permission_required_message, Toast.LENGTH_LONG).show();
            ll_registro.setVisibility(View.VISIBLE);
            rl_aguarde.setVisibility(View.INVISIBLE);
        }
    }

    private boolean ContatoCadastrado() {
        boolean contatoCadastrado = ContatoCadastradoValido();

        if (!contatoCadastrado) {
            Intent intent = new Intent(DroidRegistro.this, RegistrationIntentService.class);
            startService(intent);
        }
        return contatoCadastrado;

    }

    private boolean ContatoCadastradoValido() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getBoolean(Constantes.SENT_TOKEN_TO_SERVER, false);
    }

    private void abrirAcessoNotificacoesSeNecessario() {
        if (isNotificationListenerEnabled()) {
            return;
        }
        Intent mIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(mIntent);
    }

    private boolean isNotificationListenerEnabled() {
        try {
            String enabledListeners = Settings.Secure.getString(
                    getContentResolver(),
                    "enabled_notification_listeners");
            return !TextUtils.isEmpty(enabledListeners)
                    && enabledListeners.toLowerCase().contains(getPackageName().toLowerCase());
        } catch (Exception ignored) {
            return false;
        }
    }


}
