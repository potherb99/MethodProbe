package com.methodprobe.agent.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async file log output implementation.
 * 
 * Features:
 * - Uses a blocking queue for async buffering
 * - Background daemon thread for batch writing
 * - Configurable buffer size and flush interval
 * - Date-based log file rolling
 * - Graceful shutdown with queue drain
 */
public class AsyncFileLogOutput implements LogOutput {

    private static final String LOG_FILE_PREFIX = "method-probe-";
    private static final String LOG_FILE_SUFFIX = ".log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final String logDir;
    private final long flushIntervalMs;

    private final BlockingQueue<String> messageQueue;
    private final Thread writerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile String currentDate;
    private volatile BufferedWriter currentWriter;
    private final Object writerLock = new Object();

    /**
     * Create an async file log output.
     * 
     * @param logDir          directory for log files
     * @param bufferSize      max size of message queue
     * @param flushIntervalMs interval between flush operations in milliseconds
     */
    public AsyncFileLogOutput(String logDir, int bufferSize, long flushIntervalMs) {
        this.logDir = logDir;
        this.flushIntervalMs = flushIntervalMs;
        this.messageQueue = new LinkedBlockingQueue<>(bufferSize);

        // Ensure log directory exists
        File dir = new File(logDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Initialize date and writer
        this.currentDate = DATE_FORMAT.format(new Date());
        initWriter();

        // Start background writer thread
        this.writerThread = new Thread(this::writerLoop, "MethodProbe-AsyncLogWriter");
        this.writerThread.setDaemon(true);
        this.writerThread.start();

        System.out.println("[MethodProbe] AsyncFileLogOutput started. Log dir: " + logDir);
    }

    @Override
    public void write(String message) {
        if (!running.get()) {
            return;
        }

        // Non-blocking offer to avoid blocking caller
        if (!messageQueue.offer(message)) {
            // Queue is full, drop oldest message and try again
            messageQueue.poll();
            messageQueue.offer(message);
        }
    }

    @Override
    public void flush() {
        // Drain and write all pending messages
        drainAndWrite();
    }

    @Override
    public void shutdown() {
        running.set(false);

        // Interrupt the writer thread to wake up from poll
        writerThread.interrupt();

        // Wait for thread to finish with timeout
        try {
            writerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Final drain
        drainAndWrite();

        // Close writer
        synchronized (writerLock) {
            closeWriter();
        }

        System.out.println("[MethodProbe] AsyncFileLogOutput shutdown complete.");
    }

    /**
     * Background writer loop.
     */
    private void writerLoop() {
        List<String> batch = new ArrayList<>(100);
        long lastFlushTime = System.currentTimeMillis();

        while (running.get() || !messageQueue.isEmpty()) {
            try {
                // Poll with timeout
                String message = messageQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);

                if (message != null) {
                    batch.add(message);

                    // Drain more messages if available (batch processing)
                    messageQueue.drainTo(batch, 99);
                }

                long now = System.currentTimeMillis();
                boolean shouldFlush = !batch.isEmpty()
                        && (now - lastFlushTime >= flushIntervalMs || batch.size() >= 100);

                if (shouldFlush) {
                    writeBatch(batch);
                    batch.clear();
                    lastFlushTime = now;
                }

            } catch (InterruptedException e) {
                // Shutdown signal, drain remaining
                if (!running.get()) {
                    break;
                }
            }
        }

        // Final batch write
        if (!batch.isEmpty()) {
            writeBatch(batch);
        }
    }

    /**
     * Write a batch of messages to file.
     */
    private void writeBatch(List<String> messages) {
        synchronized (writerLock) {
            try {
                checkDateRolling();

                if (currentWriter == null) {
                    initWriter();
                }

                if (currentWriter != null) {
                    for (String message : messages) {
                        currentWriter.write(message);
                    }
                    currentWriter.flush();
                }
            } catch (IOException e) {
                System.err.println("[MethodProbe] Error writing to log file: " + e.getMessage());
                // Try to reinitialize writer
                closeWriter();
                initWriter();
            }
        }
    }

    /**
     * Drain all messages from queue and write immediately.
     */
    private void drainAndWrite() {
        List<String> messages = new ArrayList<>();
        messageQueue.drainTo(messages);
        if (!messages.isEmpty()) {
            writeBatch(messages);
        }
    }

    /**
     * Check and perform date rolling if needed.
     */
    private void checkDateRolling() {
        String today = DATE_FORMAT.format(new Date());
        if (!today.equals(currentDate)) {
            currentDate = today;
            closeWriter();
            initWriter();
        }
    }

    /**
     * Initialize the current writer for today's log file.
     */
    private void initWriter() {
        try {
            String fileName = LOG_FILE_PREFIX + currentDate + LOG_FILE_SUFFIX;
            File logFile = new File(logDir, fileName);
            currentWriter = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(logFile, true),
                            StandardCharsets.UTF_8),
                    8192 // 8KB buffer
            );
        } catch (IOException e) {
            System.err.println("[MethodProbe] Failed to create log file: " + e.getMessage());
            currentWriter = null;
        }
    }

    /**
     * Close the current writer.
     */
    private void closeWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.flush();
                currentWriter.close();
            } catch (IOException e) {
                System.err.println("[MethodProbe] Error closing log file: " + e.getMessage());
            }
            currentWriter = null;
        }
    }

    /**
     * Get the log directory path.
     */
    public String getLogDir() {
        return logDir;
    }

    /**
     * Get the current queue size (for monitoring).
     */
    public int getQueueSize() {
        return messageQueue.size();
    }
}
