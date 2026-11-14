package io.virtualapp;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.stub.VASettings;

import java.io.File;
import java.io.IOException;

import io.virtualapp.delegate.MyVirtualInitializer;
import io.virtualapp.diagnostics.CloneHubDebugLog;

/**
 * @author Lody
 */
public class XApp extends Application {

    private static final String TAG = "XApp";

    public static final String XPOSED_INSTALLER_PACKAGE = "de.robv.android.xposed.installer";

    private static XApp gApp;

    public static XApp getApp() {
        return gApp;
    }

    @Override
    protected void attachBaseContext(Context base) {
        gApp = this;
        super.attachBaseContext(base);
        CloneHubDebugLog.log(base, "Application.attachBaseContext");
        VASettings.ENABLE_IO_REDIRECT = true;
        VASettings.ENABLE_INNER_SHORTCUT = false;
        try {
            CloneHubDebugLog.log(base, "VirtualCore.startup begin");
            VirtualCore.get().startup(base);
            CloneHubDebugLog.log(base, "VirtualCore.startup complete");
        } catch (Throwable e) {
            CloneHubDebugLog.log(base, "VirtualCore.startup failed", e);
            e.printStackTrace();
        }
        // On Android 14+ (API 34+), auto-disable Xposed on first launch since
        // Epic/SandHook may be incompatible. Users can re-enable from Settings.
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                File disableXposedFile = base.getFileStreamPath(".disable_xposed");
                File xposedApiChecked = base.getFileStreamPath(".xposed_api34_checked");
                if (!xposedApiChecked.exists()) {
                    if (!disableXposedFile.exists()) {
                        disableXposedFile.createNewFile();
                        Log.i(TAG, "Auto-disabled Xposed on API " + Build.VERSION.SDK_INT);
                    }
                    xposedApiChecked.createNewFile();
                }
            } catch (IOException e) {
                CloneHubDebugLog.log(base, "Failed to auto-configure Xposed settings", e);
                Log.w(TAG, "Failed to auto-configure Xposed settings", e);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CloneHubDebugLog.initialize(this);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            CloneHubDebugLog.log(this, "UNCAUGHT EXCEPTION on thread " + thread.getName(), error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
        VirtualCore virtualCore = VirtualCore.get();
        try {
            CloneHubDebugLog.log(this, "VirtualCore.initialize begin");
            virtualCore.initialize(new MyVirtualInitializer(this, virtualCore));
            CloneHubDebugLog.log(this, "VirtualCore.initialize complete");
        } catch (Throwable error) {
            CloneHubDebugLog.log(this, "VirtualCore.initialize failed", error);
            throw error;
        }
    }
}
