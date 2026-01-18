package com.methodprobe.agent.log;

/**
 * Console log output implementation.
 * Writes log messages directly to System.out (synchronous).
 */
public class ConsoleLogOutput implements LogOutput {

    private static final ConsoleLogOutput INSTANCE = new ConsoleLogOutput();

    private ConsoleLogOutput() {
        // Singleton
    }

    public static ConsoleLogOutput getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(String message) {
        System.out.print(message);
    }

    @Override
    public void flush() {
        System.out.flush();
    }
}
