package com.methodprobe.agent.snapshot;

/**
 * ThreadLocal context for storing method arguments during execution.
 * Args are stored at method entry and retrieved at method exit if snapshot is
 * needed.
 */
public class SnapshotContext {

    private static final ThreadLocal<Object[]> ARGS = new ThreadLocal<>();

    /**
     * Store arguments at method entry.
     */
    public static void setArgs(Object[] args) {
        ARGS.set(args);
    }

    /**
     * Get stored arguments at method exit.
     */
    public static Object[] getArgs() {
        return ARGS.get();
    }

    /**
     * Clear context (must be called at method exit).
     */
    public static void clear() {
        ARGS.remove();
    }
}
