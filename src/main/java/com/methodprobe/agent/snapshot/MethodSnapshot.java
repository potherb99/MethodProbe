package com.methodprobe.agent.snapshot;

import java.io.Serializable;

/**
 * Snapshot data model for capturing method execution state.
 */
public class MethodSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private String snapshotId; // Unique ID for log-snapshot correlation
    private long timestamp; // Capture timestamp
    private String className; // Fully qualified class name
    private String methodName; // Method name
    private String threadName; // Thread name
    private double durationMs; // Execution duration
    private String[] argTypes; // Argument type names
    private byte[][] serializedArgs; // Serialized arguments (Kryo bytes)
    private byte[] serializedException; // Serialized exception if any

    public MethodSnapshot() {
    }

    public MethodSnapshot(String snapshotId, long timestamp, String className, String methodName,
            String threadName, double durationMs) {
        this.snapshotId = snapshotId;
        this.timestamp = timestamp;
        this.className = className;
        this.methodName = methodName;
        this.threadName = threadName;
        this.durationMs = durationMs;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    // Getters and Setters
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public double getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(double durationMs) {
        this.durationMs = durationMs;
    }

    public String[] getArgTypes() {
        return argTypes;
    }

    public void setArgTypes(String[] argTypes) {
        this.argTypes = argTypes;
    }

    public byte[][] getSerializedArgs() {
        return serializedArgs;
    }

    public void setSerializedArgs(byte[][] serializedArgs) {
        this.serializedArgs = serializedArgs;
    }

    public byte[] getSerializedException() {
        return serializedException;
    }

    public void setSerializedException(byte[] serializedException) {
        this.serializedException = serializedException;
    }

    public String getFullMethodName() {
        return className + "." + methodName;
    }
}
