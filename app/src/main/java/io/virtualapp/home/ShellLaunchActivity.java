package io.virtualapp.home;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** Entry point used by separately installed CloneHub shell APKs. */
public class ShellLaunchActivity extends Activity {
    public static final String EXTRA_TARGET_PACKAGE = "io.va.extra.SHELL_TARGET_PACKAGE";
    public static final String EXTRA_USER_ID = "io.va.extra.SHELL_USER_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        int userId = getIntent().getIntExtra(EXTRA_USER_ID, 0);
        if (targetPackage == null || targetPackage.trim().isEmpty()) {
            Toast.makeText(this, "This shell has no target app configured.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            if (!LoadingActivity.launch(this, targetPackage, userId)) {
                Toast.makeText(this, "Clone " + targetPackage + " in CloneHub first.", Toast.LENGTH_LONG).show();
                NewHomeActivity.goHome(this);
            }
        } catch (Throwable error) {
            Toast.makeText(this, "Unable to open clone: " + error.getMessage(), Toast.LENGTH_LONG).show();
            NewHomeActivity.goHome(this);
        }
        finish();
    }
}
