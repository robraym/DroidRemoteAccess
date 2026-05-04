package com.droid.remoteaccess.location;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

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
            Location location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            lc.setLatitude(location.getLatitude());
            lc.setLongitude(location.getLongitude());
        }
        catch (Exception ex)
        {
            Log.d(Constantes.TAG, ex.getMessage());
        }
        return lc;
    }




}
