package com.droid.remoteaccess.activitys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import com.droid.remoteaccess.services.BrokerMessaging;
import com.droid.remoteaccess.services.BrokerSyncService;
import com.droid.remoteaccess.services.FirebaseRemoteTransport;
import com.droid.remoteaccess.services.LocalDiscovery;
import com.droid.remoteaccess.services.RegistrationIntentService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Created by Robson on 06/03/2016.
 */
public class DroidListaContatos extends AppCompatActivity {

    private static final String TAG = "DroidListaContatos";
    private static final long DISCOVERY_REQUEST_INTERVAL_MS = 30000;

    private Context context;
    private ListView lv_contatos;
    private Persintencia persintencia;
    private ReceiverResponseListaContatos receiver;
    private Handler presenceRefreshHandler;
    private Runnable presenceRefreshRunnable;
    private long lastDiscoveryRequestAt;
    private volatile boolean discoveryRequestInFlight;

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
        requestDeviceDiscovery(true);
        scheduleInitialDiscoveryRetry(5000);
        scheduleInitialDiscoveryRetry(15000);
        startPresenceRefresh();

    }

    @Override
    protected void onResume() {
        super.onResume();
        requestDeviceDiscovery(false);
        atualizaAdapterContatos();
    }

    @Override
    protected void onDestroy() {
        stopPresenceRefresh();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onDestroy();
    }

    private void atualizaAdapterContatos() {

        String[] from = {HMContato.AVATAR, HMContato.DEVICE, HMContato.EMAIL, HMContato.PRESENCE, HMContato.DEVICE_LOOKUP};
        int[] to = {R.id.celula_tv_icon, R.id.celula_tv_device, R.id.celula_tv_email, R.id.celula_tv_presence, R.id.celula_btn_identificar};

        SimpleAdapter adapter = new SimpleAdapter(context,
                listaContatosComPresenca(),
                R.layout.celula,
                from,
                to);
        adapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data, String textRepresentation) {
                if (view.getId() == R.id.celula_tv_icon && view instanceof TextView) {
                    TextView iconView = (TextView) view;
                    String avatar = textRepresentation == null ? "" : textRepresentation.trim();
                    iconView.setText(avatar.isEmpty() ? "AN" : avatar);
                    iconView.setTextSize(avatar.length() > 3 ? 11 : 12);
                    return true;
                }
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
        String currentId = Methods.getIDDevice(context);
        String currentDevice = Methods.getNameDevice(context);
        ArrayList<HMContato> contatos = persintencia.listaContatos(currentId);
        Map<String, HMContato> contatosPorDevice = new LinkedHashMap<>();
        ArrayList<HMContato> contatosSemDeviceConfiavel = new ArrayList<>();
        for (HMContato item : contatos) {
            String id = item.get(HMContato.ID);
            if (isCurrentDeviceAlias(item, currentId, currentDevice)) {
                persintencia.ApagarContato(id);
                continue;
            }

            String deviceKey = normalizeDeviceForComparison(
                    firstNotEmpty(item.get(HMContato.DEVICE), item.get(HMContato.DEVICE_RAW)));
            if (!isReliableDeviceName(deviceKey)) {
                contatosSemDeviceConfiavel.add(item);
                continue;
            }

            HMContato existente = contatosPorDevice.get(deviceKey);
            if (existente == null) {
                contatosPorDevice.put(deviceKey, item);
                continue;
            }

            HMContato preferido = escolherContatoMaisRecente(existente, item);
            HMContato duplicado = preferido == existente ? item : existente;
            persintencia.ApagarContato(duplicado.get(HMContato.ID));
            contatosPorDevice.put(deviceKey, preferido);
        }

        ArrayList<HMContato> contatosVisiveis = new ArrayList<>();
        contatosVisiveis.addAll(contatosPorDevice.values());
        contatosVisiveis.addAll(contatosSemDeviceConfiavel);
        for (HMContato item : contatosVisiveis) {
            String id = item.get(HMContato.ID);
            String state = DevicePresence.getStatusState(this, id);
            String label = DevicePresence.getStatusText(this, id);
            item.put(HMContato.PRESENCE, state + "|" + label);
            item.put(HMContato.AVATAR, buildDeviceAvatar(item.get(HMContato.DEVICE), item.get(HMContato.DEVICE_RAW)));
        }
        return contatosVisiveis;
    }

    private HMContato escolherContatoMaisRecente(HMContato primeiro, HMContato segundo) {
        long primeiroLastSeen = DevicePresence.getLastSeenTime(this, primeiro.get(HMContato.ID));
        long segundoLastSeen = DevicePresence.getLastSeenTime(this, segundo.get(HMContato.ID));
        if (segundoLastSeen > primeiroLastSeen) {
            return segundo;
        }
        if (primeiroLastSeen > segundoLastSeen) {
            return primeiro;
        }

        String primeiroState = DevicePresence.getStatusState(this, primeiro.get(HMContato.ID));
        String segundoState = DevicePresence.getStatusState(this, segundo.get(HMContato.ID));
        return getPresencePriority(segundoState) > getPresencePriority(primeiroState) ? segundo : primeiro;
    }

    private int getPresencePriority(String state) {
        if (DevicePresence.STATE_ONLINE.equals(state)) {
            return 3;
        }
        if (DevicePresence.STATE_OFFLINE.equals(state)) {
            return 2;
        }
        return 1;
    }

    private boolean isCurrentDeviceAlias(HMContato item, String currentId, String currentDevice) {
        if (item == null) {
            return false;
        }
        String id = item.get(HMContato.ID);
        if (id != null && id.equals(currentId)) {
            return true;
        }

        String contactDevice = firstNotEmpty(item.get(HMContato.DEVICE), item.get(HMContato.DEVICE_RAW));
        String contactNormalized = normalizeDeviceForComparison(contactDevice);
        String currentNormalized = normalizeDeviceForComparison(currentDevice);
        return isReliableDeviceName(contactNormalized) && contactNormalized.equals(currentNormalized);
    }

    private String normalizeDeviceForComparison(String value) {
        if (value == null) {
            return "";
        }
        return Methods.formatDeviceName(value)
                .toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]+", "");
    }

    private boolean isReliableDeviceName(String normalizedDevice) {
        return normalizedDevice != null
                && !normalizedDevice.isEmpty()
                && !"APARELHOANDROID".equals(normalizedDevice);
    }

    private String buildDeviceAvatar(String deviceName, String rawDevice) {
        String name = firstNotEmpty(deviceName, rawDevice);
        String normalized = normalizeAvatarSource(name);
        String galaxyBadge = buildGalaxyBadge(normalized);
        if (!galaxyBadge.isEmpty()) {
            return galaxyBadge;
        }

        String genericBadge = buildGenericBadge(normalized);
        return genericBadge.isEmpty() ? "AN" : genericBadge;
    }

    private String firstNotEmpty(String preferred, String fallback) {
        if (preferred != null && !preferred.trim().isEmpty()) {
            return preferred;
        }
        return fallback == null ? "" : fallback;
    }

    private String normalizeAvatarSource(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase(Locale.US)
                .replace("+", " PLUS ")
                .replaceAll("[^A-Z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildGalaxyBadge(String normalized) {
        if (normalized.isEmpty()) {
            return "";
        }
        String cleaned = normalized
                .replace("SAMSUNG", "")
                .replace("GALAXY", "")
                .trim();
        String[] tokens = cleaned.split("\\s+");

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String next = i + 1 < tokens.length ? tokens[i + 1] : "";
            if (token.matches("S\\d{1,3}")) {
                return limitAvatar(token + getVariantSuffix(next));
            }
            if (token.matches("NOTE\\d{1,3}")) {
                return limitAvatar("N" + token.replace("NOTE", "") + getVariantSuffix(next));
            }
            if (token.startsWith("FOLD")) {
                return limitAvatar("ZF" + trailingDigits(token));
            }
            if (token.startsWith("FLIP")) {
                return limitAvatar("ZP" + trailingDigits(token));
            }
        }
        return "";
    }

    private String getVariantSuffix(String token) {
        if ("ULTRA".equals(token)) {
            return "U";
        }
        if ("EDGE".equals(token)) {
            return "E";
        }
        if ("PLUS".equals(token)) {
            return "+";
        }
        return "";
    }

    private String trailingDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D+", "");
    }

    private String buildGenericBadge(String normalized) {
        if (normalized.isEmpty()) {
            return "";
        }
        String[] tokens = normalized.split("\\s+");
        StringBuilder badge = new StringBuilder();
        for (String token : tokens) {
            if (token.isEmpty() || "SAMSUNG".equals(token) || "GALAXY".equals(token)) {
                continue;
            }
            badge.append(token.charAt(0));
            if (badge.length() >= 2) {
                break;
            }
        }
        return limitAvatar(badge.toString());
    }

    private String limitAvatar(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() > 4 ? value.substring(0, 4) : value;
    }

    private void startPresenceRefresh() {
        presenceRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                requestDeviceDiscovery(false);
                atualizaAdapterContatos();
                presenceRefreshHandler.postDelayed(this, 15000);
            }
        };
        presenceRefreshHandler.postDelayed(presenceRefreshRunnable, 15000);
    }

    private void scheduleInitialDiscoveryRetry(long delayMs) {
        presenceRefreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                requestDeviceDiscovery(true);
            }
        }, delayMs);
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

    private void requestDeviceDiscovery(boolean force) {
        long now = System.currentTimeMillis();
        if (discoveryRequestInFlight) {
            return;
        }
        if (!force && now - lastDiscoveryRequestAt < DISCOVERY_REQUEST_INTERVAL_MS) {
            return;
        }
        lastDiscoveryRequestAt = now;
        discoveryRequestInFlight = true;
        final String requestId = Methods.getIDDevice(getApplicationContext()) + "_discover_" + now;
        FirebaseRemoteTransport.registerDeviceAsync(getApplicationContext());
        LocalDiscovery.sendDiscoveryRequestAsync(getApplicationContext());

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BrokerMessaging.publishDiscoveryRequest(getApplicationContext(), requestId);
                    Log.i(TAG, "Busca de dispositivos enviada: " + requestId);
                } catch (Exception ex) {
                    Log.d(TAG, "Falha ao buscar dispositivos online", ex);
                } finally {
                    discoveryRequestInFlight = false;
                }
            }
        }, "DeviceDiscoveryRequest").start();
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
