package com.blithe.legacysend.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;

public final class DeviceInfo {
    public static final String PROTOCOL_VERSION = "2.0";

    private final String alias;
    private final String version;
    private final String deviceModel;
    private final String deviceType;
    private final String fingerprint;
    private final int port;
    private final String protocol;
    private final boolean download;
    private final InetAddress address;

    public DeviceInfo(String alias, String version, String deviceModel, String deviceType,
                      String fingerprint, int port, String protocol, boolean download,
                      InetAddress address) {
        this.alias = alias;
        this.version = version;
        this.deviceModel = deviceModel;
        this.deviceType = deviceType;
        this.fingerprint = fingerprint;
        this.port = port;
        this.protocol = protocol;
        this.download = download;
        this.address = address;
    }

    public static DeviceInfo fromJson(JSONObject json, InetAddress address) throws JSONException {
        return new DeviceInfo(
                json.getString("alias"),
                json.optString("version", PROTOCOL_VERSION),
                json.optString("deviceModel", "未知设备"),
                json.optString("deviceType", "desktop"),
                json.getString("fingerprint"),
                json.optInt("port", 53317),
                json.optString("protocol", "https"),
                json.optBoolean("download", false),
                address
        );
    }

    public JSONObject toJson(boolean announce, boolean includePortAndProtocol) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("alias", alias);
        json.put("version", version);
        json.put("deviceModel", deviceModel);
        json.put("deviceType", deviceType);
        json.put("fingerprint", fingerprint);
        if (includePortAndProtocol) {
            json.put("port", port);
            json.put("protocol", protocol);
        }
        json.put("download", download);
        json.put("announce", announce);
        return json;
    }

    public String getAlias() { return alias; }
    public String getVersion() { return version; }
    public String getDeviceModel() { return deviceModel; }
    public String getDeviceType() { return deviceType; }
    public String getFingerprint() { return fingerprint; }
    public int getPort() { return port; }
    public String getProtocol() { return protocol; }
    public boolean isDownload() { return download; }
    public InetAddress getAddress() { return address; }

    public String key() {
        return fingerprint + "@" + (address == null ? "" : address.getHostAddress());
    }

    @Override public String toString() {
        String host = address == null ? "未知地址" : address.getHostAddress();
        return alias + "\n" + deviceModel + " · " + host + ":" + port;
    }
}
