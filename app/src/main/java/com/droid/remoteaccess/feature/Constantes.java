package com.droid.remoteaccess.feature;

import com.droid.remoteaccess.BuildConfig;

/**
 * Created by nalmir on 19/12/2015.
 */
public class Constantes {

    public static final String TAG = "DRA";
    public static final String API_KEY = BuildConfig.GCM_API_KEY;
    public static final String SENDER_ID = BuildConfig.GCM_SENDER_ID;
    public static final String ID_TO = "id_to";
    public static final String ID_FROM = "id_from";
    public static final String EMAIL_TO = "email_to";
    public static final String EMAIL_FROM = "email_from";
    public static final String TOKEN_FROM = "token_from";
    public static final String DEVICE_FROM = "device_from";
    public static final String MESSAGE = "message";
    public static final String PARAMETRO_ID = "paremetro_id";
    public static final String SENT_TOKEN_TO_SERVER = "sentTokenToServer";
    public static final String REGISTRATION_COMPLETE = "registrationComplete";
    public static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static final String RECEIVERRESPONSELISTACONTATOS = "receiverResponseListaContatos";
    public static final String RECEIVERRESPONSECONTROLEREMOTO = "receiverResponseControleRemoto";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";



    public static enum EnumTypeViewCam
    {
        FacingBack,
        FacingFront
    }

    public enum EnumStateRecVideo {
        CLOSE,
        STOP,
        VIEW,
        RECORD
    }

    public static final String CHAMADAPELOSERVICO = "chamadaPeloServico";
    public static final String CHAMADAPORCOMANDOTEXTO = "chamadaPorComandoDeTexto";
    public static final String CHAVERECEIVER = "DVRREC";
    public static final String COMANDOINICIADOPOR = "DVR=";
    public static final String PASTADOSARQUIVOSGRAVADOS = "/DroidVideoRecorder/";



}
