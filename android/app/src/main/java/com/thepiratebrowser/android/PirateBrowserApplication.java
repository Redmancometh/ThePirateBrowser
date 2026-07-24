package com.thepiratebrowser.android;

import android.app.Application;

public final class PirateBrowserApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
