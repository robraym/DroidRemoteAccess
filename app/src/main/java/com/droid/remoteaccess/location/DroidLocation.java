package com.droid.remoteaccess.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.HandlerThread;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.feature.Localizacao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by Robson on 30/03/2016.
 */
public class DroidLocation {

    private static final long LOCATION_WAIT_TIMEOUT_MS = 15000;

    public static Localizacao MyLocation(Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w(Constantes.TAG, "Location permission missing");
                return null;
            }

            if (lm == null) {
                return null;
            }

            Location location = requestFreshLocation(lm);
            if (location == null) {
                location = getBestLastKnownLocation(lm);
            }

            if (location != null) {
                Localizacao lc = new Localizacao();
                lc.setLatitude(location.getLatitude());
                lc.setLongitude(location.getLongitude());
                return lc;
            }
        } catch (Exception ex) {
            Log.d(Constantes.TAG, "Could not get location", ex);
        }
        return null;
    }

    private static Location requestFreshLocation(LocationManager lm) {
        final Location[] result = new Location[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final HandlerThread handlerThread = new HandlerThread("RemoteLocationRequest");
        LocationListener listener = null;

        try {
            List<String> providers = getEnabledProviders(lm);
            if (providers.isEmpty()) {
                Log.w(Constantes.TAG, "No enabled location providers");
                return null;
            }

            handlerThread.start();
            listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (isUsableLocation(location)) {
                        result[0] = location;
                        latch.countDown();
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };

            for (String provider : providers) {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, handlerThread.getLooper());
            }

            latch.await(LOCATION_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return result[0];
        } catch (SecurityException ex) {
            Log.w(Constantes.TAG, "Location permission denied during request", ex);
            return null;
        } catch (Exception ex) {
            Log.d(Constantes.TAG, "Fresh location request failed", ex);
            return null;
        } finally {
            if (listener != null) {
                try {
                    lm.removeUpdates(listener);
                } catch (Exception ignored) {
                }
            }
            handlerThread.quitSafely();
        }
    }

    private static Location getBestLastKnownLocation(LocationManager lm) {
        Location bestLocation = null;
        for (String provider : getEnabledProviders(lm)) {
            try {
                Location location = lm.getLastKnownLocation(provider);
                if (isBetterLocation(location, bestLocation)) {
                    bestLocation = location;
                }
            } catch (SecurityException ex) {
                Log.w(Constantes.TAG, "Location permission denied for provider " + provider, ex);
            }
        }
        return isUsableLocation(bestLocation) ? bestLocation : null;
    }

    private static List<String> getEnabledProviders(LocationManager lm) {
        List<String> providers = new ArrayList<>();
        addProviderIfEnabled(lm, providers, LocationManager.GPS_PROVIDER);
        addProviderIfEnabled(lm, providers, LocationManager.NETWORK_PROVIDER);
        addProviderIfEnabled(lm, providers, LocationManager.PASSIVE_PROVIDER);
        return providers;
    }

    private static void addProviderIfEnabled(LocationManager lm, List<String> providers, String provider) {
        try {
            if (lm.isProviderEnabled(provider)) {
                providers.add(provider);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isBetterLocation(Location location, Location currentBest) {
        if (!isUsableLocation(location)) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        if (location.hasAccuracy() && currentBest.hasAccuracy()
                && location.getAccuracy() < currentBest.getAccuracy()) {
            return true;
        }
        return location.getTime() > currentBest.getTime();
    }

    private static boolean isUsableLocation(Location location) {
        if (location == null) {
            return false;
        }
        return location.getLatitude() != 0.0 || location.getLongitude() != 0.0;
    }
}
