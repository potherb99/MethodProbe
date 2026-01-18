package com.methodprobe.agent.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Agent configuration management.
 * Supports separate Flat and Tree mode configurations.
 */
public class AgentConfig {

    // ==================== Exclude Patterns ====================
    private static final Set<String> excludePatterns = ConcurrentHashMap.newKeySet();

    // ==================== Flat Mode Configuration ====================
    private static boolean flatEnabled = true;
    private static final Set<String> flatPackages = ConcurrentHashMap.newKeySet();
    private static final Set<String> flatClasses = ConcurrentHashMap.newKeySet();
    private static final Set<String> flatMethods = ConcurrentHashMap.newKeySet();
    private static long flatThresholdMs = 0;

    // ==================== Tree Mode Configuration ====================
    private static boolean treeEnabled = true;
    private static final Set<String> treeEntryMethods = ConcurrentHashMap.newKeySet();
    private static final Set<String> treePackages = ConcurrentHashMap.newKeySet();
    private static long treeThresholdMs = 0;

    // ==================== General Configuration ====================
    private static int httpPort = 9876;
    private static long reportIntervalSeconds = 30;

    // ==================== Log Output Configuration ====================
    private static String outputMode = "console";
    private static String outputDir = "./probe-logs";
    private static int outputBufferSize = 10000;
    private static long outputFlushInterval = 1000;

    // ==================== Snapshot Configuration (volatile for high-perf access)
    // ====================
    public static volatile boolean snapshotEnabled = false;
    public static volatile String snapshotDir = "./probe-snapshots";
    public static volatile int snapshotMaxObjectSize = 1048576; // 1MB
    public static volatile int snapshotRetentionDays = 7;
    public static volatile boolean snapshotSerializeSync = true; // true=sync, false=async
    public static volatile boolean treeSnapshotProbeAll = false; // true=all, false=entry_only

    // ==================== Trigger Mode Configuration ====================
    // Flat mode triggers
    public static volatile boolean flatTriggerOnTimeout = true;
    public static volatile boolean flatTriggerOnException = false;
    // Tree mode triggers
    public static volatile boolean treeTriggerOnTimeout = true;
    public static volatile boolean treeTriggerOnException = false;

    // ==================== Exception Filter Configuration ====================
    // Exception types to include (empty = include all)
    private static final Set<String> exceptionInclude = new CopyOnWriteArraySet<>();
    // Exception types to exclude (takes priority over include)
    private static final Set<String> exceptionExclude = new CopyOnWriteArraySet<>();
    // Maximum stack trace depth to capture (default: 10)
    public static volatile int exceptionStackDepth = 10;

    /**
     * Initialize configuration from agent arguments.
     */
    public static void init(String agentArgs) {
        // Add default exclusions
        excludePatterns.add("com.methodprobe.agent.");
        excludePatterns.add("java.");
        excludePatterns.add("javax.");
        excludePatterns.add("sun.");
        excludePatterns.add("jdk.");
        excludePatterns.add("net.bytebuddy.");

        if (agentArgs == null || agentArgs.isEmpty()) {
            System.out.println("[MethodProbe] No configuration provided, using defaults.");
            return;
        }

        // Parse agent arguments
        String configPath = null;
        for (String arg : agentArgs.split(",")) {
            String[] kv = arg.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();

                if ("config".equals(key)) {
                    configPath = value;
                } else if ("port".equals(key)) {
                    httpPort = Integer.parseInt(value);
                }
            }
        }

        // Load config file if specified
        if (configPath != null) {
            loadConfigFile(configPath);
        }

        printConfiguration();
    }

    /**
     * Load configuration from properties file.
     */
    private static void loadConfigFile(String path) {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(path)) {
            props.load(is);

            // Flat Mode
            String flatEnabledStr = props.getProperty("probe.flat.enabled", "true");
            flatEnabled = Boolean.parseBoolean(flatEnabledStr);

            String flatPkgs = props.getProperty("probe.flat.packages", "");
            if (!flatPkgs.isEmpty()) {
                addToSet(flatPackages, flatPkgs);
            }

            String flatCls = props.getProperty("probe.flat.classes", "");
            if (!flatCls.isEmpty()) {
                addToSet(flatClasses, flatCls);
            }

            String flatMtd = props.getProperty("probe.flat.methods", "");
            if (!flatMtd.isEmpty()) {
                addToSet(flatMethods, flatMtd);
            }

            String flatThreshold = props.getProperty("probe.flat.threshold", "0");
            flatThresholdMs = Long.parseLong(flatThreshold);

            // Tree Mode
            String treeEnabledStr = props.getProperty("probe.tree.enabled", "true");
            treeEnabled = Boolean.parseBoolean(treeEnabledStr);

            String treeEntry = props.getProperty("probe.tree.entry.methods", "");
            if (!treeEntry.isEmpty()) {
                addToSet(treeEntryMethods, treeEntry);
            }

            String treePkgs = props.getProperty("probe.tree.packages", "");
            if (!treePkgs.isEmpty()) {
                addToSet(treePackages, treePkgs);
            }

            String treeThreshold = props.getProperty("probe.tree.threshold", "0");
            treeThresholdMs = Long.parseLong(treeThreshold);

            // General
            String port = props.getProperty("probe.http.port", "");
            if (!port.isEmpty()) {
                httpPort = Integer.parseInt(port);
            }

            String interval = props.getProperty("probe.report.interval", "");
            if (!interval.isEmpty()) {
                reportIntervalSeconds = Long.parseLong(interval);
            }

            String exclude = props.getProperty("probe.exclude", "");
            if (!exclude.isEmpty()) {
                addToSet(excludePatterns, exclude);
            }

            // Log Output
            String mode = props.getProperty("probe.output.mode", "");
            if (!mode.isEmpty()) {
                outputMode = mode;
            }

            String dir = props.getProperty("probe.output.dir", "");
            if (!dir.isEmpty()) {
                outputDir = dir;
            }

            String bufferSize = props.getProperty("probe.output.buffer.size", "");
            if (!bufferSize.isEmpty()) {
                outputBufferSize = Integer.parseInt(bufferSize);
            }

            String flushInterval = props.getProperty("probe.output.flush.interval", "");
            if (!flushInterval.isEmpty()) {
                outputFlushInterval = Long.parseLong(flushInterval);
            }

            // Snapshot Configuration
            String snapEnabled = props.getProperty("probe.snapshot.enabled", "");
            if (!snapEnabled.isEmpty()) {
                snapshotEnabled = Boolean.parseBoolean(snapEnabled);
            }

            String snapDir = props.getProperty("probe.snapshot.dir", "");
            if (!snapDir.isEmpty()) {
                snapshotDir = snapDir;
            }

            String snapMaxSize = props.getProperty("probe.snapshot.max.object.size", "");
            if (!snapMaxSize.isEmpty()) {
                snapshotMaxObjectSize = Integer.parseInt(snapMaxSize);
            }

            String snapRetention = props.getProperty("probe.snapshot.retention.days", "");
            if (!snapRetention.isEmpty()) {
                snapshotRetentionDays = Integer.parseInt(snapRetention);
            }

            String snapSerializeMode = props.getProperty("probe.snapshot.serialize.mode", "");
            if (!snapSerializeMode.isEmpty()) {
                snapshotSerializeSync = "sync".equalsIgnoreCase(snapSerializeMode);
            }

            String treeSnapProbe = props.getProperty("probe.tree.snapshot.probe", "");
            if (!treeSnapProbe.isEmpty()) {
                treeSnapshotProbeAll = "all".equalsIgnoreCase(treeSnapProbe);
            }

            // Parse trigger configurations
            String flatTrigger = props.getProperty("probe.flat.trigger", "timeout");
            parseTriggerConfig(flatTrigger, true);

            String treeTrigger = props.getProperty("probe.tree.trigger", "timeout");
            parseTriggerConfig(treeTrigger, false);

            // Parse exception filter configuration
            String exInclude = props.getProperty("probe.exception.include", "");
            if (!exInclude.isEmpty()) {
                addToSet(exceptionInclude, exInclude);
            }

            String exExclude = props.getProperty("probe.exception.exclude", "");
            if (!exExclude.isEmpty()) {
                addToSet(exceptionExclude, exExclude);
            }

            String stackDepth = props.getProperty("probe.exception.stack.depth", "");
            if (!stackDepth.isEmpty()) {
                exceptionStackDepth = Integer.parseInt(stackDepth);
            }

        } catch (IOException e) {
            System.err.println("[MethodProbe] Failed to load config file: " + path);
            e.printStackTrace();
        }
    }

    private static void addToSet(Set<String> set, String values) {
        Arrays.stream(values.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(set::add);
    }

    /**
     * Parse trigger configuration string (e.g., "timeout", "exception",
     * "timeout,exception", "both")
     * 
     * @param triggerValue the trigger configuration value
     * @param isFlat       true for flat mode, false for tree mode
     */
    private static void parseTriggerConfig(String triggerValue, boolean isFlat) {
        String lowerValue = triggerValue.toLowerCase().trim();
        boolean onTimeout = lowerValue.contains("timeout") || "both".equals(lowerValue);
        boolean onException = lowerValue.contains("exception") || "both".equals(lowerValue);

        // Default to timeout if nothing specified
        if (!onTimeout && !onException) {
            onTimeout = true;
        }

        if (isFlat) {
            flatTriggerOnTimeout = onTimeout;
            flatTriggerOnException = onException;
        } else {
            treeTriggerOnTimeout = onTimeout;
            treeTriggerOnException = onException;
        }
    }

    /**
     * Check if the given exception should be captured based on include/exclude
     * filters.
     * 
     * @param thrown the exception to check
     * @return true if should capture, false if should ignore
     */
    public static boolean shouldCaptureException(Throwable thrown) {
        if (thrown == null) {
            return false;
        }

        String exceptionName = thrown.getClass().getSimpleName();
        String fullyQualifiedName = thrown.getClass().getName();

        // Check exclude list first (takes priority)
        if (!exceptionExclude.isEmpty()) {
            for (String pattern : exceptionExclude) {
                if (exceptionName.equals(pattern) || fullyQualifiedName.equals(pattern) ||
                        fullyQualifiedName.endsWith("." + pattern)) {
                    return false;
                }
            }
        }

        // If include list is empty, include all (that aren't excluded)
        if (exceptionInclude.isEmpty()) {
            return true;
        }

        // Check include list
        for (String pattern : exceptionInclude) {
            if (exceptionName.equals(pattern) || fullyQualifiedName.equals(pattern) ||
                    fullyQualifiedName.endsWith("." + pattern)) {
                return true;
            }
        }

        // Not in include list
        return false;
    }

    /**
     * Get the configured stack trace depth limit.
     */
    public static int getExceptionStackDepth() {
        return exceptionStackDepth;
    }

    public static void setExceptionStackDepth(int depth) {
        exceptionStackDepth = depth;
        System.out.println("[MethodProbe] Exception stack depth set to: " + depth);
    }

    public static Set<String> getExceptionInclude() {
        return Collections.unmodifiableSet(new HashSet<>(exceptionInclude));
    }

    public static Set<String> getExceptionExclude() {
        return Collections.unmodifiableSet(new HashSet<>(exceptionExclude));
    }

    public static void addExceptionInclude(String pattern) {
        if (pattern != null && !pattern.trim().isEmpty()) {
            exceptionInclude.add(pattern.trim());
            System.out.println("[MethodProbe] Added exception include: " + pattern);
        }
    }

    public static void addExceptionExclude(String pattern) {
        if (pattern != null && !pattern.trim().isEmpty()) {
            exceptionExclude.add(pattern.trim());
            System.out.println("[MethodProbe] Added exception exclude: " + pattern);
        }
    }

    public static boolean removeExceptionInclude(String pattern) {
        if (pattern != null && exceptionInclude.remove(pattern.trim())) {
            System.out.println("[MethodProbe] Removed exception include: " + pattern);
            return true;
        }
        return false;
    }

    public static boolean removeExceptionExclude(String pattern) {
        if (pattern != null && exceptionExclude.remove(pattern.trim())) {
            System.out.println("[MethodProbe] Removed exception exclude: " + pattern);
            return true;
        }
        return false;
    }

    private static void printConfiguration() {
        System.out.println("[MethodProbe] Configuration loaded:");
        System.out.println("  [Flat Mode]");
        System.out.println("    - enabled: " + flatEnabled);
        System.out.println("    - packages: " + flatPackages);
        System.out.println("    - classes: " + flatClasses);
        System.out.println("    - methods: " + flatMethods);
        System.out.println("    - threshold: " + flatThresholdMs + "ms");
        System.out.println("    - trigger: " + getTriggerDescription(flatTriggerOnTimeout, flatTriggerOnException));
        System.out.println("  [Tree Mode]");
        System.out.println("    - enabled: " + treeEnabled);
        System.out.println("    - entry.methods: " + treeEntryMethods);
        System.out.println("    - packages: " + treePackages);
        System.out.println("    - threshold: " + treeThresholdMs + "ms");
        System.out.println("    - trigger: " + getTriggerDescription(treeTriggerOnTimeout, treeTriggerOnException));
        System.out.println("  [Snapshot]");
        System.out.println("    - enabled: " + snapshotEnabled);
        System.out.println("    - dir: " + snapshotDir);
        System.out.println("  [Exception Filter]");
        System.out.println("    - include: " + (exceptionInclude.isEmpty() ? "(all)" : exceptionInclude));
        System.out.println("    - exclude: " + (exceptionExclude.isEmpty() ? "(none)" : exceptionExclude));
        System.out.println("    - stack.depth: " + exceptionStackDepth);
        System.out.println("  [General]");
        System.out.println("    - http.port: " + httpPort);
        System.out.println("    - output.mode: " + outputMode);
    }

    private static String getTriggerDescription(boolean onTimeout, boolean onException) {
        if (onTimeout && onException)
            return "timeout,exception";
        if (onTimeout)
            return "timeout";
        if (onException)
            return "exception";
        return "none";
    }

    // ==================== Instrumentation Check ====================

    /**
     * Check if a class should be instrumented (for either Flat or Tree mode).
     */
    public static boolean shouldInstrumentClass(String className) {
        // Check exclusions first
        if (isExcluded(className)) {
            return false;
        }

        // Check if needed for Flat mode
        if (flatEnabled && shouldInstrumentForFlat(className)) {
            return true;
        }

        // Check if needed for Tree mode
        if (treeEnabled && shouldInstrumentForTree(className)) {
            return true;
        }

        return false;
    }

    /**
     * Check if a class should be instrumented for Flat mode.
     */
    public static boolean shouldInstrumentForFlat(String className) {
        // Check packages
        for (String pkg : flatPackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }

        // Check classes
        if (flatClasses.contains(className)) {
            return true;
        }

        // Check methods (class part)
        for (String method : flatMethods) {
            int lastDot = method.lastIndexOf('.');
            if (lastDot > 0) {
                String methodClass = method.substring(0, lastDot);
                if (className.equals(methodClass)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a class should be instrumented for Tree mode.
     */
    public static boolean shouldInstrumentForTree(String className) {
        // Check tree packages
        for (String pkg : treePackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }

        // Check if class has entry method
        for (String entry : treeEntryMethods) {
            int lastDot = entry.lastIndexOf('.');
            if (lastDot > 0) {
                String entryClass = entry.substring(0, lastDot);
                if (className.equals(entryClass)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isExcluded(String className) {
        for (String pattern : excludePatterns) {
            if (pattern.endsWith("*")) {
                if (className.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            } else if (pattern.startsWith("*")) {
                if (className.endsWith(pattern.substring(1))) {
                    return true;
                }
            } else if (className.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    // ==================== Flat Mode Methods ====================

    public static boolean isFlatEnabled() {
        return flatEnabled;
    }

    public static void setFlatEnabled(boolean enabled) {
        flatEnabled = enabled;
        System.out.println("[MethodProbe] Flat mode " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Check if a specific method should be logged in Flat mode.
     */
    public static boolean shouldLogFlatMethod(String className, String methodName) {
        if (!flatEnabled) {
            return false;
        }

        String fullMethod = className + "." + methodName;

        // Check if specific method is configured
        if (flatMethods.contains(fullMethod)) {
            return true;
        }

        // Check if class is configured
        if (flatClasses.contains(className)) {
            return true;
        }

        // Check if package is configured
        for (String pkg : flatPackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }

        return false;
    }

    public static long getFlatThresholdMs() {
        return flatThresholdMs;
    }

    public static void setFlatThresholdMs(long threshold) {
        flatThresholdMs = threshold;
        System.out.println("[MethodProbe] Flat threshold set to: " + threshold + "ms");
    }

    public static Set<String> getFlatPackages() {
        return Collections.unmodifiableSet(new HashSet<>(flatPackages));
    }

    public static Set<String> getFlatClasses() {
        return Collections.unmodifiableSet(new HashSet<>(flatClasses));
    }

    public static Set<String> getFlatMethods() {
        return Collections.unmodifiableSet(new HashSet<>(flatMethods));
    }

    public static void addFlatPackage(String pkg) {
        if (pkg != null && !pkg.trim().isEmpty()) {
            flatPackages.add(pkg.trim());
            System.out.println("[MethodProbe] Added flat package: " + pkg);
        }
    }

    public static void addFlatClass(String cls) {
        if (cls != null && !cls.trim().isEmpty()) {
            flatClasses.add(cls.trim());
            System.out.println("[MethodProbe] Added flat class: " + cls);
        }
    }

    public static void addFlatMethod(String method) {
        if (method != null && !method.trim().isEmpty()) {
            flatMethods.add(method.trim());
            System.out.println("[MethodProbe] Added flat method: " + method);
        }
    }

    public static boolean removeFlatPackage(String pkg) {
        if (pkg != null && flatPackages.remove(pkg.trim())) {
            System.out.println("[MethodProbe] Removed flat package: " + pkg);
            return true;
        }
        return false;
    }

    public static boolean removeFlatClass(String cls) {
        if (cls != null && flatClasses.remove(cls.trim())) {
            System.out.println("[MethodProbe] Removed flat class: " + cls);
            return true;
        }
        return false;
    }

    public static boolean removeFlatMethod(String method) {
        if (method != null && flatMethods.remove(method.trim())) {
            System.out.println("[MethodProbe] Removed flat method: " + method);
            return true;
        }
        return false;
    }

    // ==================== Tree Mode Methods ====================

    public static boolean isTreeEnabled() {
        return treeEnabled;
    }

    public static void setTreeEnabled(boolean enabled) {
        treeEnabled = enabled;
        System.out.println("[MethodProbe] Tree mode " + (enabled ? "enabled" : "disabled"));
    }

    public static boolean isTreeEntryMethod(String className, String methodName) {
        String fullMethod = className + "." + methodName;
        return treeEntryMethods.contains(fullMethod);
    }

    public static boolean shouldIncludeInTree(String className) {
        // Entry method classes are always included
        for (String entry : treeEntryMethods) {
            int lastDot = entry.lastIndexOf('.');
            if (lastDot > 0) {
                String entryClass = entry.substring(0, lastDot);
                if (className.equals(entryClass)) {
                    return true;
                }
            }
        }

        // Check tree packages
        for (String pkg : treePackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    public static long getTreeThresholdMs() {
        return treeThresholdMs;
    }

    public static void setTreeThresholdMs(long threshold) {
        treeThresholdMs = threshold;
        System.out.println("[MethodProbe] Tree threshold set to: " + threshold + "ms");
    }

    public static Set<String> getTreeEntryMethods() {
        return Collections.unmodifiableSet(new HashSet<>(treeEntryMethods));
    }

    public static Set<String> getTreePackages() {
        return Collections.unmodifiableSet(new HashSet<>(treePackages));
    }

    public static void addTreeEntryMethod(String method) {
        if (method != null && !method.trim().isEmpty()) {
            treeEntryMethods.add(method.trim());
            System.out.println("[MethodProbe] Added tree entry method: " + method);
        }
    }

    public static void addTreePackage(String pkg) {
        if (pkg != null && !pkg.trim().isEmpty()) {
            treePackages.add(pkg.trim());
            System.out.println("[MethodProbe] Added tree package: " + pkg);
        }
    }

    public static boolean removeTreeEntryMethod(String method) {
        if (method != null && treeEntryMethods.remove(method.trim())) {
            System.out.println("[MethodProbe] Removed tree entry method: " + method);
            return true;
        }
        return false;
    }

    public static boolean removeTreePackage(String pkg) {
        if (pkg != null && treePackages.remove(pkg.trim())) {
            System.out.println("[MethodProbe] Removed tree package: " + pkg);
            return true;
        }
        return false;
    }

    // ==================== General Getters ====================

    public static int getHttpPort() {
        return httpPort;
    }

    public static long getReportIntervalSeconds() {
        return reportIntervalSeconds;
    }

    // ==================== Log Output Getters ====================

    public static String getOutputMode() {
        return outputMode;
    }

    public static void setOutputMode(String mode) {
        outputMode = mode;
        System.out.println("[MethodProbe] Output mode set to: " + mode);
    }

    public static String getOutputDir() {
        return outputDir;
    }

    public static void setOutputDir(String dir) {
        outputDir = dir;
        System.out.println("[MethodProbe] Output dir set to: " + dir);
    }

    public static int getOutputBufferSize() {
        return outputBufferSize;
    }

    public static long getOutputFlushInterval() {
        return outputFlushInterval;
    }

    // ==================== Backward Compatibility (Deprecated) ====================

    @Deprecated
    public static long getThresholdMs() {
        return flatThresholdMs;
    }

    @Deprecated
    public static void setThresholdMs(long threshold) {
        setFlatThresholdMs(threshold);
    }

    @Deprecated
    public static boolean isEntryMethod(String className, String methodName) {
        return isTreeEntryMethod(className, methodName);
    }
}
