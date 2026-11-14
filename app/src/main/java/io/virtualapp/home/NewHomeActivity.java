package io.virtualapp.home;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.lody.virtual.client.core.VirtualCore;

import io.virtualapp.R;
import io.virtualapp.VCommends;
import io.virtualapp.diagnostics.CloneHubDebugLog;
import io.virtualapp.home.adapters.HomeAppAdapter;
import io.virtualapp.home.models.AppData;
import io.virtualapp.home.models.PackageAppData;
import io.virtualapp.home.repo.AppRepository;
import io.virtualapp.settings.SettingsActivity;

public class NewHomeActivity extends AppCompatActivity {

    private static final String SHOW_DOZE_ALERT_KEY = "SHOW_DOZE_ALERT_KEY";
    private static final String TAG = NewHomeActivity.class.getSimpleName();
    private static final int REQUEST_EXPORT_DEBUG_PERMISSION = 731;
    private Handler mUiHandler;
    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private View mEmptyView;
    private FloatingActionButton mFab;
    private HomeAppAdapter mAdapter;
    private AppRepository mRepository;

    public static void goHome(Context context) {
        Intent intent = new Intent(context, NewHomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CloneHubDebugLog.log(this, "NewHomeActivity.onCreate");
        setContentView(R.layout.activity_home);
        mUiHandler = new Handler(getMainLooper());

        Toolbar toolbar = findViewById(R.id.home_toolbar);
        setSupportActionBar(toolbar);
        mRecyclerView = findViewById(R.id.home_recycler_view);
        mProgressBar = findViewById(R.id.home_progress_bar);
        mEmptyView = findViewById(R.id.home_empty_view);
        mFab = findViewById(R.id.home_fab_add);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
        layoutManager.setItemPrefetchEnabled(true);
        layoutManager.setInitialPrefetchItemCount(8);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setItemViewCacheSize(20);
        mRecyclerView.setHasFixedSize(true);

        mAdapter = new HomeAppAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setOnAppClickListener(new HomeAppAdapter.OnAppClickListener() {
            @Override
            public void onAppClick(AppData data, int position) {
                if (data instanceof PackageAppData) {
                    PackageAppData appData = (PackageAppData) data;
                    if (!appData.isLoading) {
                        appData.isLoading = true;
                        mAdapter.notifyItemChanged(position);
                        CloneHubDebugLog.log(NewHomeActivity.this, "Home app clicked package="
                                + appData.packageName + " name=" + appData.name + " position=" + position);
                        try {
                            boolean launched = LoadingActivity.launch(NewHomeActivity.this, appData.packageName, 0);
                            CloneHubDebugLog.log(NewHomeActivity.this, "LoadingActivity.launch result=" + launched
                                    + " package=" + appData.packageName);
                        } catch (Throwable error) {
                            CloneHubDebugLog.log(NewHomeActivity.this, "Home app launch threw for " + appData.packageName, error);
                            Log.e(TAG, "Unable to launch virtual app", error);
                        }
                        mUiHandler.postDelayed(() -> {
                            appData.isLoading = false;
                            mAdapter.notifyItemChanged(position);
                        }, 3000);
                    }
                }
            }

            @Override
            public void onAppLongClick(AppData data, int position) {
                if (data instanceof PackageAppData) showAppPopupMenu((PackageAppData) data, position);
            }
        });

        mFab.setOnClickListener(v -> ListAppActivity.gotoListApp(this));
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 10) mFab.hide();
                else if (dy < -10) mFab.show();
            }
        });
        mRepository = new AppRepository(this);
    }

    private void showAppPopupMenu(PackageAppData appData, int position) {
        View itemView = mRecyclerView.findViewHolderForAdapterPosition(position) != null
                ? mRecyclerView.findViewHolderForAdapterPosition(position).itemView
                : mRecyclerView;
        PopupMenu popup = new PopupMenu(this, itemView);
        popup.getMenuInflater().inflate(R.menu.app_popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.app_clear) confirmClearData(appData);
            else if (id == R.id.app_stop) confirmStopApp(appData);
            else if (id == R.id.app_remove) confirmUninstall(appData, position);
            else if (id == R.id.app_shortcut) Toast.makeText(this, R.string.create_shortcut_success, Toast.LENGTH_SHORT).show();
            return true;
        });
        popup.show();
    }

    private void confirmClearData(PackageAppData appData) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.home_menu_clear_title)
                .setMessage(String.format(getString(R.string.home_menu_clear_content), appData.name))
                .setPositiveButton(android.R.string.yes, (d, w) -> {
                    try {
                        VirtualCore.get().clearPackage(appData.packageName);
                        CloneHubDebugLog.log(this, "Cleared virtual app data for " + appData.packageName);
                        Toast.makeText(this, R.string.reboot_tips_1, Toast.LENGTH_SHORT).show();
                    } catch (Throwable e) {
                        CloneHubDebugLog.log(this, "Failed clearing virtual app data for " + appData.packageName, e);
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void confirmStopApp(PackageAppData appData) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.home_menu_kill_title)
                .setMessage(String.format(getString(R.string.home_menu_kill_content), appData.name))
                .setPositiveButton(android.R.string.yes, (d, w) -> {
                    try {
                        VirtualCore.get().killApp(appData.packageName, 0);
                        CloneHubDebugLog.log(this, "Force-stopped virtual app " + appData.packageName);
                        Toast.makeText(this, R.string.reboot_tips_1, Toast.LENGTH_SHORT).show();
                    } catch (Throwable e) {
                        CloneHubDebugLog.log(this, "Failed force-stopping " + appData.packageName, e);
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void confirmUninstall(PackageAppData appData, int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.home_menu_delete_title)
                .setMessage(String.format(getString(R.string.home_menu_delete_content), appData.name))
                .setPositiveButton(android.R.string.yes, (d, w) -> {
                    try {
                        VirtualCore.get().uninstallPackage(appData.packageName);
                        CloneHubDebugLog.log(this, "Uninstalled virtual app " + appData.packageName);
                        mAdapter.getData().remove(position);
                        mAdapter.notifyItemRemoved(position);
                        updateEmptyView();
                    } catch (Throwable e) {
                        CloneHubDebugLog.log(this, "Failed uninstalling virtual app " + appData.packageName, e);
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void loadApps() {
        mProgressBar.setVisibility(View.VISIBLE);
        mEmptyView.setVisibility(View.GONE);
        mRepository.getVirtualApps().done(apps -> runOnUiThread(() -> {
            mProgressBar.setVisibility(View.GONE);
            mAdapter.setData(apps);
            CloneHubDebugLog.log(this, "Loaded virtual app list count=" + apps.size());
            updateEmptyView();
        })).fail(err -> runOnUiThread(() -> {
            mProgressBar.setVisibility(View.GONE);
            CloneHubDebugLog.log(this, "Failed to load virtual apps", (Throwable) err);
            Log.e(TAG, "Failed to load virtual apps", (Throwable) err);
            updateEmptyView();
        }));
    }

    private void updateEmptyView() {
        if (mAdapter.getItemCount() == 0) {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        alertForDoze();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.main_debug_log) {
            exportDebugLog();
            return true;
        } else if (id == R.id.main_setting) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.main_reboot) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.settings_reboot_title)
                    .setMessage(R.string.settings_reboot_content)
                    .setPositiveButton(android.R.string.yes, (d, w) -> {
                        CloneHubDebugLog.log(this, "User requested VirtualCore.killAllApps");
                        VirtualCore.get().killAllApps();
                        Toast.makeText(this, R.string.reboot_tips_1, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportDebugLog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_EXPORT_DEBUG_PERMISSION);
            return;
        }
        saveDebugLogToDownloads();
    }

    private void saveDebugLogToDownloads() {
        try {
            String path = CloneHubDebugLog.exportToDownloads(this);
            Toast.makeText(this, "Debug TXT saved: " + path, Toast.LENGTH_LONG).show();
            CloneHubDebugLog.log(this, "User exported debug report to " + path);
        } catch (Throwable error) {
            CloneHubDebugLog.log(this, "Failed to export debug TXT to Downloads", error);
            Toast.makeText(this, "Could not save debug TXT: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_EXPORT_DEBUG_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveDebugLogToDownloads();
            } else {
                Toast.makeText(this, "Storage permission is needed to save the TXT file.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VCommends.REQUEST_SELECT_APP) loadApps();
    }

    public void startVirtualActivity(Intent intent, Bundle options, int usedId) {
        String packageName = intent.getPackage();
        if (TextUtils.isEmpty(packageName)) {
            ComponentName component = intent.getComponent();
            if (component != null) packageName = component.getPackageName();
        }
        if (packageName == null) {
            try {
                startActivity(intent);
                return;
            } catch (Throwable ignored) {
                CloneHubDebugLog.log(this, "Direct external activity launch failed", ignored);
            }
        }
        boolean result = LoadingActivity.launch(this, packageName, usedId);
        if (!result) throw new ActivityNotFoundException("can not launch activity for: " + intent);
    }

    private void alertForDoze() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) return;
        boolean showAlert = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SHOW_DOZE_ALERT_KEY, true);
        if (!showAlert) return;
        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            mUiHandler.postDelayed(() -> {
                try {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.alert_for_doze_mode_title)
                            .setMessage(R.string.alert_for_doze_mode_content)
                            .setPositiveButton(R.string.alert_for_doze_mode_yes, (dialog, which) -> {
                                try {
                                    startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:" + getPackageName())));
                                } catch (Throwable e) {
                                    try {
                                        startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                                    } catch (Throwable ex) {
                                        PreferenceManager.getDefaultSharedPreferences(this)
                                                .edit().putBoolean(SHOW_DOZE_ALERT_KEY, false).apply();
                                    }
                                }
                            })
                            .setNegativeButton(R.string.alert_for_doze_mode_no, (dialog, which) ->
                                    PreferenceManager.getDefaultSharedPreferences(this)
                                            .edit().putBoolean(SHOW_DOZE_ALERT_KEY, false).apply())
                            .show();
                } catch (Throwable ignored) {
                }
            }, 1500);
        }
    }
}
