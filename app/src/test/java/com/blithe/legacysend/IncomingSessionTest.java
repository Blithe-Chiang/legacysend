package com.blithe.legacysend;

import com.blithe.legacysend.model.DeviceInfo;
import com.blithe.legacysend.model.TransferFile;
import com.blithe.legacysend.server.IncomingSession;

import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class IncomingSessionTest {
    private IncomingSession session() throws Exception {
        InetAddress address = InetAddress.getByName("192.168.1.8");
        DeviceInfo sender = new DeviceInfo("发送方", "2.0", "电脑", "desktop", "ABC",
                53317, "https", false, address);
        return new IncomingSession(sender, address, Arrays.asList(
                new TransferFile("a", "一.bin", 10, "application/octet-stream", null),
                new TransferFile("b", "二.bin", 30, "application/octet-stream", null)));
    }

    @Test public void acceptAndRejectStatesAreExplicit() throws Exception {
        IncomingSession accepted = session();
        accepted.accept();
        assertEquals(IncomingSession.Decision.ACCEPTED,
                accepted.awaitDecision(1, TimeUnit.MILLISECONDS));
        IncomingSession rejected = session();
        rejected.reject();
        assertEquals(IncomingSession.Decision.REJECTED,
                rejected.awaitDecision(1, TimeUnit.MILLISECONDS));
    }

    @Test public void cancelStateStopsSession() throws Exception {
        IncomingSession value = session();
        value.cancel();
        assertEquals(IncomingSession.Decision.CANCELLED, value.getDecision());
    }

    @Test public void timeoutIsRejectedAndTokensArePerFile() throws Exception {
        IncomingSession value = session();
        assertEquals(IncomingSession.Decision.REJECTED,
                value.awaitDecision(1, TimeUnit.MILLISECONDS));
        assertNotEquals(value.getToken("a"), value.getToken("b"));
        assertEquals(40L, value.getTotalBytes());
    }
}
