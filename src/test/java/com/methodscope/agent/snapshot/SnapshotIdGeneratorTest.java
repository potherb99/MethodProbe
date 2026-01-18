package com.methodprobe.agent.snapshot;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tests for SnapshotIdGenerator.
 */
public class SnapshotIdGeneratorTest {

    @Test
    public void testGenerateNotNull() {
        String id = SnapshotIdGenerator.generate();
        assertNotNull("Generated ID should not be null", id);
        assertFalse("Generated ID should not be empty", id.isEmpty());
    }

    @Test
    public void testGenerateFormat() {
        String id = SnapshotIdGenerator.generate();
        // Format: yyyyMMdd-HHmmss-SSS-seq (e.g., 20260112-091313-001-00001)
        String[] parts = id.split("-");
        assertEquals("ID should have 4 parts separated by -", 4, parts.length);

        // Date part: 8 digits
        assertEquals("Date part should be 8 chars", 8, parts[0].length());
        assertTrue("Date part should be numeric", parts[0].matches("\\d{8}"));

        // Time part: 6 digits
        assertEquals("Time part should be 6 chars", 6, parts[1].length());
        assertTrue("Time part should be numeric", parts[1].matches("\\d{6}"));

        // Milliseconds: 3 digits
        assertEquals("Millis part should be 3 chars", 3, parts[2].length());
        assertTrue("Millis part should be numeric", parts[2].matches("\\d{3}"));

        // Sequence: 5 digits
        assertEquals("Sequence part should be 5 chars", 5, parts[3].length());
        assertTrue("Sequence part should be numeric", parts[3].matches("\\d{5}"));
    }

    @Test
    public void testGenerateUniqueness() {
        Set<String> ids = new HashSet<>();
        int count = 1000;

        for (int i = 0; i < count; i++) {
            String id = SnapshotIdGenerator.generate();
            assertTrue("ID should be unique: " + id, ids.add(id));
        }

        assertEquals("Should have generated unique IDs", count, ids.size());
    }

    @Test
    public void testGenerateThreadSafety() throws InterruptedException {
        final int threadCount = 10;
        final int idsPerThread = 100;
        final Set<String> allIds = java.util.Collections.synchronizedSet(new HashSet<>());
        final CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < idsPerThread; i++) {
                        String id = SnapshotIdGenerator.generate();
                        allIds.add(id);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // All generated IDs should be unique
        assertEquals("All IDs across threads should be unique",
                threadCount * idsPerThread, allIds.size());
    }

    @Test
    public void testResetSequence() {
        // Generate some IDs
        SnapshotIdGenerator.generate();
        SnapshotIdGenerator.generate();

        // Reset
        SnapshotIdGenerator.resetSequence();

        // Next ID should start from 1
        String id = SnapshotIdGenerator.generate();
        assertTrue("After reset, sequence should start from 1",
                id.endsWith("-00001"));
    }
}
