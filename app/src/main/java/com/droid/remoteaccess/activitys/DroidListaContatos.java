package com.droid.remoteaccess.activitys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.feature.HMContato;
import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.R;
import com.droid.remoteaccess.others.DeviceNameResolver;
import com.droid.remoteaccess.others.DevicePresence;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.services.BrokerSyncService;
import com.droid.remoteaccess.services.RegistrationIntentService;

import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Created by Robson on 06/03/2016.
 */
public class DroidListaContatos extends AppCompatActivity {

    private Context context;
    private ListView lv_contatos;
    private Persintencia persintencia;
    private ReceiverResponseListaContatos receiver;
    private Handler presenceRefreshHandler;
    private Runnable presenceRefreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.telalistacontatos);

        context = getBaseContext();
        presenceRefreshHandler = new Handler(Looper.getMainLooper());
        Methods.AskNotificationPermission(this, getApplicationContext());
        lv_contatos = (ListView) findViewById(R.id.telalistacontatos__lv_contatos);
        lv_contatos.setEmptyView(findViewById(R.id.telalistacontatos_tv_vazio));
        persintencia = new Persintencia(context);

        ContextCompat.startForegroundService(this, new Intent(this, BrokerSyncService.class));
        startService(new Intent(this, RegistrationIntentService.class));

        atualizaAdapterContatos();


        lv_contatos.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                HMContato item = (HMContato) parent.getItemAtPosition(position);
                //
                //chamarDetalhes(Long.parseLong(item.get(HMContato.EMAIL)));
                //

                persintencia.ApagarContato(item.get(HMContato.ID));
                Methods.showMessage(DroidListaContatos.this, "Registro apagado");
                atualizaAdapterContatos();
                return true;
            }
        });
        lv_contatos.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent mIntent = new Intent(context, DroidControleRemoto.class);
                HMContato item = (HMContato) parent.getItemAtPosition(position);
                mIntent.putExtra(Constantes.ID_FROM, Methods.getIDDevice(context));
                mIntent.putExtra(Constantes.ID_TO, item.get(HMContato.ID));
                startActivity(mIntent);
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Constantes.RECEIVERRESPONSELISTACONTATOS);
        filter.addAction(Constantes.RECEIVERPRESENCESTATUS);
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        receiver = new ReceiverResponseListaContatos();
        //
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        startPresenceRefresh();

    }

    @Override
    protected void onDestroy() {
        stopPresenceRefresh();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onDestroy();
    }

    private void atualizaAdapterContatos() {

        String[] from = {HMContato.DEVICE, HMContato.EMAIL, HMContato.PRESENCE, HMContato.DEVICE_LOOKUP};
        int[] to = {R.id.celula_tv_device, R.id.celula_tv_email, R.id.celula_tv_presence, R.id.celula_btn_identificar};

        SimpleAdapter adapter = new SimpleAdapter(context,
                listaContatosComPresenca(),
                R.layout.celula,
                from,
                to);
        adapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data, String textRepresentation) {
                if (view.getId() == R.id.celula_tv_email && view instanceof TextView) {
                    TextView emailView = (TextView) view;
                    String email = textRepresentation == null ? "" : textRepresentation.trim();
                    emailView.setVisibility(email.isEmpty() ? View.GONE : View.VISIBLE);
                    emailView.setText(email);
                    return true;
                }
                if (view.getId() == R.id.celula_tv_presence && view instanceof TextView) {
                    TextView presenceView = (TextView) view;
                    PresencePayload payload = PresencePayload.from(textRepresentation);
                    presenceView.setText(payload.label);
                    int colorResId = DevicePresence.STATE_ONLINE.equals(payload.state)
                            ? R.color.presenceOnline
                            : DevicePresence.STATE_OFFLINE.equals(payload.state)
                            ? R.color.presenceOffline
                            : R.color.presenceUnknown;
                    presenceView.setTextColor(ContextCompat.getColor(DroidListaContatos.this, colorResId));
                    return true;
                }
                if (view.getId() == R.id.celula_btn_identificar && view instanceof Button) {
                    Button lookupButton = (Button) view;
                    LookupPayload payload = LookupPayload.from(textRepresentation);
                    boolean showLookup = payload.isValid()
                            && DeviceNameResolver.shouldOfferLookup(payload.rawDevice);
                    lookupButton.setVisibility(showLookup ? View.VISIBLE : View.GONE);
                    lookupButton.setEnabled(showLookup);
                    lookupButton.setText(R.string.contact_identify_device);
                    lookupButton.setOnClickListener(null);
                    if (showLookup) {
                        lookupButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                identificarNomeAparelho(payload.id, payload.rawDevice, (Button) v);
                            }
                        });
                    }
                    return true;
                }
                return false;
            }
        });
        lv_contatos.setAdapter(adapter);
    }

    private ArrayList<HMContato> listaContatosComPresenca() {
        ArrayList<HMContato> contatos = persintencia.listaContatos(Methods.getIDDevice(context));
        for (HMContato item : contatos) {
            String id = item.get(HMContato.ID);
            String state = DevicePresence.getStatusState(this, id);
            String label = DevicePresence.getStatusText(this, id);
            item.put(HMContato.PRESENCE, state + "|" + label);
        }
        return contatos;
    }

    private void startPresenceRefresh() {
        presenceRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                atualizaAdapterContatos();
                presenceRefreshHandler.postDelayed(this, 15000);
            }
        };
        presenceRefreshHandler.postDelayed(presenceRefreshRunnable, 15000);
    }

    private void stopPresenceRefresh() {
        if (presenceRefreshHandler != null && presenceRefreshRunnable != null) {
            presenceRefreshHandler.removeCallbacks(presenceRefreshRunnable);
            presenceRefreshRunnable = null;
        }
    }

    private void identificarNomeAparelho(final String idContato, final String deviceRaw, final Button button) {
        button.setEnabled(false);
        button.setText(R.string.contact_identifying_device);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String deviceName = DeviceNameResolver.resolve(getApplicationContext(), deviceRaw);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!deviceName.isEmpty()) {
                            persintencia.AtualizarDeviceContato(idContato, deviceName);
                            Toast.makeText(DroidListaContatos.this,
                                    getString(R.string.contact_identified_device, deviceName),
                                    Toast.LENGTH_SHORT).show();
                            atualizaAdapterContatos();
                            return;
                        }

                        button.setEnabled(true);
                        button.setText(R.string.contact_identify_device);
                        Toast.makeText(DroidListaContatos.this,
                                R.string.contact_identify_not_found,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private static class LookupPayload {
        private final String id;
        private final String rawDevice;

        private LookupPayload(String id, String rawDevice) {
            this.id = id == null ? "" : id;
            this.rawDevice = rawDevice == null ? "" : rawDevice;
        }

        private static LookupPayload from(String payload) {
            if (payload == null) {
                return new LookupPayload("", "");
            }
            int separator = payload.indexOf('|');
            if (separator < 0) {
                return new LookupPayload("", "");
            }
            return new LookupPayload(
                    payload.substring(0, separator),
                    payload.substring(separator + 1));
        }

        private boolean isValid() {
            return !id.isEmpty() && !rawDevice.isEmpty();
        }
    }

    private static class PresencePayload {
        private final String state;
        private final String label;

        private PresencePayload(String state, String label) {
            this.state = state == null ? DevicePresence.STATE_UNKNOWN : state;
            this.label = label == null ? "" : label;
        }

        private static PresencePayload from(String payload) {
            if (payload == null) {
                return new PresencePayload(DevicePresence.STATE_UNKNOWN, "");
            }
            int separator = payload.indexOf('|');
            if (separator < 0) {
                return new PresencePayload(DevicePresence.STATE_UNKNOWN, payload);
            }
            return new PresencePayload(payload.substring(0, separator), payload.substring(separator + 1));
        }
    }


    public class ReceiverResponseListaContatos extends BroadcastReceiver
    {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constantes.RECEIVERPRESENCESTATUS.equals(intent.getAction())) {
                atualizaAdapterContatos();
                return;
            }
            String message = intent.getStringExtra(Constantes.MESSAGE);
            if ("refresh".equals(message))
            {
                atualizaAdapterContatos();
            }

        }
    }
}
