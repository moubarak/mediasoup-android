package com.example.rtcclient.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.example.rtcclient.Application;

/**
 * URL library
 */
public class API {

    /**
     * Android emulator uses 10.0.2.2 as a host loopback interface
     */
    private static final String loopback = "http://10.0.2.2";
    private static final String port = "3000";

    /**
     * How to detect running on emulator from StackOverflow https://stackoverflow.com/a/21505193
     */
    private static final boolean isEmulator = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.PRODUCT.contains("sdk_google")
            || Build.PRODUCT.contains("google_sdk")
            || Build.PRODUCT.contains("sdk")
            || Build.PRODUCT.contains("sdk_x86")
            || Build.PRODUCT.contains("vbox86p")
            || Build.PRODUCT.contains("emulator")
            || Build.PRODUCT.contains("simulator");

    /**
     * Could use a Builder pattern
     */
    public String api = isEmulator ? (loopback + ":" + port + "/signaling/") : (getHost() + ":" + port + "/signaling/");

    public static String getHost() {
        return "http://" + PreferenceManager.getDefaultSharedPreferences(Application.context.getApplicationContext()).getString("serverIP", "192.168.8.101") + ":" + port + "/signaling/";
    }
}
