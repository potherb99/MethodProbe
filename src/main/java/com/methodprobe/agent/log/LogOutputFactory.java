package com.methodprobe.agent.log;

import com.methodprobe.agent.config.AgentConfig;

/**
 * Factory for creating LogOutput instances based on configuration.
 */
public class LogOutputFactory {

    private static volatile LogOutput instance;
    private static final Object lock = new Object();

    /**
     * Get the configured LogOutput instance (singleton).
     */
    public static LogOutput getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = createLogOutput();
                }
            }
        }
        return instance;
    }

    /**
     * Create a LogOutput based on current configuration.
     */
    private static LogOutput createLogOutput() {
        String mode = AgentConfig.getOutputMode();

        if ("file".equalsIgnoreCase(mode)) {
            return new AsyncFileLogOutput(
                    AgentConfig.getOutputDir(),
                    AgentConfig.getOutputBufferSize(),
                    AgentConfig.getOutputFlushInterval());
        } else {
            // Default to console
            return ConsoleLogOutput.getInstance();
        }
    }

    /**
     * Initialize the LogOutput. Should be called during agent startup.
     */
    public static void init() {
        getInstance();
        System.out.println("[MethodProbe] LogOutput initialized: " + instance.getClass().getSimpleName());
    }

    /**
     * Shutdown the LogOutput. Should be called during agent shutdown.
     */
    public static void shutdown() {
        if (instance != null) {
            instance.shutdown();
        }
    }

    /**
     * Get a LogOutput for writing log messages.
     * This is the main method to use from other classes.
     */
    public static void write(String message) {
        getInstance().write(message);
    }

    /**
     * Flush any pending log messages.
     */
    public static void flush() {
        if (instance != null) {
            instance.flush();
        }
    }
}
