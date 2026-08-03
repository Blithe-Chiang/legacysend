package com.blithe.legacysend;

import com.blithe.legacysend.model.DeviceInfo;
import com.blithe.legacysend.model.TransferFile;
import com.blithe.legacysend.protocol.ProtocolJson;

import org.json.JSONObject;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProtocolJsonTest {
    @Test public void deviceInformationRoundTrips() throws Exception {
        DeviceInfo source = new DeviceInfo("客厅手机", "2.0", "Android 4.4.2", "mobile",
                "AABBCC", 53317, "https", false, InetAddress.getByName("192.168.1.5"));
        JSONObject json = source.toJson(true, true);
        DeviceInfo decoded = DeviceInfo.fromJson(json, source.getAddress());
        assertEquals("客厅手机", decoded.getAlias());
        assertEquals("mobile", decoded.getDeviceType());
        assertEquals(53317, decoded.getPort());
        assertTrue(json.getBoolean("announce"));
    }

    @Test public void fileMetadataSupportsChineseSpacesAndSpecialCharacters() throws Exception {
        TransferFile chinese = new TransferFile("文件-1", "春节 照片 #1 (最终).jpg", 123456L,
                "image/jpeg", null);
        JSONObject json = chinese.toJson();
        TransferFile decoded = TransferFile.fromJson(json);
        assertEquals(chinese.getFileName(), decoded.getFileName());
        assertEquals(chinese.getSize(), decoded.getSize());
        assertEquals("image/jpeg", decoded.getFileType());
    }

    @Test public void multipleFilesUseProtocolObjectKeyedById() throws Exception {
        DeviceInfo self = new DeviceInfo("测试设备", "2.0", "测试机", "mobile", "FF00",
                53317, "https", false, null);
        List<TransferFile> files = Arrays.asList(
                new TransferFile("one", "一.txt", 1, "text/plain", null),
                new TransferFile("two", "二.txt", 2, "text/plain", null));
        JSONObject request = ProtocolJson.prepareUpload(self, files);
        assertEquals(2, request.getJSONObject("files").length());
        assertEquals("一.txt", request.getJSONObject("files").getJSONObject("one").getString("fileName"));
        assertFalse(request.getJSONObject("info").has("announce"));
        assertEquals(2, ProtocolJson.parseFiles(request.getJSONObject("files")).size());
    }

    @Test public void prepareResponseContainsSessionAndPerFileTokens() throws Exception {
        Map<String, String> tokens = new LinkedHashMap<String, String>();
        tokens.put("file-a", "token-a");
        tokens.put("file-b", "token-b");
        JSONObject response = ProtocolJson.prepareResponse("session-1", tokens);
        assertEquals("session-1", response.getString("sessionId"));
        assertEquals("token-b", response.getJSONObject("files").getString("file-b"));
    }
}
