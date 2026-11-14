package io.virtualapp.home;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import io.virtualapp.diagnostics.CloneHubDebugLog;

/** Entry point used by separately installed CloneHub shell APKs. */
public class ShellLaunchActivity extends Activity {
    public static final String EXTRA_TARGET_PACKAGE = "io.va.extra.SHELL_TARGET_PACKAGE";
    public static final String EXTRA_USER_ID = "io.va.extra.SHELL_USER_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        int userId = getIntent().getIntExtra(EXTRA_USER_ID, 0);
        CloneHubDebugLog.log(this, "ShellLaunchActivity target=" + targetPackage + " userId=" + userId);
        if (targetPackage == null || targetPackage.trim().isEmpty()) {
            CloneHubDebugLog.log(this, "Shell launch rejected: target package is empty");
            Toast.makeText(this, "This shell has no target app configured.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            boolean launched = LoadingActivity.launch(this, targetPackage, userId);
            CloneHubDebugLog.log(this, "Shell launch delegated to LoadingActivity result=" + launched);
            if (!launched) {
                Toast.makeText(this, "Clone " + targetPackage + " in CloneHub first.", Toast.LENGTH_LONG).show();
                NewHomeActivity.goHome(this);
            }
        } catch (Throwable error) {
            CloneHubDebugLog.log(this, "Shell launch failed for " + targetPackage, error);
            Toast.makeText(this, "Unable to open clone: " + error.getMessage(), Toast.LENGTH_LONG).show();
            NewHomeActivity.goHome(this);
        }
        finish();
    }
}
