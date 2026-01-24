package com.methodprobe.agent.log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages WebSocket server for real-time log broadcasting.
 * Uses async queue to prevent blocking business threads.
 */
public class WebSocketLogManager {

    private static LogWebSocketServer wsServer;
    private static int port;
    private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(10000);
    private static Thread broadcastThread;
    private static volatile boolean running = false;

    public static synchronized void init(int port) {
        if (wsServer == null) {
            try {
                WebSocketLogManager.port = port;
                wsServer = new LogWebSocketServer(port);
                wsServer.start();

                // Start async broadcast thread
                startBroadcastThread();

                System.out.println("[MethodProbe] WebSocket Log Server initialized on port: " + port);
            } catch (Exception e) {
                System.err.println("[MethodProbe] Failed to start WebSocket Log Server: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Start dedicated thread for async message broadcasting.
     */
    private static void startBroadcastThread() {
        running = true;
        broadcastThread = new Thread(() -> {
            while (running) {
                try {
                    // Block until message available (with timeout for graceful shutdown)
                    String message = messageQueue.poll(1, TimeUnit.SECONDS);
                    if (message != null && wsServer != null && wsServer.hasClients()) {
                        wsServer.broadcastLog(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Continue on error to keep thread alive
                    System.err.println("[MethodProbe] Broadcast error: " + e.getMessage());
                }
            }
        }, "MethodProbe-WS-Broadcast");
        broadcastThread.setDaemon(true);
        broadcastThread.start();
    }

    /**
     * Async broadcast: enqueue message without blocking.
     * If queue is full, silently drop message to prevent memory issues.
     */
    public static void broadcast(String message) {
        if (wsServer != null && wsServer.hasClients()) {
            // Non-blocking offer: returns false if queue full
            if (!messageQueue.offer(message)) {
                // Queue full - drop message silently
                // Alternative: could log warning, but that might cause recursion
            }
        }
    }

    public static synchronized void stop() {
        if (wsServer != null) {
            try {
                System.out.println("[MethodProbe] Stopping WebSocket Log Server...");

                // Stop broadcast thread
                running = false;
                if (broadcastThread != null) {
                    broadcastThread.interrupt();
                    try {
                        broadcastThread.join(2000); // Wait up to 2 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // Clear queue
                messageQueue.clear();

                // Stop WebSocket server
                wsServer.stop();
                wsServer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static int getPort() {
        return port;
    }
}
