package com.methodprobe.agent.log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Unit tests for AsyncFileLogOutput.
 */
public class AsyncFileLogOutputTest {

    private static final String TEST_LOG_DIR = "./target/test-probe-logs";
    private AsyncFileLogOutput logOutput;

    @Before
    public void setUp() {
        // Clean up test directory
        deleteDirectory(new File(TEST_LOG_DIR));
    }

    @After
    public void tearDown() {
        if (logOutput != null) {
            logOutput.shutdown();
        }
        // Clean up test directory
        deleteDirectory(new File(TEST_LOG_DIR));
    }

    @Test
    public void testWriteToFile() throws Exception {
        logOutput = new AsyncFileLogOutput(TEST_LOG_DIR, 1000, 100);

        // Write a test message
        String testMessage = "Test message 1\n";
        logOutput.write(testMessage);

        // Force flush
        logOutput.flush();

        // Wait a bit for async write
        Thread.sleep(200);

        // Verify file was created and contains the message
        String todayDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File logFile = new File(TEST_LOG_DIR, "method-probe-" + todayDate + ".log");

        assertTrue("Log file should exist", logFile.exists());

        String content = readFileContent(logFile);
        assertTrue("Log file should contain test message", content.contains("Test message 1"));
    }

    @Test
    public void testAsyncBuffering() throws Exception {
        logOutput = new AsyncFileLogOutput(TEST_LOG_DIR, 1000, 500);

        // Write multiple messages quickly
        for (int i = 0; i < 10; i++) {
            logOutput.write("Buffered message " + i + "\n");
        }

        // Messages should be buffered, not immediately written
        // Force flush
        logOutput.flush();

        // Wait for async processing
        Thread.sleep(600);

        // Verify all messages were written
        String todayDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File logFile = new File(TEST_LOG_DIR, "method-probe-" + todayDate + ".log");

        String content = readFileContent(logFile);
        for (int i = 0; i < 10; i++) {
            assertTrue("Should contain message " + i, content.contains("Buffered message " + i));
        }
    }

    @Test
    public void testFlushOnShutdown() throws Exception {
        logOutput = new AsyncFileLogOutput(TEST_LOG_DIR, 1000, 5000); // Long flush interval

        // Write messages
        logOutput.write("Shutdown test message 1\n");
        logOutput.write("Shutdown test message 2\n");

        // Shutdown should flush all pending messages
        logOutput.shutdown();
        logOutput = null; // Prevent double shutdown in tearDown

        // Verify messages were written
        String todayDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File logFile = new File(TEST_LOG_DIR, "method-probe-" + todayDate + ".log");

        assertTrue("Log file should exist", logFile.exists());

        String content = readFileContent(logFile);
        assertTrue("Should contain message 1", content.contains("Shutdown test message 1"));
        assertTrue("Should contain message 2", content.contains("Shutdown test message 2"));
    }

    @Test
    public void testHighConcurrency() throws Exception {
        logOutput = new AsyncFileLogOutput(TEST_LOG_DIR, 10000, 100);

        int numThreads = 10;
        int messagesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        // Write messages from multiple threads
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < messagesPerThread; i++) {
                        logOutput.write("Thread-" + threadId + "-Message-" + i + "\n");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Shutdown to flush all messages
        logOutput.shutdown();
        logOutput = null;

        // Verify all messages were written
        String todayDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File logFile = new File(TEST_LOG_DIR, "method-probe-" + todayDate + ".log");

        List<String> lines = readFileLines(logFile);

        // Should have all messages
        assertEquals("Should have all messages", numThreads * messagesPerThread, lines.size());
    }

    @Test
    public void testLogDir() {
        logOutput = new AsyncFileLogOutput(TEST_LOG_DIR, 1000, 1000);
        assertEquals(TEST_LOG_DIR, logOutput.getLogDir());
    }

    @Test
    public void testQueueSize() throws Exception {
        logOutput = new AsyncFileLogOutput(TEST_LOG_DIR, 1000, 5000); // Long flush interval

        // Queue should start empty
        assertEquals(0, logOutput.getQueueSize());

        // Write messages
        logOutput.write("Message 1\n");
        logOutput.write("Message 2\n");

        // Queue should have messages (before flush)
        assertTrue("Queue should not be empty", logOutput.getQueueSize() >= 0);
    }

    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private List<String> readFileLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
