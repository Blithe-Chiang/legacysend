package com.blithe.legacysend.storage;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.blithe.legacysend.model.TransferFile;

import java.io.File;
import java.util.Set;
import java.util.UUID;

public final class StorageUtils {
    private static final String PREFERENCES = "storage_settings";
    private static final String RECEIVE_TREE_URI = "receive_tree_uri";
    private static final String RECEIVE_FILE_PATH = "receive_file_path";

    private StorageUtils() {}

    public static TransferFile describe(ContentResolver resolver, Uri uri) {
        // ES 文件浏览器在 Android 4.x 上通过 file:// URI 分享文件。这类 URI 没有
        // ContentProvider，因此无法通过 OpenableColumns 查询名称，需要直接读取路径。
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            return describe(new File(uri.getPath()), uri);
        }

        String name = "未命名文件";
        long size = -1L;
        Cursor cursor = resolver.query(uri, new String[] {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex);
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
                }
            } finally {
                cursor.close();
            }
        }
        if (size < 0) {
            try {
                AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(uri, "r");
                if (descriptor != null) {
                    size = descriptor.getLength();
                    descriptor.close();
                }
            } catch (Exception ignored) {}
        }
        String type = resolver.getType(uri);
        return new TransferFile(UUID.randomUUID().toString(), sanitizeFileName(name),
                Math.max(0L, size), type, uri);
    }

    public static TransferFile describe(File file) {
        return describe(file, Uri.fromFile(file));
    }

    private static TransferFile describe(File file, Uri uri) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                extension == null ? "" : extension.toLowerCase(java.util.Locale.US));
        return new TransferFile(UUID.randomUUID().toString(), sanitizeFileName(file.getName()),
                file.length(), type, uri);
    }

    private static File defaultReceiveDirectory(Context context) {
        File downloads;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloads == null) downloads = context.getFilesDir();
        } else {
            downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }
        return new File(downloads, "LegacySend");
    }

    public static ReceiveDirectory receiveDirectory(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String treeUri = preferences.getString(RECEIVE_TREE_URI, null);
        if (treeUri != null && android.os.Build.VERSION.SDK_INT >= 21) {
            return ReceiveDirectory.forTree(context, Uri.parse(treeUri));
        }
        String filePath = preferences.getString(RECEIVE_FILE_PATH, null);
        File directory = filePath == null ? defaultReceiveDirectory(context) : new File(filePath);
        return ReceiveDirectory.forFile(context, directory);
    }

    public static void setReceiveDirectory(Context context, Uri treeUri) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(RECEIVE_TREE_URI, treeUri.toString())
                .remove(RECEIVE_FILE_PATH)
                .apply();
    }

    public static void setReceiveDirectory(Context context, File directory) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(RECEIVE_FILE_PATH, directory.getAbsolutePath())
                .remove(RECEIVE_TREE_URI)
                .apply();
    }

    public static void resetReceiveDirectory(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .remove(RECEIVE_TREE_URI)
                .remove(RECEIVE_FILE_PATH)
                .apply();
    }

    public static File uniqueFile(File directory, String requestedName) {
        String safe = sanitizeFileName(requestedName);
        File candidate = new File(directory, safe);
        if (!candidate.exists()) return candidate;
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String extension = dot > 0 ? safe.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            candidate = new File(directory, base + " (" + i + ")" + extension);
            if (!candidate.exists()) return candidate;
        }
        return new File(directory, base + "-" + System.currentTimeMillis() + extension);
    }

    public static String uniqueName(Set<String> existingNames, String requestedName) {
        String safe = sanitizeFileName(requestedName);
        if (!existingNames.contains(safe)) return safe;
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String extension = dot > 0 ? safe.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            String candidate = base + " (" + i + ")" + extension;
            if (!existingNames.contains(candidate)) return candidate;
        }
        return base + "-" + System.currentTimeMillis() + extension;
    }

    public static String sanitizeFileName(String name) {
        if (name == null || name.trim().length() == 0) return "未命名文件";
        String safe = name.replace('/', '_').replace('\\', '_').replace('\u0000', '_');
        while (safe.startsWith(".")) safe = safe.substring(1);
        if (safe.length() == 0) return "未命名文件";
        return safe;
    }
}
