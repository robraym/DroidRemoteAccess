package com.droid.remoteaccess.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import com.droid.remoteaccess.dbase.Persintencia;
import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.Methods;

/**
 * Created by Robson on 03/02/2016.
 */

public class DroidNotification extends DroidBaseNotification {

    private boolean sentBroadcast = false;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) {
            return;
        }

        String msgNotification = getNotificationKitKat(sbn);
        Context context = getBaseContext();

        if (msgNotification != null && !msgNotification.isEmpty()) {
            Persintencia persintencia = new Persintencia(context);
            persintencia.InserirMensagens(Methods.getIDDevice(context), Methods.getEmail(context), msgNotification);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        sentBroadcast = false;
    }

    private void SendBroadCast(String msgNotification) {
        Intent mIntent = new Intent();
        mIntent.setAction(Constantes.CHAVERECEIVER);
        mIntent.addCategory(Intent.CATEGORY_DEFAULT);
        mIntent.putExtra(Constantes.CHAVERECEIVER, msgNotification);
        sendBroadcast(mIntent);
        sentBroadcast = true;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private String getNotificationKitKat(StatusBarNotification mStatusBarNotification) {
        Notification notification = mStatusBarNotification.getNotification();
        if (notification == null || notification.extras == null) {
            return "";
        }

        Bundle extras = notification.extras;
        CharSequence desc = extras.getCharSequence(Notification.EXTRA_TEXT); // / Description
        String msg = "";

        try {
            CharSequence[] descArray = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (descArray != null && descArray.length > 0 && descArray[descArray.length - 1] != null) {
                msg = descArray[descArray.length - 1].toString();
            }

        } catch (Exception ex) {

        }

        if (msg.isEmpty() && desc != null) {
            msg = desc.toString();
        }

        return msg;
    }

}
