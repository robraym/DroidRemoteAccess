package com.droid.remoteaccess.feature;

import java.util.HashMap;

/**
 * Created by nalmir on 19/12/2015.
 */
public class HMContato extends HashMap<String,String> {

    public static final String ID = "id";
    public static final String EMAIL = "email";
    public static final String DEVICE = "device";
    public static final String DEVICE_RAW = "device_raw";
    public static final String DEVICE_LOOKUP = "device_lookup";
    public static final String PRESENCE = "presence";
    public static final String AVATAR = "avatar";


    @Override
    public String toString() {
        return get(ID);
    }
}
