package com.blithe.legacysend;

import com.blithe.legacysend.storage.StorageUtils;
import com.blithe.legacysend.util.IoUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StorageAndIoTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void duplicateNamesAreRenamedWithoutOverwriting() throws Exception {
        File directory = temporaryFolder.newFolder("downloads");
        File original = new File(directory, "报告 2026.txt");
        assertTrue(original.createNewFile());
        File renamed = StorageUtils.uniqueFile(directory, "报告 2026.txt");
        assertEquals("报告 2026 (1).txt", renamed.getName());
    }

    @Test public void unsafePathCharactersCannotEscapeDirectory() throws Exception {
        assertEquals("_上级_秘密.txt", StorageUtils.sanitizeFileName("/上级\\秘密.txt"));
        assertEquals("隐藏", StorageUtils.sanitizeFileName("..隐藏"));
    }

    @Test public void streamCopyPreservesLargeContentAndHash() throws Exception {
        byte[] source = new byte[1024 * 1024 + 123];
        new Random(42L).nextBytes(source);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long copied = IoUtils.copy(new ByteArrayInputStream(source), output, source.length, null);
        assertEquals(source.length, copied);
        assertArrayEquals(source, output.toByteArray());
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        assertArrayEquals(sha256.digest(source), sha256.digest(output.toByteArray()));
    }

    @Test(expected = IOException.class)
    public void interruptedStreamIsDetected() throws Exception {
        IoUtils.copy(new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
                new ByteArrayOutputStream(), 10, null);
    }

    @Test public void progressCalculationHandlesBoundsAndEmptyFiles() {
        assertEquals(0, IoUtils.percent(0, 0));
        assertEquals(50, IoUtils.percent(5, 10));
        assertEquals(100, IoUtils.percent(20, 10));
    }
}
