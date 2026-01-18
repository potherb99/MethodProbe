package com.methodprobe.agent.tree;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;

import com.methodprobe.agent.config.AgentConfig;
import com.methodprobe.agent.log.LogOutputFactory;
import com.methodprobe.agent.snapshot.ExceptionInfo;
import com.methodprobe.agent.snapshot.MethodSnapshot;
import com.methodprobe.agent.snapshot.SnapshotIdGenerator;
import com.methodprobe.agent.snapshot.SnapshotSerializer;
import com.methodprobe.agent.snapshot.SnapshotWriter;

/**
 * ThreadLocal context manager for building method call trees.
 * 
 * Each thread maintains its own call stack. When an entry method is detected,
 * a new call tree is started. When the entry method exits, the entire tree
 * is printed and the context is cleared.
 * 
 * IMPORTANT: This class is designed to be called from ByteBuddy Advice code.
 * All methods must be public static and thread-safe.
 */
public class CallTreeContext {

    /**
     * ThreadLocal holding the current call stack for each thread.
     * The stack contains method nodes from root (entry method) to current method.
     */
    private static final ThreadLocal<Deque<MethodCallNode>> CALL_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * ThreadLocal holding the root node of the current call tree.
     */
    private static final ThreadLocal<MethodCallNode> ROOT_NODE = new ThreadLocal<>();

    /**
     * Called when entering a method.
     * 
     * @param className  fully qualified class name
     * @param methodName method name
     * @param args       method arguments (for snapshot)
     * @return true if this method should be tracked (is entry or within tree)
     */
    public static boolean onMethodEnter(String className, String methodName, Object[] args) {
        // Check if Tree mode is enabled
        if (!AgentConfig.isTreeEnabled()) {
            return false;
        }

        String fullMethodName = className + "." + methodName;
        Deque<MethodCallNode> stack = CALL_STACK.get();

        // Check if this is an entry method
        boolean isEntryMethod = AgentConfig.isTreeEntryMethod(className, methodName);

        // Determine if we should capture args for snapshot
        boolean captureArgs = AgentConfig.snapshotEnabled &&
                (AgentConfig.treeSnapshotProbeAll || isEntryMethod);

        if (isEntryMethod) {
            // Start a new call tree
            MethodCallNode rootNode = new MethodCallNode(fullMethodName, System.nanoTime());
            if (captureArgs) {
                rootNode.setArgs(args);
                rootNode.setSnapshotId(SnapshotIdGenerator.generate());
            }
            ROOT_NODE.set(rootNode);
            stack.clear();
            stack.push(rootNode);
            return true;
        }

        // Check if we are currently in a tree (entry method was called)
        MethodCallNode root = ROOT_NODE.get();
        if (root == null) {
            // Not in a call tree, skip this method
            return false;
        }

        // Check if this method should be included in the tree
        if (!AgentConfig.shouldIncludeInTree(className)) {
            return false;
        }

        // Create node and add to current parent
        MethodCallNode node = new MethodCallNode(fullMethodName, System.nanoTime());
        if (captureArgs) {
            node.setArgs(args);
            node.setSnapshotId(SnapshotIdGenerator.generate());
        }
        MethodCallNode parent = stack.peek();
        if (parent != null) {
            parent.addChild(node);
        }
        stack.push(node);

        return true;
    }

    /**
     * Called when exiting a method.
     * 
     * @param className  fully qualified class name
     * @param methodName method name
     * @param thrown     any exception thrown (null if none)
     */
    public static void onMethodExit(String className, String methodName, Throwable thrown) {
        MethodCallNode root = ROOT_NODE.get();
        if (root == null) {
            // Not in a call tree
            return;
        }

        Deque<MethodCallNode> stack = CALL_STACK.get();
        if (stack.isEmpty()) {
            return;
        }

        String fullMethodName = className + "." + methodName;
        MethodCallNode currentNode = stack.peek();

        // Verify we're popping the correct node
        if (currentNode != null && currentNode.getMethodName().equals(fullMethodName)) {
            currentNode.setEndTime(System.nanoTime());

            // Check if exception should be captured (based on include/exclude filters)
            boolean captureException = thrown != null && AgentConfig.shouldCaptureException(thrown);

            // Set exception on node if thrown and passes filter
            if (captureException) {
                currentNode.setException(thrown);
            }

            // Determine if tree trigger conditions are met
            boolean triggerMet = false;
            // Trigger on timeout
            if (AgentConfig.treeTriggerOnTimeout &&
                    currentNode.getDurationMs() >= AgentConfig.getTreeThresholdMs()) {
                triggerMet = true;
            }
            // Trigger on exception (only if matches filter)
            if (AgentConfig.treeTriggerOnException && captureException) {
                triggerMet = true;
            }

            // Create snapshot if trigger conditions met and snapshot enabled
            if (triggerMet && currentNode.getSnapshotId() != null && AgentConfig.snapshotEnabled) {
                createNodeSnapshot(currentNode, thrown);
            }

            stack.pop();

            // Check if this is the entry method exiting
            boolean isEntryMethod = AgentConfig.isTreeEntryMethod(className, methodName);
            if (isEntryMethod && stack.isEmpty()) {
                // Entry method is exiting - determine if tree should be printed
                double totalDurationMs = root.getDurationMs();
                boolean shouldPrint = false;

                // Trigger on timeout
                if (AgentConfig.treeTriggerOnTimeout && totalDurationMs >= AgentConfig.getTreeThresholdMs()) {
                    shouldPrint = true;
                }
                // Trigger on exception (only if passes filter)
                if (AgentConfig.treeTriggerOnException && captureException) {
                    shouldPrint = true;
                }

                if (shouldPrint) {
                    // Capture root for async printing (root will be cleared below)
                    final MethodCallNode capturedRoot = root;
                    // Only pass thrown if it should be captured (passes filter)
                    final Throwable capturedThrown = captureException ? thrown : null;
                    AsyncTreePrinter.submit(() -> printCallTree(capturedRoot, capturedThrown));
                }

                // Clear the context for thread reuse
                ROOT_NODE.remove();
                CALL_STACK.get().clear();
            }
        }
    }

    /**
     * Create snapshot for a tree node.
     */
    private static void createNodeSnapshot(MethodCallNode node, Throwable thrown) {
        try {
            String[] parts = node.getMethodName().split("\\.");
            String methodName = parts.length > 0 ? parts[parts.length - 1] : node.getMethodName();
            String className = node.getMethodName().substring(0,
                    node.getMethodName().length() - methodName.length() - 1);

            MethodSnapshot snapshot = new MethodSnapshot(
                    node.getSnapshotId(),
                    System.currentTimeMillis(),
                    className,
                    methodName,
                    Thread.currentThread().getName(),
                    node.getDurationMs());

            Object[] args = node.getArgs();
            snapshot.setArgTypes(SnapshotSerializer.getArgTypes(args));
            byte[][] serializedArgs = SnapshotSerializer.serializeArgs(args);
            snapshot.setSerializedArgs(serializedArgs);
            // Only serialize exception if it passes the filter
            if (thrown != null && AgentConfig.shouldCaptureException(thrown)) {
                int stackDepth = AgentConfig.getExceptionStackDepth();
                snapshot.setSerializedException(SnapshotSerializer.serialize(new ExceptionInfo(thrown, stackDepth)));
            }
            SnapshotWriter.submitSerialized(snapshot);
        } catch (Exception e) {
            System.err.println("[MethodProbe] Failed to create tree snapshot: " + e.getMessage());
        }
    }

    /**
     * Print the call tree to console with tree-style formatting.
     */
    private static void printCallTree(MethodCallNode root, Throwable thrown) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String timestamp = sdf.format(new Date());
        String threadName = Thread.currentThread().getName();

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════════\n");
        sb.append(String.format("║ [%s] [%s] Method Call Tree\n", timestamp, threadName));
        if (thrown != null) {
            sb.append(String.format("║ ⚠ Exception: %s\n", thrown.getClass().getName()));
        }
        sb.append("╠══════════════════════════════════════════════════════════════════════════════\n");

        // Print tree recursively
        printNode(sb, root, "", true);

        sb.append("╚══════════════════════════════════════════════════════════════════════════════\n");

        LogOutputFactory.write(sb.toString());
    }

    /**
     * Recursively print a node and its children with tree branches.
     */
    private static void printNode(StringBuilder sb, MethodCallNode node, String prefix, boolean isLast) {
        String connector = isLast ? "└── " : "├── ";
        String durationStr = String.format("%.2f ms", node.getDurationMs());

        sb.append("║ ");
        sb.append(prefix);
        sb.append(connector);
        sb.append(node.getMethodName());
        sb.append(" - ");
        sb.append(durationStr);

        // Show exception tag if node has exception
        if (node.hasException()) {
            sb.append(" [EXCEPTION: ");
            sb.append(node.getException().getClass().getSimpleName());
            sb.append("]");
        }

        // Show snapshot ID if available (instead of args)
        String snapshotId = node.getSnapshotId();
        if (snapshotId != null) {
            sb.append(" [snap:");
            sb.append(snapshotId);
            sb.append("]");
        }
        sb.append("\n");

        // Print children
        String childPrefix = prefix + (isLast ? "    " : "│   ");
        for (int i = 0; i < node.getChildren().size(); i++) {
            boolean childIsLast = (i == node.getChildren().size() - 1);
            printNode(sb, node.getChildren().get(i), childPrefix, childIsLast);
        }
    }

    /**
     * Format an argument for display (truncate long values).
     */
    private static String formatArg(Object arg) {
        if (arg == null)
            return "null";
        String str = arg.toString();
        if (str.length() > 50) {
            return str.substring(0, 47) + "...";
        }
        return str;
    }

    /**
     * Check if the current thread is within an active call tree.
     */
    public static boolean isInTree() {
        return ROOT_NODE.get() != null;
    }

    /**
     * Get current tree depth for the calling thread.
     */
    public static int getCurrentDepth() {
        Deque<MethodCallNode> stack = CALL_STACK.get();
        return stack.size();
    }

    /**
     * Force clear the context (useful for cleanup).
     */
    public static void clear() {
        ROOT_NODE.remove();
        CALL_STACK.get().clear();
    }
}
