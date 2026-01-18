package com.methodprobe.agent.snapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generator for unique snapshot IDs.
 * Format: yyyyMMdd-HHmmss-SSS-seq (e.g., 20260112-091313-001-0001)
 * Thread-safe using atomic sequence counter.
 */
public class SnapshotIdGenerator {

    private static final AtomicLong sequence = new AtomicLong(0);

    // Use ThreadLocal to avoid synchronization on SimpleDateFormat
    private static final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal
            .withInitial(() -> new SimpleDateFormat("yyyyMMdd-HHmmss-SSS"));

    /**
     * Generate a unique snapshot ID.
     * 
     * @return unique ID in format: yyyyMMdd-HHmmss-SSS-seq
     */
    public static String generate() {
        String timestamp = dateFormat.get().format(new Date());
        long seq = sequence.incrementAndGet() % 100000;
        return String.format("%s-%05d", timestamp, seq);
    }

    /**
     * Reset sequence counter (for testing).
     */
    public static void resetSequence() {
        sequence.set(0);
    }
}
