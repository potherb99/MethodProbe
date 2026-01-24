package com.methodprobe.agent.snapshot;

import com.methodprobe.agent.config.AgentConfig;

public class SnapshotHelper {

    /**
     * Create and submit a method snapshot.
     * Use this helper instead of placing logic directly in Advice to avoid
     * ClassFormatErrors during retransformation (prevent adding methods to target
     * class).
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
