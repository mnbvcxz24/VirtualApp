package io.virtualapp.home;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.env.Constants;
import com.lody.virtual.client.ipc.VActivityManager;
import com.lody.virtual.client.ipc.VPackageManager;
import com.lody.virtual.helper.utils.VLog;
import com.lody.virtual.server.pm.parser.VPackage;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.virtualapp.R;
import io.virtualapp.abs.ui.VActivity;
import io.virtualapp.abs.ui.VUiKit;
import io.virtualapp.diagnostics.CloneHubDebugLog;
import io.virtualapp.home.models.PackageAppData;
import io.virtualapp.home.repo.PackageAppDataStorage;
import io.virtualapp.widgets.EatBeansView;
import jonathanfinerty.once.Once;

/**
 * @author Lody
 */
public class LoadingActivity extends VActivity {

    private static final String TAG = "LoadingActivity";
    private PackageAppData appModel;
    private EatBeansView loadingView;
    private static final int REQUEST_PERMISSION_CODE = 100;
    private Intent intentToLaunch;
    private int userToLaunch;
    private long start;

    public static boolean launch(Context context, String packageName, int userId) {
        CloneHubDebugLog.log(context, "LoadingActivity.launch requested package=" + packageName + " userId=" + userId);
        try {
            Intent intent = VirtualCore.get().getLaunchIntent(packageName, userId);
            if (intent != null) {
                CloneHubDebugLog.log(context, "VirtualCore returned launch intent for " + packageName
                        + " component=" + intent.getComponent());
                Intent loadingPageIntent = new Intent(context, LoadingActivity.class);
                loadingPageIntent.putExtra(Constants.PASS_PKG_NAME_ARGUMENT, packageName);
                loadingPageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                loadingPageIntent.putExtra(Constants.PASS_KEY_INTENT, intent);
                loadingPageIntent.putExtra(Constants.PASS_KEY_USER, userId);
                context.startActivity(loadingPageIntent);
                CloneHubDebugLog.log(context, "LoadingActivity startActivity submitted for " + packageName);
                return true;
            }
            CloneHubDebugLog.log(context, "VirtualCore returned NULL launch intent for " + packageName);
            return false;
        } catch (Throwable error) {
            CloneHubDebugLog.log(context, "LoadingActivity.launch failed for " + packageName, error);
            throw error;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        start = SystemClock.elapsedRealtime();
        String pkg = getIntent().getStringExtra(Constants.PASS_PKG_NAME_ARGUMENT);
        int userId = getIntent().getIntExtra(Constants.PASS_KEY_USER, -1);
        CloneHubDebugLog.log(this, "LoadingActivity.onCreate package=" + pkg + " userId=" + userId
                + " intent=" + getIntent());

        setContentView(R.layout.activity_loading);
        loadingView = (EatBeansView) findViewById(R.id.loading_anim);
        appModel = PackageAppDataStorage.get().acquire(pkg);
        if (appModel == null) {
            CloneHubDebugLog.log(this, "PackageAppDataStorage returned NULL for " + pkg);
            Toast.makeText(getApplicationContext(), "Open App:" + pkg + " failed.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        CloneHubDebugLog.log(this, "Resolved virtual app name=" + appModel.name + " package=" + appModel.packageName);
        ImageView iconView = (ImageView) findViewById(R.id.app_icon);
        iconView.setImageDrawable(appModel.icon);
        TextView nameView = (TextView) findViewById(R.id.app_name);
        nameView.setText(String.format(Locale.ENGLISH, "Opening %s...", appModel.name));
        Intent intent = getIntent().getParcelableExtra(Constants.PASS_KEY_INTENT);
        if (intent == null) {
            CloneHubDebugLog.log(this, "LoadingActivity received NULL target launch intent");
            finish();
            return;
        }
        CloneHubDebugLog.log(this, "Target intent component=" + intent.getComponent() + " action=" + intent.getAction());
        VirtualCore.get().setUiCallback(intent, mUiCallback);

        try {
            boolean uiRunning = false;
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = am.getRunningAppProcesses();
                for (ActivityManager.RunningAppProcessInfo runningAppProcess : runningAppProcesses) {
                    String appProcessName = VActivityManager.get().getAppProcessName(runningAppProcess.pid);
                    if (TextUtils.equals(appProcessName, pkg)) {
                        uiRunning = true;
                        break;
                    }
                }
            }
            CloneHubDebugLog.log(this, "Virtual app process already running=" + uiRunning);
            VLog.i(TAG, pkg + "is running: " + uiRunning);
            if (uiRunning) {
                launchActivity(intent, userId);
                return;
            }
        } catch (Throwable error) {
            CloneHubDebugLog.log(this, "Failed while checking existing virtual process; continuing launch", error);
        }
        checkAndLaunch(intent, userId);
    }

    private void checkAndLaunch(Intent intent, int userId) {
        final int RUNTIME_PERMISSION_API_LEVEL = android.os.Build.VERSION_CODES.M;
        CloneHubDebugLog.log(this, "Permission check begin deviceApi=" + android.os.Build.VERSION.SDK_INT);
        if (android.os.Build.VERSION.SDK_INT < RUNTIME_PERMISSION_API_LEVEL) {
            CloneHubDebugLog.log(this, "Device below Android M; launching without runtime permission flow");
            launchActivityWithDelay(intent, userId);
            return;
        }

        String packageName = appModel.packageName;
        String name = appModel.name;
        try {
            ApplicationInfo applicationInfo = VPackageManager.get().getApplicationInfo(packageName, 0, 0);
            int targetSdkVersion = applicationInfo.targetSdkVersion;
            CloneHubDebugLog.log(this, "Virtual package ApplicationInfo resolved targetSdk=" + targetSdkVersion);
            Log.i(TAG, "target package: " + packageName + " targetSdkVersion: " + targetSdkVersion);
            if (targetSdkVersion >= RUNTIME_PERMISSION_API_LEVEL) {
                CloneHubDebugLog.log(this, "Target app supports runtime permissions; launch directly");
                launchActivityWithDelay(intent, userId);
            } else {
                intentToLaunch = intent;
                userToLaunch = userId;
                PackageInfo packageInfo = VPackageManager.get().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, 0);
                String[] requestedPermissions = packageInfo.requestedPermissions;
                Set<String> dangerousPermissions = new HashSet<>();
                if (requestedPermissions != null) {
                    for (String requestedPermission : requestedPermissions) {
                        if (VPackage.PermissionComponent.DANGEROUS_PERMISSION.contains(requestedPermission)) {
                            if (ContextCompat.checkSelfPermission(this, requestedPermission) != PackageManager.PERMISSION_GRANTED) {
                                dangerousPermissions.add(requestedPermission);
                            } else {
                                Log.i(TAG, "permission: " + requestedPermission + " is granted, ignore.");
                            }
                        }
                    }
                }
                CloneHubDebugLog.log(this, "Dangerous permissions still needing grant=" + dangerousPermissions);
                if (dangerousPermissions.isEmpty()) {
                    launchActivityWithDelay(intent, userId);
                } else {
                    AlertDialog alertDialog = new AlertDialog.Builder(this, R.style.Theme_AppCompat_DayNight_Dialog_Alert)
                            .setTitle(R.string.permission_tip_title)
                            .setMessage(getResources().getString(R.string.permission_tips_content, name))
                            .setPositiveButton(R.string.permission_tips_confirm, (dialog, which) -> {
                                String[] permissionToRequest = dangerousPermissions.toArray(new String[dangerousPermissions.size()]);
                                try {
                                    ActivityCompat.requestPermissions(this, permissionToRequest, REQUEST_PERMISSION_CODE);
                                } catch (Throwable error) {
                                    CloneHubDebugLog.log(this, "Runtime permission request failed", error);
                                }
                            }).create();
                    try {
                        alertDialog.show();
                    } catch (Throwable error) {
                        CloneHubDebugLog.log(this, "Could not show runtime permission dialog", error);
                        finish();
                        Toast.makeText(this, getResources().getString(R.string.start_app_failed, appModel.name), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Throwable error) {
            CloneHubDebugLog.log(this, "Permission analysis failed; falling back to launch", error);
            Log.e(TAG, "check permission failed: ", error);
            launchActivityWithDelay(intent, userId);
        }
    }

    private void launchActivityWithDelay(Intent intent, int userId) {
        final int MAX_WAIT = 1000;
        long delta = SystemClock.elapsedRealtime() - start;
        long waitTime = MAX_WAIT - delta;
        CloneHubDebugLog.log(this, "Launch delay calculated waitMs=" + Math.max(0, waitTime));
        if (waitTime <= 0) {
            launchActivity(intent, userId);
        } else {
            loadingView.postDelayed(() -> launchActivity(intent, userId), waitTime);
        }
    }

    private void launchActivity(Intent intent, int userId) {
        try {
            CloneHubDebugLog.log(this, "Calling VActivityManager.startActivity userId=" + userId
                    + " component=" + intent.getComponent());
            int result = VActivityManager.get().startActivity(intent, userId);
            CloneHubDebugLog.log(this, "VActivityManager.startActivity returned " + result);
        } catch (Throwable error) {
            CloneHubDebugLog.log(this, "VActivityManager.startActivity threw exception", error);
            VLog.e(TAG, "start activity failed:", error);
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.start_app_failed, appModel.name), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        CloneHubDebugLog.log(this, "onRequestPermissionsResult requestCode=" + requestCode
                + " permissions=" + java.util.Arrays.toString(permissions)
                + " results=" + java.util.Arrays.toString(grantResults));
        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (intentToLaunch == null) {
                    CloneHubDebugLog.log(this, "Permission callback had no saved target intent");
                    Toast.makeText(this, getResources().getString(R.string.start_app_failed, appModel.name), Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    launchActivityWithDelay(intentToLaunch, userToLaunch);
                }
            } else {
                CloneHubDebugLog.log(this, "One or more runtime permissions were denied");
                Log.i(TAG, "can not use runtime permission, you must grant all permission, otherwise the app may not work!");
                final String tag = "permission_tips_" + appModel.packageName.replaceAll("\\.", "_");
                if (!Once.beenDone(tag)) {
                    AlertDialog alertDialog = new AlertDialog.Builder(this, R.style.Theme_AppCompat_DayNight_Dialog_Alert)
                            .setTitle(android.R.string.dialog_alert_title)
                            .setMessage(getResources().getString(R.string.permission_denied_tips_content, appModel.name))
                            .setPositiveButton(R.string.permission_tips_confirm, (dialog, which) -> {
                                finish();
                                Once.markDone(tag);
                                launchActivityWithDelay(intentToLaunch, userToLaunch);
                            }).create();
                    try {
                        alertDialog.show();
                    } catch (Throwable error) {
                        CloneHubDebugLog.log(this, "Could not show permission-denied dialog", error);
                        Toast.makeText(this, getResources().getString(R.string.start_app_failed, appModel.name), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    launchActivityWithDelay(intentToLaunch, userToLaunch);
                    finish();
                }
            }
        }
    }

    private final VirtualCore.UiCallback mUiCallback = new VirtualCore.UiCallback() {
        @Override
        public void onAppOpened(String packageName, int userId) throws RemoteException {
            CloneHubDebugLog.log(LoadingActivity.this, "VirtualCore callback onAppOpened package=" + packageName + " userId=" + userId);
            finish();
        }

        @Override
        public void onOpenFailed(String packageName, int userId) throws RemoteException {
            CloneHubDebugLog.log(LoadingActivity.this, "VirtualCore callback onOpenFailed package=" + packageName + " userId=" + userId);
            VUiKit.defer().when(() -> { }).done((v) -> {
                if (!isFinishing()) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.start_app_failed, packageName),
                            Toast.LENGTH_SHORT).show();
                }
            });
            finish();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        startAnim();
    }

    private void startAnim() {
        if (loadingView != null) loadingView.startAnim();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (loadingView != null) loadingView.stopAnim();
    }
}
