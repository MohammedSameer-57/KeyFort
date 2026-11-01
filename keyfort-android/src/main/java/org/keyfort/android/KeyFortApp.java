package org.keyfort.android;

import android.app.Application;

public class KeyFortApp extends Application {

    private static KeyFortApp instance;
    private AppSession appSession;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        appSession = new AppSession();
    }

    public static KeyFortApp getInstance() {
        return instance;
    }

    public AppSession getAppSession() {
        return appSession;
    }
}
