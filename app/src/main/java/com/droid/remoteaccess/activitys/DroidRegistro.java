package com.droid.remoteaccess.activitys;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.telaregistro);

        context = getBaseContext();
        persintencia = new Persintencia(context);
        btn_registrar = (Button) findViewById(R.id.telaregistro_btn_registrar);
        mInformationTextView = (TextView) findViewById(R.id.informationTextView);
        mRegistrationProgressBar = (ProgressBar) findViewById(R.id.registrationProgressBar);



        ll_registro = (LinearLayout) findViewById(R.id.telaregistro_ll_registro);
        rl_aguarde = (LinearLayout) findViewById(R.id.telaregistro_rl_aguarde);

        if (Methods.AskPermissionGrand( this, context.getApplicationContext())) {

            if (ContatoCadastrado()) {
                finish();
                ChamaListaContatos();
            }


            btn_registrar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ll_registro.setVisibility(View.INVISIBLE);
                    rl_aguarde.setVisibility(View.VISIBLE);
                    ContatoCadastrado();
                }
            });

            mRegistrationBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {


                    SharedPreferences sharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(context);
                    boolean sentToken = sharedPreferences
                            .getBoolean(Constantes.SENT_TOKEN_TO_SERVER, false);
                    if (sentToken) {
                        ChamaListaContatos();
                        Toast.makeText(context, getString(R.string.gcm_send_message), Toast.LENGTH_SHORT).show();
                        Intent mIntent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(mIntent);
                        finish();

                    } else {

                        Toast.makeText(context, getString(R.string.token_error_message), Toast.LENGTH_SHORT).show();
                        ll_registro.setVisibility(View.VISIBLE);
                        rl_aguarde.setVisibility(View.INVISIBLE);

                    }

                }
            };
        }
        else finish();

    }

    private void ChamaListaContatos() {

        Intent mIntent = new Intent(context, DroidListaContatos.class);
        startActivity(mIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                new IntentFilter(Constantes.REGISTRATION_COMPLETE));

    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegistrationBroadcastReceiver);
        super.onPause();
    }

    private boolean ContatoCadastrado() {
        boolean contatoCadastrado = persintencia.JaExisteContatoCadastrado(Methods.getIDDevice( context));

        if (!contatoCadastrado) {
            if (Methods.checkPlayServices(DroidRegistro.this)) {
                // Start IntentService to register this application with GCM.
                Intent intent = new Intent(DroidRegistro.this, RegistrationIntentService.class);
                startService(intent);


            }
        }
        return contatoCadastrado;

    }


}
