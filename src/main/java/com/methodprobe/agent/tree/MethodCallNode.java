package com.methodprobe.agent.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single method call node in the call tree.
 * Each node records method execution time and contains references
 * to child method calls.
 */
public class MethodCallNode {

    private final String methodName;
    private final long startTimeNanos;
    private long endTimeNanos;
    private MethodCallNode parent;
    private final List<MethodCallNode> children;
    private Object[] args; // Method arguments for snapshot
    private String snapshotId; // Unique ID for log-snapshot correlation

    public MethodCallNode(String methodName, long startTimeNanos) {
        this.methodName = methodName;
        this.startTimeNanos = startTimeNanos;
        this.children = new ArrayList<>();
    }

    public MethodCallNode(String methodName, long startTimeNanos, Object[] args) {
        this(methodName, startTimeNanos);
        this.args = args;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    // Exception information for exception-triggered printing
    private Throwable exception;

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public boolean hasException() {
        return exception != null;
    }

    public void addChild(MethodCallNode child) {
        child.parent = this;
        children.add(child);
    }

    public void setEndTime(long endTimeNanos) {
        this.endTimeNanos = endTimeNanos;
    }

    public String getMethodName() {
        return methodName;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public long getEndTimeNanos() {
        return endTimeNanos;
    }

    public double getDurationMs() {
        return (endTimeNanos - startTimeNanos) / 1_000_000.0;
    }

    public MethodCallNode getParent() {
        return parent;
    }

    public List<MethodCallNode> getChildren() {
        return children;
    }

    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Calculate the depth of this node in the tree.
     */
    public int getDepth() {
        int depth = 0;
        MethodCallNode current = parent;
        while (current != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }
}
