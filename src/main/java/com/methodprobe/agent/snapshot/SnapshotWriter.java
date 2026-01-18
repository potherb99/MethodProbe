package com.methodprobe.agent.snapshot;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async writer for method snapshots.
 * Uses a single daemon thread to write snapshots to files without blocking
 * business threads.
 */
public class SnapshotWriter {

    private static volatile ExecutorService executor;
    private static volatile String snapshotDir = "./probe-snapshots";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicLong sequence = new AtomicLong(0);

    private static final byte[] MAGIC = new byte[] { 'M', 'T', 'S', 'S' }; // Method probe Snap Shot
    private static final int VERSION = 1;

    /**
     * Initialize the snapshot writer.
     */
    public static void init(String dir) {
        if (initialized.compareAndSet(false, true)) {
            snapshotDir = dir;
            executor = new ThreadPoolExecutor(
                    1, 1,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(500),
                    r -> {
                        Thread t = new Thread(r, "MethodProbe-SnapshotWriter");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.DiscardOldestPolicy());
            System.out.println("[MethodProbe] SnapshotWriter initialized. Dir: " + dir);
        }
    }

    /**
     * Submit a snapshot for async writing.
     */
    public static void submit(MethodSnapshot snapshot) {
        if (executor != null && !executor.isShutdown()) {
            executor.submit(() -> writeSnapshot(snapshot));
        }
    }

    /**
     * Submit pre-serialized snapshot for async file writing only.
     */
    public static void submitSerialized(MethodSnapshot snapshot) {
        if (executor != null && !executor.isShutdown()) {
            executor.submit(() -> writeSnapshot(snapshot));
        }
    }

    /**
     * Write snapshot to file.
     */
    private static void writeSnapshot(MethodSnapshot snapshot) {
        try {
            // Create directory structure: {dir}/{date}/
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
            String dateStr = dateFmt.format(new Date(snapshot.getTimestamp()));

            Path dirPath = Paths.get(snapshotDir, dateStr);
            Files.createDirectories(dirPath);

            // Filename: use snapshotId if available, otherwise fallback to time-based
            String snapshotId = snapshot.getSnapshotId();
            String filename;
            if (snapshotId != null) {
                filename = snapshotId + ".snapshot";
            } else {
                SimpleDateFormat timeFmt = new SimpleDateFormat("HH-mm-ss-SSS");
                String timeStr = timeFmt.format(new Date(snapshot.getTimestamp()));
                filename = String.format("%s-%04d.snapshot", timeStr, sequence.incrementAndGet() % 10000);
            }
            Path filePath = dirPath.resolve(filename);

            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(filePath)))) {

                // Write magic and version
                dos.write(MAGIC);
                dos.writeInt(VERSION);

                // Write snapshotId
                dos.writeUTF(snapshotId != null ? snapshotId : "");

                // Write header
                dos.writeLong(snapshot.getTimestamp());
                dos.writeUTF(snapshot.getClassName());
                dos.writeUTF(snapshot.getMethodName());
                dos.writeUTF(snapshot.getThreadName());
                dos.writeDouble(snapshot.getDurationMs());

                // Write arg types
                String[] argTypes = snapshot.getArgTypes();
                dos.writeInt(argTypes != null ? argTypes.length : 0);
                if (argTypes != null) {
                    for (String type : argTypes) {
                        dos.writeUTF(type != null ? type : "null");
                    }
                }

                // Write serialized args
                byte[][] serializedArgs = snapshot.getSerializedArgs();
                dos.writeInt(serializedArgs != null ? serializedArgs.length : 0);
                if (serializedArgs != null) {
                    for (byte[] arg : serializedArgs) {
                        if (arg != null) {
                            dos.writeInt(arg.length);
                            dos.write(arg);
                        } else {
                            dos.writeInt(-1); // null marker
                        }
                    }
                }

                // Write exception if any
                byte[] exception = snapshot.getSerializedException();
                if (exception != null) {
                    dos.writeInt(exception.length);
                    dos.write(exception);
                } else {
                    dos.writeInt(-1);
                }
            }

        } catch (Exception e) {
            System.err.println("[MethodProbe] Failed to write snapshot: " + e.getMessage());
        }
    }

    /**
     * Shutdown the writer gracefully.
     */
    public static void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("[MethodProbe] SnapshotWriter shutdown complete");
        }
    }
}
