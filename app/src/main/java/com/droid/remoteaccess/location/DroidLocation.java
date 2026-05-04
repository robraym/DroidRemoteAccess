package com.droid.remoteaccess.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.feature.Localizacao;

/**
 * Created by Robson on 30/03/2016.
 */
public class DroidLocation  {

    public static Localizacao MyLocation(Context context)
    {
        Localizacao lc = new Localizacao();
        try {

            LocationManager lm = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return lc;
            }
            Location location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location == null) {
                location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (location != null) {
                lc.setLatitude(location.getLatitude());
                lc.setLongitude(location.getLongitude());
            }
        }
        catch (Exception ex)
        {
            Log.d(Constantes.TAG, ex.getMessage());
        }
        return lc;
    }




}
