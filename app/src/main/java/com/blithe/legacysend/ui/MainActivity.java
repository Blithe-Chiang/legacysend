package com.blithe.legacysend.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.blithe.legacysend.LegacySendApp;
import com.blithe.legacysend.model.DeviceInfo;
import com.blithe.legacysend.model.TransferFile;
import com.blithe.legacysend.server.IncomingSession;
import com.blithe.legacysend.storage.StorageUtils;

import java.util.ArrayList;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements LegacySendApp.UiListener {
    private static final int PICK_FILES = 1001;
    private static final int STORAGE_PERMISSION = 1002;

    private LegacySendApp app;
    private TextView deviceName;
    private TextView serviceStatus;
    private Button serviceButton;
    private LinearLayout selectedFilesContainer;
    private LinearLayout devicesContainer;
    private final List<TransferFile> selectedFiles = new ArrayList<TransferFile>();
    private Dialog progressDialog;
    private TextView progressTitle;
    private TextView progressFile;
    private TextView progressPath;
    private ProgressBar progressBar;
    private Button cancelTransfer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        app = (LegacySendApp) getApplication();
        buildUi();
        ensureStoragePermission();
        app.setUiListener(this);
        app.startReceiving();
    }

    @Override protected void onResume() {
        super.onResume();
        app.setUiListener(this);
    }

    @Override protected void onPause() {
        app.setUiListener(null);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("旧版互传", 26, Color.rgb(25, 55, 90));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());
        TextView subtitle = text("兼容 LocalSend 的局域网文件传输", 14, Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(subtitle, matchWrap());
        root.addView(space(18));

        root.addView(section("本机设备"));
        deviceName = text("正在初始化设备身份…", 17, Color.BLACK);
        root.addView(deviceName, matchWrap());
        serviceStatus = text("接收服务已停止", 14, Color.DKGRAY);
        root.addView(serviceStatus, matchWrap());

        LinearLayout serviceActions = horizontal();
        serviceButton = button("停止接收");
        serviceButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if ("停止接收".contentEquals(serviceButton.getText())) app.stopReceiving();
                else app.startReceiving();
            }
        });
        Button refresh = button("重新发现");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { app.refreshDiscovery(); }
        });
        serviceActions.addView(serviceButton, weighted());
        serviceActions.addView(refresh, weighted());
        root.addView(serviceActions, matchWrap());

        root.addView(space(18));
        root.addView(section("选择文件"));
        Button choose = button("选择一个或多个文件");
        choose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openFilePicker(); }
        });
        root.addView(choose, matchWrap());
        selectedFilesContainer = new LinearLayout(this);
        selectedFilesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(selectedFilesContainer, matchWrap());
        renderSelectedFiles();

        root.addView(space(18));
        root.addView(section("附近设备"));
        TextView hint = text("选择文件后，点击目标设备开始发送。", 13, Color.DKGRAY);
        root.addView(hint, matchWrap());
        devicesContainer = new LinearLayout(this);
        devicesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(devicesContainer, matchWrap());
        renderDevices(new ArrayList<DeviceInfo>());

        setContentView(scroll);
    }

    private void openFilePicker() {
        if (Build.VERSION.SDK_INT <= 20) {
            openLegacyDirectory(legacyStorageRoot());
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_FILES);
    }

    private void openLegacyDirectory(final File directory) {
        File[] listed = directory.listFiles();
        if (listed == null) {
            Toast.makeText(this, "无法读取此目录，请检查存储权限", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<File> entries = new ArrayList<File>(Arrays.asList(listed));
        Collections.sort(entries, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                if (left.isDirectory() != right.isDirectory()) return left.isDirectory() ? -1 : 1;
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            File file = entries.get(i);
            labels[i] = file.isDirectory() ? "[文件夹] " + file.getName()
                    : file.getName() + "  ·  " + formatSize(file.length());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("选择文件\n" + directory.getAbsolutePath())
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        File selected = entries.get(which);
                        if (selected.isDirectory()) {
                            openLegacyDirectory(selected);
                        } else if (selected.isFile() && selected.canRead()) {
                            for (TransferFile existing : selectedFiles) {
                                if (existing.getUri().equals(Uri.fromFile(selected))) return;
                            }
                            selectedFiles.add(StorageUtils.describe(selected));
                            renderSelectedFiles();
                            Toast.makeText(MainActivity.this,
                                    "已添加文件；可再次选择以添加更多文件", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "无法读取所选文件", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null);
        File root = legacyStorageRoot();
        File parent = directory.getParentFile();
        if (parent != null && !directory.equals(root)) {
            builder.setNeutralButton("上一级", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    openLegacyDirectory(directory.getParentFile());
                }
            });
        }
        builder.show();
    }

    private File legacyStorageRoot() {
        File root = Environment.getExternalStorageDirectory();
        // 部分 Kindle 4.4 固件错误地把外部存储根目录报告为 Download。
        File parent = root.getParentFile();
        if (Environment.DIRECTORY_DOWNLOADS.equalsIgnoreCase(root.getName())
                && parent != null && parent.canRead()) {
            return parent;
        }
        return root;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILES || resultCode != RESULT_OK || data == null) return;
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) addSelectedUri(clip.getItemAt(i).getUri(), flags);
        } else if (data.getData() != null) {
            addSelectedUri(data.getData(), flags);
        }
        renderSelectedFiles();
    }

    private void addSelectedUri(Uri uri, int flags) {
        try {
            getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        for (TransferFile file : selectedFiles) if (uri.equals(file.getUri())) return;
        selectedFiles.add(StorageUtils.describe(getContentResolver(), uri));
    }

    private void renderSelectedFiles() {
        selectedFilesContainer.removeAllViews();
        if (selectedFiles.isEmpty()) {
            selectedFilesContainer.addView(text("尚未选择文件", 14, Color.GRAY), matchWrap());
            return;
        }
        for (final TransferFile file : new ArrayList<TransferFile>(selectedFiles)) {
            LinearLayout row = horizontal();
            TextView info = text(file.getFileName() + "\n" + formatSize(file.getSize()), 14, Color.BLACK);
            Button remove = button("移除");
            remove.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    selectedFiles.remove(file);
                    renderSelectedFiles();
                }
            });
            row.addView(info, weighted());
            row.addView(remove, new LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT));
            selectedFilesContainer.addView(row, matchWrap());
        }
    }

    private void renderDevices(List<DeviceInfo> devices) {
        devicesContainer.removeAllViews();
        if (devices.isEmpty()) {
            devicesContainer.addView(text("暂未发现设备，请确认处于同一局域网。", 14, Color.GRAY), matchWrap());
            return;
        }
        for (final DeviceInfo device : devices) {
            Button target = button(device.toString());
            target.setAllCaps(false);
            target.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            target.setPadding(dp(14), dp(10), dp(14), dp(10));
            target.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { confirmSend(device); }
            });
            devicesContainer.addView(target, matchWrap());
        }
    }

    private void confirmSend(final DeviceInfo device) {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        long total = 0L;
        for (TransferFile file : selectedFiles) total += file.getSize();
        new AlertDialog.Builder(this)
                .setTitle("发送文件")
                .setMessage("向“" + device.getAlias() + "”发送 " + selectedFiles.size()
                        + " 个文件，共 " + formatSize(total) + "？")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始发送", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        showProgress(true, "等待接收方确认…", "", 0, "");
                        app.sendFiles(device, new ArrayList<TransferFile>(selectedFiles));
                    }
                }).show();
    }

    @Override public void onReady(DeviceInfo self) {
        deviceName.setText("设备名称：" + self.getAlias());
    }

    @Override public void onServiceChanged(boolean running, String detail) {
        serviceStatus.setText(detail);
        serviceButton.setText(running ? "停止接收" : "启动接收");
    }

    @Override public void onDevicesChanged(List<DeviceInfo> devices) { renderDevices(devices); }

    @Override public void onIncoming(final IncomingSession session) {
        StringBuilder details = new StringBuilder();
        details.append("发送方：").append(session.getSender().getAlias()).append('\n');
        details.append("文件数量：").append(session.getFiles().size()).append('\n');
        details.append("总大小：").append(formatSize(session.getTotalBytes())).append("\n\n");
        for (TransferFile file : session.getFiles()) {
            details.append(file.getFileName()).append("  ").append(formatSize(file.getSize())).append('\n');
        }
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("收到文件传输请求")
                .setMessage(details.toString())
                .setCancelable(false)
                .setNegativeButton("拒绝", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        app.decideIncoming(session, false);
                    }
                })
                .setPositiveButton("接受", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        app.decideIncoming(session, true);
                        showProgress(false, "等待发送方上传…", "", 0,
                                StorageUtils.receiveDirectory(MainActivity.this).getAbsolutePath());
                    }
                }).create();
        dialog.show();
    }

    @Override public void onTransferProgress(boolean sending, String title, String currentFile,
                                             int percent, String path) {
        showProgress(sending, title, currentFile, percent, path);
    }

    @Override public void onTransferResult(boolean sending, boolean success, String message) {
        if (progressDialog != null) progressDialog.dismiss();
        progressDialog = null;
        new AlertDialog.Builder(this)
                .setTitle(success ? (sending ? "发送成功" : "接收成功") : "传输失败")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void showProgress(final boolean sending, String title, String file, int percent, String path) {
        if (progressDialog == null) {
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(22), dp(18), dp(22), dp(12));
            progressTitle = text(title, 18, Color.BLACK);
            progressFile = text(file, 14, Color.DKGRAY);
            progressPath = text(path.length() == 0 ? "" : "保存位置：" + path, 12, Color.GRAY);
            progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            cancelTransfer = button(sending ? "取消发送" : "取消接收");
            cancelTransfer.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (sending) app.cancelSending(); else app.cancelIncoming();
                    cancelTransfer.setEnabled(false);
                }
            });
            content.addView(progressTitle, matchWrap());
            content.addView(progressFile, matchWrap());
            content.addView(progressBar, matchWrap());
            content.addView(progressPath, matchWrap());
            content.addView(cancelTransfer, matchWrap());
            progressDialog = new Dialog(this);
            progressDialog.setTitle(sending ? "发送状态" : "接收状态");
            progressDialog.setContentView(content);
            progressDialog.setCancelable(false);
            progressDialog.show();
            if (progressDialog.getWindow() != null) progressDialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        progressTitle.setText(title);
        progressFile.setText(file.length() == 0 ? "准备中" : "当前文件：" + file);
        progressPath.setText(path.length() == 0 ? "" : "保存位置：" + path);
        progressBar.setProgress(percent);
    }

    private void ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, STORAGE_PERMISSION);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_PERMISSION && (results.length == 0
                || results[results.length - 1] != PackageManager.PERMISSION_GRANTED)) {
            new AlertDialog.Builder(this).setTitle("缺少存储权限")
                    .setMessage("没有存储权限将无法保存接收的文件。可在系统设置中重新授权。")
                    .setNegativeButton("暂不授权", null)
                    .setPositiveButton("打开设置", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        }
                    }).show();
        }
    }

    private TextView section(String value) {
        TextView view = text(value, 18, Color.rgb(20, 80, 145));
        view.setPadding(0, 0, 0, dp(6));
        return view;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        return button;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = { "KB", "MB", "GB", "TB" };
        int index = -1;
        do { value /= 1024.0; index++; } while (value >= 1024.0 && index < units.length - 1);
        return String.format(Locale.CHINA, "%.1f %s", value, units[index]);
    }
}
