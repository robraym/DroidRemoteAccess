package com.droid.remoteaccess.dbase;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

import com.droid.remoteaccess.feature.Contato;
import com.droid.remoteaccess.feature.HMContato;
import com.droid.remoteaccess.others.Methods;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by nalmir on 19/12/2015.
 */
public class Persintencia extends SQLiteOpenHelper {

    //public static final String BANCO = "/storage/extSdCard/BancoDados/contatosdbase.db3";
    public static final String BANCO = GetPathStorage() + "remoteAccess.db3";

    public static final int VERSAO = 31;
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

    /* Checks if external storage is available for read and write */
    public static boolean isExternalStorageMediaMounted() {
        return (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()));
    }

    public static String GetPathStorage() {
        String strSDCardPath = "";
        String strDirectory = "";
        String strPaste = "/DBase/";
        try {
            if (isExternalStorageMediaMounted()) {
                strSDCardPath = System.getenv("SECONDARY_STORAGE");
                if ((null == strSDCardPath) || (strSDCardPath.length() == 0)) {
                    strSDCardPath = System.getenv("EXTERNAL_SDCARD_STORAGE");
                }
                strDirectory = CreateGetDirectory(strSDCardPath + strPaste);
            }
        } catch (Exception e) {
        } finally {
            if (strSDCardPath == "" || strDirectory == "") {
                strSDCardPath = Environment.getExternalStorageDirectory().toString();
                strDirectory = CreateGetDirectory(strSDCardPath + strPaste);
            }
        }
        return strDirectory;
    }

    private static void TimeSleep(Integer seg) {
        try {
            Thread.sleep(seg);
        } catch (Exception ex) {
        }
    }

    public static String CreateGetDirectory(String pathStorage) {
        String pathDirectory = "";

        try {

            File myNewFolder = new File(pathStorage);

            if (!myNewFolder.exists()) {
                myNewFolder.mkdir();
                TimeSleep(1000);
            }
            if (myNewFolder.exists()) {
                pathDirectory = pathStorage;
            }
        } catch (Exception e) {

        }
        return pathDirectory;

    }

    private void CreateTabelaContatos(SQLiteDatabase db)
    {
        StringBuilder stringBuilder = new StringBuilder();       //


        stringBuilder.append("CREATE TABLE IF NOT EXISTS [" + CONTATOS + "] (\n" +
                "  [id] CHAR(20) NOT NULL, \n" +
                "  [email] CHAR(100) NOT NULL, \n" +
                "  [token] CHAR(200) NOT NULL, \n" +
                "  [device] CHAR(100));\n" +
                "  CONSTRAINT [] PRIMARY KEY ([id])); ");
        //
        db.execSQL(stringBuilder.toString());
    }

    private void CreateTabelaMensagens(SQLiteDatabase db) {

        StringBuilder stringBuilder = new StringBuilder();       //

        stringBuilder.append("CREATE TABLE IF NOT EXISTS [" + MENSAGENS + "] (\n" +
                "  [id] CHAR(20) NOT NULL, \n" +
                "  [email] CHAR(100) NOT NULL, \n" +
                "  [mensagem] CHAR(1024));\n" +
                "  CONSTRAINT [] FOREIGN KEY ([id])); ");
        //
        db.execSQL(stringBuilder.toString());
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
        cv.put(DEVICE, contato.getDevice());
        //
        getWritableDatabase().update(CONTATOS, cv, FILTRO, argumentos);
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
                item.put(HMContato.ID, cursor.getString(cursor.getColumnIndex(ID)));
                item.put(HMContato.EMAIL, Methods.formatEmailForDisplay(cursor.getString(cursor.getColumnIndex(EMAIL))));
                item.put(HMContato.DEVICE, Methods.formatDeviceName(cursor.getString(cursor.getColumnIndex(DEVICE))));
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
