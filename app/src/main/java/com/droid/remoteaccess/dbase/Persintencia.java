package com.droid.remoteaccess.dbase;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.droid.remoteaccess.feature.Contato;
import com.droid.remoteaccess.feature.HMContato;
import com.droid.remoteaccess.others.DeviceNameResolver;
import com.droid.remoteaccess.others.Methods;

import java.util.ArrayList;

/**
 * Created by nalmir on 19/12/2015.
 */
public class Persintencia extends SQLiteOpenHelper {

    public static final String BANCO = "remoteAccess.db3";

    public static final int VERSAO = 32;
    //
    public static final String CONTATOS = "contatos";
    public static final String MENSAGENS = "mensagens";

    public static final String ID = "id";
    public static final String EMAIL = "email";
    public static final String TOKEN = "token";
    public static final String DEVICE = "device";
    public static final String MENSAGEM = "mensagem";

    public Persintencia(Context context) {
        super(context, BANCO, null, VERSAO);
    }

    private void CreateTabelaContatos(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + CONTATOS + " ("
                + ID + " TEXT PRIMARY KEY NOT NULL, "
                + EMAIL + " TEXT NOT NULL, "
                + TOKEN + " TEXT NOT NULL, "
                + DEVICE + " TEXT);");
    }

    private void CreateTabelaMensagens(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + MENSAGENS + " ("
                + ID + " TEXT NOT NULL, "
                + EMAIL + " TEXT NOT NULL, "
                + MENSAGEM + " TEXT);");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        CreateTabelaContatos(db);
        CreateTabelaMensagens(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + CONTATOS + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MENSAGENS + ";");
        //
        onCreate(db);
    }

    public void InserirContato(Contato contato) {
        ContentValues cv = new ContentValues();
        //
        cv.put(ID, contato.getId());
        cv.put(EMAIL, contato.getEmail());
        cv.put(TOKEN, contato.getToken());
        cv.put(EMAIL, contato.getEmail());
        cv.put(DEVICE, contato.getDevice());
        //
        getWritableDatabase().insert(CONTATOS, null, cv);
    }


    private boolean JaExisteMensagem(String id, String mensagem)
    {
        boolean cadastrado = false;
        //
        Cursor cursor = null;
        //
        try {
            String[] argumentos = new String[]{id, mensagem};
            //
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM " + MENSAGENS + " WHERE id = ? AND mensagem = ? ");
            //
            cursor = getWritableDatabase().rawQuery(sb.toString(), argumentos);
            //

            cadastrado = cursor.getCount() > 0;

        } catch (Exception e) {
            Log.d("DBase", e.getMessage());

        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        //
        return cadastrado;

    }

    public void InserirMensagens (String id, String email, String mensagem) {

        if (!JaExisteMensagem(id, mensagem)) {
            ContentValues cv = new ContentValues();
            //
            cv.put(ID, id);
            cv.put(EMAIL, email);
            cv.put(MENSAGEM, mensagem);
            //
            getWritableDatabase().insert(MENSAGENS, null, cv);
        }
    }


    public boolean JaExisteContatoCadastrado(String id) {
        boolean cadastrado = false;
        //
        Cursor cursor = null;
        //
        try {
            String[] argumentos = new String[]{id};
            //
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM " + CONTATOS + " WHERE id = ?");
            //
            cursor = getWritableDatabase().rawQuery(sb.toString(), argumentos);
            //

            cadastrado = cursor.getCount() > 0;

        } catch (Exception e) {
            Log.d("DBase", e.getMessage());

        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        //
        return cadastrado;
    }

    public void AtualizarContato(Contato contato) {
        ContentValues cv = new ContentValues();
        //
        String[] argumentos = new String[]{contato.getId()};
        String FILTRO = ID + " = ?";
        //
        cv.put(EMAIL, contato.getEmail());
        cv.put(TOKEN, contato.getToken());
        cv.put(DEVICE, selecionarNomeDeviceParaAtualizacao(contato));
        //
        getWritableDatabase().update(CONTATOS, cv, FILTRO, argumentos);
    }

    public void AtualizarDeviceContato(String id, String device) {
        if (id == null || id.isEmpty() || device == null || device.isEmpty()) {
            return;
        }
        ContentValues cv = new ContentValues();
        String[] argumentos = new String[]{id};
        String FILTRO = ID + " = ?";
        cv.put(DEVICE, device);
        getWritableDatabase().update(CONTATOS, cv, FILTRO, argumentos);
    }

    private String selecionarNomeDeviceParaAtualizacao(Contato contato) {
        String novoDevice = contato.getDevice();
        Contato contatoAtual = ObterContato(contato.getId());
        if (contatoAtual == null) {
            return novoDevice;
        }

        String deviceAtual = contatoAtual.getDevice();
        if (devePreservarNomeIdentificado(deviceAtual, novoDevice)) {
            return deviceAtual;
        }
        return novoDevice;
    }

    private boolean devePreservarNomeIdentificado(String deviceAtual, String novoDevice) {
        if (deviceAtual == null || novoDevice == null) {
            return false;
        }
        String atual = deviceAtual.trim();
        String novo = novoDevice.trim();
        if (atual.isEmpty() || novo.isEmpty() || atual.equalsIgnoreCase(novo)) {
            return false;
        }
        if (atual.equalsIgnoreCase("Aparelho Android")) {
            return false;
        }

        return !DeviceNameResolver.shouldOfferLookup(atual)
                && DeviceNameResolver.shouldOfferLookup(novo);
    }

    public void ApagarContatosMesmoDispositivo(String device, String idAtual) {
        if (device == null || device.isEmpty() || idAtual == null || idAtual.isEmpty()) {
            return;
        }
        String[] argumentos = new String[]{device, idAtual};
        String FILTRO = DEVICE + " = ? AND " + ID + " != ?";
        getWritableDatabase().delete(CONTATOS, FILTRO, argumentos);
    }

    public void ApagarContato(String id) {
        String[] argumentos = new String[]{id};
        String FILTRO = ID + " = ?";
        //
        getWritableDatabase().delete(CONTATOS, FILTRO, argumentos);
        getWritableDatabase().delete(MENSAGENS, FILTRO, argumentos);
    }

    public StringBuilder ObterMensagens(String id) {
        StringBuilder sAux = new StringBuilder();
        //
        Cursor cursor = null;
        //
        try {
            String[] argumentos = new String[]{id};
            //
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM " + MENSAGENS + " WHERE id = ?");
            //
            cursor = getWritableDatabase().rawQuery(sb.toString(), argumentos);
            //
            while (cursor.moveToNext()) {
                sAux.append(cursor.getString(cursor.getColumnIndex(MENSAGEM)));
                sAux.append("\n");
            }


        } catch (Exception e) {
            Log.d("DBase", e.getMessage());

        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        //
        return sAux;
    }


    public Contato ObterContato(String id) {
        Contato cAux = null;
        //
        Cursor cursor = null;
        //
        try {
            String[] argumentos = new String[]{id};
            //
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM " + CONTATOS + " WHERE id = ?");
            //
            cursor = getWritableDatabase().rawQuery(sb.toString(), argumentos);
            //
            while (cursor.moveToNext()) {
                cAux = new Contato();
                //
                cAux.setId(cursor.getString(cursor.getColumnIndex(ID)));
                cAux.setEmail(cursor.getString(cursor.getColumnIndex(EMAIL)));
                cAux.setToken(cursor.getString(cursor.getColumnIndex(TOKEN)));
                cAux.setDevice(cursor.getString(cursor.getColumnIndex(DEVICE)));
            }


        } catch (Exception e) {
            Log.d("DBase", e.getMessage());

        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        //
        return cAux;
    }

    public ArrayList<HMContato> listaContatos() {
        return listaContatos("");
    }

    public ArrayList<HMContato> listaContatos(String idIgnorado) {
        ArrayList<HMContato> contatos = new ArrayList<>();
        //
        Cursor cursor = null;
        //
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT " + ID + ","  + EMAIL + ","  +  DEVICE + " FROM " + CONTATOS);
            String[] argumentos = null;
            if (idIgnorado != null && !idIgnorado.isEmpty()) {
                sb.append(" WHERE " + ID + " != ?");
                argumentos = new String[]{idIgnorado};
            }
            sb.append(" ORDER BY " + DEVICE + "," + EMAIL);
            //
            cursor = getWritableDatabase().rawQuery(sb.toString(), argumentos);
            //
            while (cursor.moveToNext()) {
                HMContato item = new HMContato();
                //
                String id = cursor.getString(cursor.getColumnIndex(ID));
                String rawDevice = cursor.getString(cursor.getColumnIndex(DEVICE));
                item.put(HMContato.ID, id);
                item.put(HMContato.EMAIL, Methods.formatEmailForDisplay(cursor.getString(cursor.getColumnIndex(EMAIL))));
                item.put(HMContato.DEVICE, Methods.formatDeviceName(rawDevice));
                item.put(HMContato.DEVICE_RAW, rawDevice == null ? "" : rawDevice);
                item.put(HMContato.DEVICE_LOOKUP, id + "|" + (rawDevice == null ? "" : rawDevice));
                //
                contatos.add(item);
            }


        } catch (Exception e) {
            Log.d("DBase", e.getMessage());

        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        //
        return contatos;
    }


}
