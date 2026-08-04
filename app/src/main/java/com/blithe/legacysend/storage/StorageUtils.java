package com.blithe.legacysend.storage;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.blithe.legacysend.model.TransferFile;

import java.io.File;
import java.util.UUID;

public final class StorageUtils {
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

    public static File receiveDirectory(Context context) {
        File downloads;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloads == null) downloads = context.getFilesDir();
        } else {
            downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }
        return new File(downloads, "LegacySend");
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

    public static String sanitizeFileName(String name) {
        if (name == null || name.trim().length() == 0) return "未命名文件";
        String safe = name.replace('/', '_').replace('\\', '_').replace('\u0000', '_');
        while (safe.startsWith(".")) safe = safe.substring(1);
        if (safe.length() == 0) return "未命名文件";
        return safe;
    }
}
