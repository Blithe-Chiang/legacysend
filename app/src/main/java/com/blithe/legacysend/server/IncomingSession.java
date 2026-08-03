package com.blithe.legacysend.server;

import com.blithe.legacysend.model.DeviceInfo;
import com.blithe.legacysend.model.TransferFile;

import java.net.InetAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class IncomingSession {
    public enum Decision { PENDING, ACCEPTED, REJECTED, CANCELLED }

    private final String sessionId = UUID.randomUUID().toString();
    private final DeviceInfo sender;
    private final InetAddress senderAddress;
    private final List<TransferFile> files;
    private final Map<String, String> tokens = new LinkedHashMap<String, String>();
    private final Map<String, Long> fileProgress = new LinkedHashMap<String, Long>();
    private final CountDownLatch decisionLatch = new CountDownLatch(1);
    private final AtomicLong receivedBytes = new AtomicLong(0L);
    private volatile Decision decision = Decision.PENDING;

    public IncomingSession(DeviceInfo sender, InetAddress senderAddress, List<TransferFile> files) {
        this.sender = sender;
        this.senderAddress = senderAddress;
        this.files = Collections.unmodifiableList(files);
        for (TransferFile file : files) {
            tokens.put(file.getId(), UUID.randomUUID().toString());
            fileProgress.put(file.getId(), 0L);
        }
    }

    public void accept() {
        if (decision == Decision.PENDING) {
            decision = Decision.ACCEPTED;
            decisionLatch.countDown();
        }
    }

    public void reject() {
        if (decision == Decision.PENDING) {
            decision = Decision.REJECTED;
            decisionLatch.countDown();
        }
    }

    public void cancel() {
        decision = Decision.CANCELLED;
        decisionLatch.countDown();
    }

    public Decision awaitDecision(long timeout, TimeUnit unit) throws InterruptedException {
        if (!decisionLatch.await(timeout, unit)) reject();
        return decision;
    }

    public TransferFile findFile(String id) {
        for (TransferFile file : files) if (file.getId().equals(id)) return file;
        return null;
    }

    public long getTotalBytes() {
        long total = 0L;
        for (TransferFile file : files) total += file.getSize();
        return total;
    }

    public synchronized long updateFileProgress(String fileId, long bytes) {
        fileProgress.put(fileId, bytes);
        long total = 0L;
        for (Long value : fileProgress.values()) total += value;
        return total;
    }

    public String getSessionId() { return sessionId; }
    public DeviceInfo getSender() { return sender; }
    public InetAddress getSenderAddress() { return senderAddress; }
    public List<TransferFile> getFiles() { return files; }
    public Map<String, String> getTokens() { return Collections.unmodifiableMap(tokens); }
    public String getToken(String fileId) { return tokens.get(fileId); }
    public Decision getDecision() { return decision; }
    public AtomicLong getReceivedBytes() { return receivedBytes; }
}
