package com.moldyhub.clonehub.shell;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Compatibility shell entry point; dynamic shell generation is a later milestone. */
public class ShellActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent launch = new Intent();
        launch.setClassName("com.moldyhub.clonehub", "io.virtualapp.home.ShellLaunchActivity");
        launch.putExtra("io.va.extra.SHELL_TARGET_PACKAGE", BuildConfig.TARGET_PACKAGE);
        launch.putExtra("io.va.extra.SHELL_USER_ID", BuildConfig.TARGET_USER_ID);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
        } catch (Throwable error) {
            Toast.makeText(this, "Open CloneHub first: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
