package io.virtualapp.diagnostics;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CloneHubDebugLog {
    private static final String TAG = "CloneHubDebug";
    private static final String FILE_NAME = "CloneHub-debug.txt";
    private static final long MAX_BYTES = 512 * 1024L;

    private CloneHubDebugLog() { }

    private static File getLogFile(Context context) {
        File directory = new File(context.getFilesDir(), "debug");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Unable to create debug log directory");
        }
        return new File(directory, FILE_NAME);
    }

    public static synchronized void log(Context context, String message) {
        if (context == null) return;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String line = timestamp + " [" + android.os.Process.myPid() + "] " + message + "\n";
        append(context, line);
        Log.i(TAG, message);
    }

    public static synchronized void log(Context context, String message, Throwable error) {
        if (context == null) return;
        StringWriter buffer = new StringWriter();
        if (error != null) error.printStackTrace(new PrintWriter(buffer));
        log(context, message + (error == null ? "" : "\n" + buffer));
        Log.e(TAG, message, error);
    }

    private static void append(Context context, String text) {
        try {
            File file = getLogFile(context);
            if (file.exists() && file.length() > MAX_BYTES) {
                File backup = new File(file.getParentFile(), FILE_NAME + ".old");
                if (backup.exists()) backup.delete();
                if (!file.renameTo(backup)) file.delete();
            }
            FileOutputStream output = new FileOutputStream(file, true);
            output.write(text.getBytes("UTF-8"));
            output.flush();
            output.close();
        } catch (Throwable error) {
            Log.e(TAG, "Could not write debug log", error);
        }
    }

    public static void initialize(Context context) {
        log(context, "===== CloneHub debug session =====");
        log(context, "App=" + context.getPackageName()
                + " Android=" + Build.VERSION.RELEASE
                + " API=" + Build.VERSION.SDK_INT
                + " Manufacturer=" + Build.MANUFACTURER
                + " Model=" + Build.MODEL
                + " ABIs=" + java.util.Arrays.toString(Build.SUPPORTED_ABIS));
    }

    public static void share(Context context) {
        try {
            File file = getLogFile(context);
            if (!file.exists()) {
                log(context, "Debug log export requested before any launch events.");
            }
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".debugfiles", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "CloneHub debug log");
            send.putExtra(Intent.EXTRA_TEXT, "CloneHub launch diagnostics attached.");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(send, "Share CloneHub debug log")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            log(context, "Debug log share sheet opened.");
        } catch (Throwable error) {
            log(context, "Failed to export debug log.", error);
        }
    }
}
