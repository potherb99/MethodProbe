package com.methodprobe.agent;

import net.bytebuddy.asm.Advice;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.methodprobe.agent.config.AgentConfig;
import com.methodprobe.agent.log.LogOutputFactory;
import com.methodprobe.agent.tree.CallTreeContext;
import com.methodprobe.agent.snapshot.ExceptionInfo;
import com.methodprobe.agent.snapshot.MethodSnapshot;
import com.methodprobe.agent.snapshot.SnapshotIdGenerator;
import com.methodprobe.agent.snapshot.SnapshotSerializer;
import com.methodprobe.agent.snapshot.SnapshotWriter;

/**
 * ByteBuddy Advice class for method probe instrumentation.
 * This class is inlined into target methods at bytecode level.
 * 
 * Supports two modes:
 * 1. Tree mode: When entry methods are configured, builds a call tree and
 * prints the tree structure when the entry method exits.
 * 2. Flat mode: When not in a call tree, logs individual method probes.
 * 
 * Also supports snapshot capture when execution exceeds threshold.
 * 
 * IMPORTANT: Do NOT use static fields here! Advice code is inlined into
 * target classes and cannot access fields from the Advice class due to
 * classloader isolation.
 */
public class ProbeAdvice {

    /**
     * Called at method entry - records start time and handles tree tracking.
     * 
     * @param className  the declaring class name
     * @param methodName the method name
     * @param args       all method arguments (for snapshot)
     * @return start time in nanoseconds
     */
    @Advice.OnMethodEnter
    public static long onEnter(
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.AllArguments Object[] args) {

        // Try to add to call tree (pass args for Tree mode snapshot)
        CallTreeContext.onMethodEnter(className, methodName, args);

        return System.nanoTime();
    }

    /**
     * Called at method exit - logs execution time if above threshold or on
     * exception.
     * If in tree mode, the tree will be printed when the entry method exits.
     * If snapshot is enabled and trigger conditions met, creates a snapshot.
     * 
     * @param startTime  start time from onEnter
     * @param className  the declaring class name
     * @param methodName the method name
     * @param args       all method arguments (captured for snapshot)
     * @param thrown     any exception thrown (null if none)
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Enter long startTime,
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.AllArguments Object[] args,
            @Advice.Thrown Throwable thrown) {

        // Calculate duration first
        long durationNanos = System.nanoTime() - startTime;
        double durationMs = durationNanos / 1_000_000.0;
        boolean isException = thrown != null;

        // Handle tree tracking exit
        CallTreeContext.onMethodExit(className, methodName, thrown);

        // Check if exception should be captured (based on include/exclude filters)
        boolean captureException = isException && AgentConfig.shouldCaptureException(thrown);

        // Flat mode probe logic
        if (AgentConfig.isFlatEnabled()
                && !CallTreeContext.isInTree()
                && AgentConfig.shouldLogFlatMethod(className, methodName)) {

            // Get trigger configuration
            boolean triggerOnTimeout = AgentConfig.flatTriggerOnTimeout;
            boolean triggerOnException = AgentConfig.flatTriggerOnException;
            long thresholdMs = AgentConfig.getFlatThresholdMs();

            // Determine if should log (based on flat trigger config)
            boolean shouldLog = false;
            if (triggerOnTimeout && durationMs >= thresholdMs) {
                shouldLog = true;
            }
            // Only log exception if matches filter
            if (triggerOnException && captureException) {
                shouldLog = true;
            }

            if (shouldLog) {
                // Generate snapshot if enabled (follows flat trigger decision)
                String snapshotId = null;
                if (AgentConfig.snapshotEnabled) {
                    snapshotId = SnapshotIdGenerator.generate();
                    createSnapshot(snapshotId, className, methodName, durationMs, args, thrown);
                }

                // Create formatter inline - avoid static fields!
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String timestamp = sdf.format(new Date());
                String threadName = Thread.currentThread().getName();
                String fullMethodName = className + "." + methodName;

                // Build exception tag if exception passes filter
                String exceptionTag = "";
                if (captureException) {
                    exceptionTag = String.format(" [EXCEPTION: %s]", thrown.getClass().getSimpleName());
                }

                // Print log entry with snapshot ID if available
                String logMessage;
                if (snapshotId != null) {
                    logMessage = String.format("[%s] [MethodProbe] [%s] %s - %.2f ms%s [snap:%s]%n",
                            timestamp, threadName, fullMethodName, durationMs, exceptionTag, snapshotId);
                } else {
                    logMessage = String.format("[%s] [MethodProbe] [%s] %s - %.2f ms%s%n",
                            timestamp, threadName, fullMethodName, durationMs, exceptionTag);
                }
                LogOutputFactory.write(logMessage);
            }
        }
    }

    /**
     * Create and submit a method snapshot.
     * NOTE: Must be public for ByteBuddy Advice inlined code access.
     */
    public static void createSnapshot(String snapshotId, String className, String methodName,
            double durationMs, Object[] args, Throwable thrown) {
        try {
            MethodSnapshot snapshot = new MethodSnapshot(
                    snapshotId,
                    System.currentTimeMillis(),
                    className,
                    methodName,
                    Thread.currentThread().getName(),
                    durationMs);

            // Get arg types
            snapshot.setArgTypes(SnapshotSerializer.getArgTypes(args));

            // Serialize based on mode
            if (AgentConfig.snapshotSerializeSync) {
                // Sync mode: serialize in business thread, write async
                byte[][] serializedArgs = SnapshotSerializer.serializeArgs(args);
                snapshot.setSerializedArgs(serializedArgs);
                // Only serialize exception if it passes the filter
                if (thrown != null && AgentConfig.shouldCaptureException(thrown)) {
                    int stackDepth = AgentConfig.getExceptionStackDepth();
                    snapshot.setSerializedException(
                            SnapshotSerializer.serialize(new ExceptionInfo(thrown, stackDepth)));
                }
                SnapshotWriter.submitSerialized(snapshot);
            } else {
                // Async mode: serialize and write in async thread (risk of data inconsistency)
                snapshot.setSerializedArgs(SnapshotSerializer.serializeArgs(args));
                // Only serialize exception if it passes the filter
                if (thrown != null && AgentConfig.shouldCaptureException(thrown)) {
                    int stackDepth = AgentConfig.getExceptionStackDepth();
                    snapshot.setSerializedException(
                            SnapshotSerializer.serialize(new ExceptionInfo(thrown, stackDepth)));
                }
                SnapshotWriter.submit(snapshot);
            }
        } catch (Exception e) {
            System.err.println("[MethodProbe] Failed to create snapshot: " + e.getMessage());
        }
    }
}
