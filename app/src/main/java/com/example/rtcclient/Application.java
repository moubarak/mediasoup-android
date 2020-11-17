package com.example.rtcclient;

import android.content.Context;

import org.mediasoup.droid.Logger;
import org.mediasoup.droid.MediasoupClient;

public class Application extends android.app.Application {

    public static Context context;

    @Override
    public void onCreate() {
        super.onCreate();

        /**
         * TODO (mohamed): Find a better way to expose the application level context
         */
        context = getApplicationContext();

        Logger.setLogLevel(Logger.LogLevel.LOG_DEBUG);
        Logger.setDefaultHandler();

        MediasoupClient.initialize(context);
    }
}
