package com.methodprobe.agent;

import com.methodprobe.agent.config.AgentConfig;

/**
 * Simplified reporter - since we now log each method call individually,
 * this class just provides a way to view current configuration.
 */
public class StatsReporter {

    /**
     * Start the reporter (placeholder for future enhancements).
     */
    public static void start() {
        long intervalSeconds = AgentConfig.getReportIntervalSeconds();
        if (intervalSeconds > 0) {
            System.out.println("[MethodProbe] Real-time logging enabled");
            System.out.println("[MethodProbe] Threshold: " + AgentConfig.getFlatThresholdMs() + "ms");
        }
    }

    /**
     * Stop the reporter (placeholder).
     */
    public static void stop() {
        // No-op for single-log mode
    }

    /**
     * Get current configuration as JSON.
     */
    public static String getConfigAsJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");

        // Flat Mode
        json.append("\"flat\":{");
        json.append("\"enabled\":").append(AgentConfig.isFlatEnabled()).append(",");
        json.append("\"thresholdMs\":").append(AgentConfig.getFlatThresholdMs()).append(",");
        json.append("\"trigger\":\"")
                .append(getTriggerString(AgentConfig.flatTriggerOnTimeout, AgentConfig.flatTriggerOnException))
                .append("\",");
        json.append("\"packages\":").append(toJsonArray(AgentConfig.getFlatPackages())).append(",");
        json.append("\"classes\":").append(toJsonArray(AgentConfig.getFlatClasses())).append(",");
        json.append("\"methods\":").append(toJsonArray(AgentConfig.getFlatMethods()));
        json.append("},");

        // Tree Mode
        json.append("\"tree\":{");
        json.append("\"enabled\":").append(AgentConfig.isTreeEnabled()).append(",");
        json.append("\"thresholdMs\":").append(AgentConfig.getTreeThresholdMs()).append(",");
        json.append("\"trigger\":\"")
                .append(getTriggerString(AgentConfig.treeTriggerOnTimeout, AgentConfig.treeTriggerOnException))
                .append("\",");
        json.append("\"entryMethods\":").append(toJsonArray(AgentConfig.getTreeEntryMethods())).append(",");
        json.append("\"packages\":").append(toJsonArray(AgentConfig.getTreePackages()));
        json.append("},");

        // Output Configuration
        json.append("\"output\":{");
        json.append("\"mode\":\"").append(escapeJson(AgentConfig.getOutputMode())).append("\",");
        json.append("\"dir\":\"").append(escapeJson(AgentConfig.getOutputDir())).append("\",");
        json.append("\"bufferSize\":").append(AgentConfig.getOutputBufferSize()).append(",");
        json.append("\"flushInterval\":").append(AgentConfig.getOutputFlushInterval());
        json.append("},");

        // Snapshot Configuration
        json.append("\"snapshot\":{");
        json.append("\"enabled\":").append(AgentConfig.snapshotEnabled).append(",");
        json.append("\"dir\":\"").append(escapeJson(AgentConfig.snapshotDir)).append("\",");
        json.append("\"maxObjectSize\":").append(AgentConfig.snapshotMaxObjectSize).append(",");
        json.append("\"retentionDays\":").append(AgentConfig.snapshotRetentionDays).append(",");
        json.append("\"serializeSync\":").append(AgentConfig.snapshotSerializeSync).append(",");
        json.append("\"treeSnapshotProbeAll\":").append(AgentConfig.treeSnapshotProbeAll);
        json.append("},");

        // Exception Filter Configuration
        json.append("\"exception\":{");
        json.append("\"include\":").append(toJsonArray(AgentConfig.getExceptionInclude())).append(",");
        json.append("\"exclude\":").append(toJsonArray(AgentConfig.getExceptionExclude())).append(",");
        json.append("\"stackDepth\":").append(AgentConfig.getExceptionStackDepth());
        json.append("},");

        // General Configuration
        json.append("\"general\":{");
        json.append("\"httpPort\":").append(AgentConfig.getHttpPort());
        json.append("}");

        json.append("}");
        return json.toString();
    }

    private static String toJsonArray(java.util.Set<String> set) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String item : set) {
            if (!first)
                sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(item)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String getTriggerString(boolean onTimeout, boolean onException) {
        if (onTimeout && onException)
            return "both";
        if (onTimeout)
            return "timeout";
        if (onException)
            return "exception";
        return "timeout"; // default
    }

    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
