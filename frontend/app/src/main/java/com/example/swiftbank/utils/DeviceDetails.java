package com.example.swiftbank.utils;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

public class DeviceDetails {

    public static String getDeviceId(Context context) {

        return Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
    }

    public static String getDeviceModel() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;

        if(model.toLowerCase().equals(manufacturer.toLowerCase()))
            return capitalize(model);
        else
            return capitalize(manufacturer) + " " + model;
    }

    public static String capitalize(String s) {
        if(s == null || s.isEmpty())
            return "";

        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
