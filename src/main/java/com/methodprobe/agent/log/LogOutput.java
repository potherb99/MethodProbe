package com.methodprobe.agent.log;

/**
 * Log output interface for method probe logs.
 * Implementations can output to console, file, or other destinations.
 */
public interface LogOutput {

    /**
     * Write a log message.
     * 
     * @param message the log message to write
     */
    void write(String message);

    /**
     * Flush any buffered content.
     * For synchronous implementations, this may be a no-op.
     */
    default void flush() {
        // Default no-op
    }

    /**
     * Shutdown the log output, releasing any resources.
     * Should ensure all pending messages are written before returning.
     */
    default void shutdown() {
        // Default no-op
    }
}
