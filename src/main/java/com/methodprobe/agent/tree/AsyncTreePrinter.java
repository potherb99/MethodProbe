package com.methodprobe.agent.tree;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async executor for tree building and printing.
 * Uses a single daemon thread to process tree print tasks without blocking
 * business threads.
 */
public class AsyncTreePrinter {

    private static volatile ExecutorService executor;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Initialize the async executor.
     */
    public static void init() {
        if (initialized.compareAndSet(false, true)) {
            executor = new ThreadPoolExecutor(
                    1, 1,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1000),
                    r -> {
                        Thread t = new Thread(r, "MethodProbe-TreePrinter");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.DiscardOldestPolicy());
            System.out.println("[MethodProbe] AsyncTreePrinter initialized");
        }
    }

    /**
     * Submit a tree print task for async execution.
     */
    public static void submit(Runnable task) {
        if (executor != null && !executor.isShutdown()) {
            executor.submit(task);
        }
    }

    /**
     * Shutdown the executor gracefully.
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
            System.out.println("[MethodProbe] AsyncTreePrinter shutdown complete");
        }
    }
}
