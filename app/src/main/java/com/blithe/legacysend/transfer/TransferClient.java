package com.blithe.legacysend.transfer;

import android.content.ContentResolver;

import com.blithe.legacysend.model.DeviceInfo;
import com.blithe.legacysend.model.TransferFile;
import com.blithe.legacysend.protocol.ProtocolJson;
import com.blithe.legacysend.security.TlsIdentity;
import com.blithe.legacysend.util.IoUtils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

public final class TransferClient {
    public interface Listener {
        void onProgress(String currentFile, int fileIndex, int fileCount, int percent);
        void onFinished(String message);
        void onFailed(String message);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final ContentResolver resolver;
    private final TlsIdentity identity;
    private final DeviceInfo self;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile HttpURLConnection activeConnection;
    private volatile String activeSession;
    private volatile DeviceInfo activeDevice;

    public TransferClient(ContentResolver resolver, TlsIdentity identity, DeviceInfo self) {
        this.resolver = resolver;
        this.identity = identity;
        this.self = self;
    }

    public DeviceInfo register(DeviceInfo remote) throws Exception {
        HttpURLConnection connection = open(remote, "/api/localsend/v2/register", "POST", 10_000);
        JSONObject registration = self.toJson(false, true);
        registration.remove("announce");
        writeJson(connection, registration);
        int status = connection.getResponseCode();
        if (status / 100 != 2) throw statusError(connection, status);
        JSONObject response = new JSONObject(readText(connection.getInputStream()));
        return DeviceInfo.fromJson(response, remote.getAddress());
    }

    public void send(DeviceInfo remote, List<TransferFile> files, Listener listener) {
        cancelled.set(false);
        activeDevice = remote;
        activeSession = null;
        try {
            if (files == null || files.isEmpty()) throw new IOException("尚未选择文件");
            HttpURLConnection prepare = open(remote, "/api/localsend/v2/prepare-upload", "POST", 300_000);
            activeConnection = prepare;
            writeJson(prepare, ProtocolJson.prepareUpload(self, files));
            int prepareStatus = prepare.getResponseCode();
            if (prepareStatus == 204) {
                listener.onFinished("对方无需接收这些文件");
                return;
            }
            if (prepareStatus == 403) throw new IOException("接收方拒绝了传输");
            if (prepareStatus / 100 != 2) throw statusError(prepare, prepareStatus);
            JSONObject response = new JSONObject(readText(prepare.getInputStream()));
            String sessionId = response.getString("sessionId");
            JSONObject tokens = response.getJSONObject("files");
            activeSession = sessionId;

            long totalBytes = 0L;
            for (TransferFile file : files) totalBytes += file.getSize();
            long completedBefore = 0L;
            for (int index = 0; index < files.size(); index++) {
                checkCancelled();
                final TransferFile file = files.get(index);
                String token = tokens.getString(file.getId());
                String path = "/api/localsend/v2/upload?sessionId=" + encode(sessionId)
                        + "&fileId=" + encode(file.getId()) + "&token=" + encode(token);
                HttpURLConnection upload = open(remote, path, "POST", 300_000);
                activeConnection = upload;
                upload.setRequestProperty("Content-Type", "application/octet-stream");
                upload.setDoOutput(true);
                upload.setFixedLengthStreamingMode(file.getSize());
                final long prior = completedBefore;
                final long total = totalBytes;
                final int fileNumber = index + 1;
                InputStream input = resolver.openInputStream(file.getUri());
                if (input == null) throw new IOException("无法读取文件：" + file.getFileName());
                try {
                    OutputStream output = upload.getOutputStream();
                    try {
                        IoUtils.copy(input, output, file.getSize(), new IoUtils.ProgressListener() {
                            @Override public void onBytes(long copied) throws IOException {
                                checkCancelled();
                                listener.onProgress(file.getFileName(), fileNumber, files.size(),
                                        IoUtils.percent(prior + copied, total));
                            }
                        });
                    } finally {
                        output.close();
                    }
                } finally {
                    input.close();
                }
                int uploadStatus = upload.getResponseCode();
                if (uploadStatus / 100 != 2) throw statusError(upload, uploadStatus);
                completedBefore += file.getSize();
            }
            listener.onProgress(files.get(files.size() - 1).getFileName(), files.size(), files.size(), 100);
            listener.onFinished("文件发送成功");
        } catch (Exception error) {
            String message = readable(error);
            if (error instanceof java.io.FileNotFoundException) {
                message = "无法读取所选文件，文件可能已被删除，或旧版文件选择器的权限已经失效";
            }
            listener.onFailed(cancelled.get() ? "发送已取消" : message);
        } finally {
            HttpURLConnection connection = activeConnection;
            if (connection != null) connection.disconnect();
            activeConnection = null;
            activeSession = null;
            activeDevice = null;
        }
    }

    public void cancel() {
        cancelled.set(true);
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        final String session = activeSession;
        final DeviceInfo device = activeDevice;
        if (session != null && device != null) {
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        HttpURLConnection cancel = open(device,
                                "/api/localsend/v2/cancel?sessionId=" + encode(session), "POST", 5_000);
                        cancel.setFixedLengthStreamingMode(0);
                        cancel.setDoOutput(true);
                        cancel.getResponseCode();
                        cancel.disconnect();
                    } catch (Exception ignored) {}
                }
            }, "LegacySend-cancel").start();
        }
    }

    private HttpURLConnection open(DeviceInfo remote, String path, String method, int readTimeout)
            throws Exception {
        String scheme = "http".equalsIgnoreCase(remote.getProtocol()) ? "http" : "https";
        URL url = new URL(scheme, remote.getAddress().getHostAddress(), remote.getPort(), path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(readTimeout);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection secure = (HttpsURLConnection) connection;
            secure.setSSLSocketFactory(identity.createPinnedClientFactory(remote.getFingerprint()));
            secure.setHostnameVerifier(TlsIdentity.pinnedHostnameVerifier());
        }
        return connection;
    }

    private static void writeJson(HttpURLConnection connection, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(UTF8);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(bytes.length);
        OutputStream output = connection.getOutputStream();
        try { output.write(bytes); } finally { output.close(); }
    }

    private static IOException statusError(HttpURLConnection connection, int status) {
        String message = "";
        try {
            InputStream error = connection.getErrorStream();
            if (error != null) message = readText(error);
        } catch (Exception ignored) {}
        if (message.length() > 160) message = message.substring(0, 160);
        return new IOException("对方返回错误 " + status + (message.length() == 0 ? "" : "：" + message));
    }

    private static String readText(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IoUtils.copy(input, output, -1, null);
        return new String(output.toByteArray(), UTF8);
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private void checkCancelled() throws IOException {
        if (cancelled.get()) throw new IOException("发送已取消");
    }

    private static String readable(Exception error) {
        String message = error.getMessage();
        return message == null || message.length() == 0 ? error.getClass().getSimpleName() : message;
    }
}
