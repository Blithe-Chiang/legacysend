package com.blithe.legacysend.protocol;

import com.blithe.legacysend.model.DeviceInfo;
import com.blithe.legacysend.model.TransferFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ProtocolJson {
    private ProtocolJson() {}

    public static JSONObject prepareUpload(DeviceInfo self, List<TransferFile> files) throws JSONException {
        JSONObject root = new JSONObject();
        JSONObject info = self.toJson(false, true);
        info.remove("announce");
        root.put("info", info);
        JSONObject fileObject = new JSONObject();
        for (TransferFile file : files) {
            fileObject.put(file.getId(), file.toJson());
        }
        root.put("files", fileObject);
        return root;
    }

    public static List<TransferFile> parseFiles(JSONObject object) throws JSONException {
        List<TransferFile> result = new ArrayList<TransferFile>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            result.add(TransferFile.fromJson(object.getJSONObject(keys.next())));
        }
        return result;
    }

    public static JSONObject prepareResponse(String sessionId, Map<String, String> tokens)
            throws JSONException {
        JSONObject root = new JSONObject();
        root.put("sessionId", sessionId);
        JSONObject files = new JSONObject();
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            files.put(token.getKey(), token.getValue());
        }
        root.put("files", files);
        return root;
    }
}
