package com.blithe.legacysend.model;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

public final class TransferFile {
    private final String id;
    private final String fileName;
    private final long size;
    private final String fileType;
    private final Uri uri;

    public TransferFile(String id, String fileName, long size, String fileType, Uri uri) {
        this.id = id;
        this.fileName = fileName;
        this.size = size;
        this.fileType = fileType == null ? "application/octet-stream" : fileType;
        this.uri = uri;
    }

    public static TransferFile fromJson(JSONObject json) throws JSONException {
        return new TransferFile(json.getString("id"), json.getString("fileName"),
                json.getLong("size"), json.optString("fileType", "application/octet-stream"), null);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("fileName", fileName);
        json.put("size", size);
        json.put("fileType", fileType);
        return json;
    }

    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public long getSize() { return size; }
    public String getFileType() { return fileType; }
    public Uri getUri() { return uri; }
}
