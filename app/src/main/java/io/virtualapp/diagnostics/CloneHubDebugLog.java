package io.virtualapp.diagnostics;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
        if (!directory.exists() && !directory.mkdirs()) Log.e(TAG, "Unable to create debug log directory");
        return new File(directory, FILE_NAME);
    }

    public static synchronized void log(Context context, String message) {
        if (context == null) return;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        append(context, timestamp + " [" + android.os.Process.myPid() + "] " + message + "\n");
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
            try { output.write(text.getBytes("UTF-8")); output.flush(); }
            finally { output.close(); }
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

    /** Saves the report directly to the public Downloads/CloneHub folder. */
    public static synchronized String exportToDownloads(Context context) throws Exception {
        File source = getLogFile(context);
        if (!source.exists()) log(context, "Debug log export requested before any launch events.");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/CloneHub/";

            Cursor cursor = resolver.query(collection,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                    new String[]{FILE_NAME, relativePath}, null);
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        resolver.delete(Uri.withAppendedPath(collection, Long.toString(id)), null, null);
                    }
                } finally { cursor.close(); }
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri destination = resolver.insert(collection, values);
            if (destination == null) throw new java.io.IOException("Android could not create the debug TXT in Downloads.");

            try {
                OutputStream output = resolver.openOutputStream(destination, "w");
                if (output == null) throw new java.io.IOException("Could not open exported debug TXT.");
                try { copy(source, output); output.flush(); }
                finally { output.close(); }
                ContentValues completed = new ContentValues();
                completed.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(destination, completed, null, null);
            } catch (Exception error) {
                resolver.delete(destination, null, null);
                throw error;
            }
            log(context, "Debug report exported to Downloads/CloneHub/" + FILE_NAME);
            return "Downloads/CloneHub/" + FILE_NAME;
        }

        if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("WRITE_EXTERNAL_STORAGE permission is required.");
        }
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File directory = new File(downloads, "CloneHub");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new java.io.IOException("Could not create " + directory.getAbsolutePath());
        }
        File destination = new File(directory, FILE_NAME);
        FileInputStream input = new FileInputStream(source);
        try {
            FileOutputStream output = new FileOutputStream(destination, false);
            try { copy(input, output); output.flush(); }
            finally { output.close(); }
        } finally { input.close(); }
        log(context, "Debug report exported to " + destination.getAbsolutePath());
        return destination.getAbsolutePath();
    }

    private static void copy(File source, OutputStream output) throws Exception {
        FileInputStream input = new FileInputStream(source);
        try { copy(input, output); }
        finally { input.close(); }
    }

    private static void copy(FileInputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
}
