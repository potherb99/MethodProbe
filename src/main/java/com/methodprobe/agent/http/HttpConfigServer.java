package com.methodprobe.agent.http;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.methodprobe.agent.MethodProbeAgent;
import com.methodprobe.agent.StatsReporter;
import com.methodprobe.agent.config.AgentConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP server for dynamic configuration.
 * 
 * Endpoints:
 * /flat/class/add - Add a flat class (className=xxx)
 * /flat/package/add - Add a flat package (packageName=xxx)
 * /flat/threshold - Set flat threshold (threshold=xxx)
 * /flat/trigger - Set flat trigger (timeout=true/false, exception=true/false)
 * /tree/entry/add - Add tree entry method (method=xxx)
 * /tree/package/add - Add tree package (packageName=xxx)
 * /tree/threshold - Set tree threshold (threshold=xxx)
 * /tree/trigger - Set tree trigger (timeout=true/false, exception=true/false)
 * /snapshot/config - Configure snapshot
 * /config - Get current configuration
 * /admin - Management page
 */
public class HttpConfigServer {

    private static HttpServer server;

    /**
     * Start the HTTP configuration server.
     */
    public static void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Flat mode endpoints
            server.createContext("/flat/class/add", HttpConfigServer::handleFlatClassAdd);
            server.createContext("/flat/class/remove", HttpConfigServer::handleFlatClassRemove);
            server.createContext("/flat/package/add", HttpConfigServer::handleFlatPackageAdd);
            server.createContext("/flat/package/remove", HttpConfigServer::handleFlatPackageRemove);
            server.createContext("/flat/threshold", HttpConfigServer::handleFlatThreshold);
            server.createContext("/flat/trigger", HttpConfigServer::handleFlatTrigger);

            // Tree mode endpoints
            server.createContext("/tree/entry/add", HttpConfigServer::handleTreeEntryAdd);
            server.createContext("/tree/entry/remove", HttpConfigServer::handleTreeEntryRemove);
            server.createContext("/tree/package/add", HttpConfigServer::handleTreePackageAdd);
            server.createContext("/tree/package/remove", HttpConfigServer::handleTreePackageRemove);
            server.createContext("/tree/threshold", HttpConfigServer::handleTreeThreshold);
            server.createContext("/tree/trigger", HttpConfigServer::handleTreeTrigger);

            // Snapshot endpoint
            server.createContext("/snapshot/config", HttpConfigServer::handleSnapshotConfig);

            // Exception filter endpoints
            server.createContext("/exception/include/add", HttpConfigServer::handleExceptionIncludeAdd);
            server.createContext("/exception/include/remove", HttpConfigServer::handleExceptionIncludeRemove);
            server.createContext("/exception/exclude/add", HttpConfigServer::handleExceptionExcludeAdd);
            server.createContext("/exception/exclude/remove", HttpConfigServer::handleExceptionExcludeRemove);
            server.createContext("/exception/depth", HttpConfigServer::handleExceptionDepth);

            // General endpoints
            server.createContext("/config", HttpConfigServer::handleGetConfig);
            server.createContext("/admin", HttpConfigServer::handleAdminPage);
            server.createContext("/", HttpConfigServer::handleHelp);

            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "MethodProbe-HTTP");
                t.setDaemon(true);
                return t;
            }));

            server.start();
            System.out.println("[MethodProbe] HTTP server started on port " + port);

        } catch (IOException e) {
            System.err.println("[MethodProbe] Failed to start HTTP server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stop the HTTP server.
     */
    public static void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ==================== Flat Mode Handlers ====================

    private static void handleFlatClassAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String className = params.get("className");
        if (className == null || className.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing className\"}");
            return;
        }
        AgentConfig.addFlatClass(className);
        MethodProbeAgent.retransformClasses();
        sendResponse(exchange, 200, "{\"success\":true,\"className\":\"" + className + "\"}");
    }

    private static void handleFlatPackageAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String packageName = params.get("packageName");
        if (packageName == null || packageName.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing packageName\"}");
            return;
        }
        AgentConfig.addFlatPackage(packageName);
        MethodProbeAgent.retransformClasses();
        sendResponse(exchange, 200, "{\"success\":true,\"packageName\":\"" + packageName + "\"}");
    }

    private static void handleFlatClassRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String className = params.get("className");
        if (className == null || className.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing className\"}");
            return;
        }
        boolean removed = AgentConfig.removeFlatClass(className);
        sendResponse(exchange, 200, "{\"success\":" + removed + ",\"className\":\"" + className + "\"}");
    }

    private static void handleFlatPackageRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String packageName = params.get("packageName");
        if (packageName == null || packageName.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing packageName\"}");
            return;
        }
        boolean removed = AgentConfig.removeFlatPackage(packageName);
        sendResponse(exchange, 200, "{\"success\":" + removed + ",\"packageName\":\"" + packageName + "\"}");
        ;
    }

    private static void handleFlatThreshold(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String thresholdStr = params.get("threshold");
        if (thresholdStr == null || thresholdStr.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing threshold\"}");
            return;
        }
        try {
            long threshold = Long.parseLong(thresholdStr);
            AgentConfig.setFlatThresholdMs(threshold);
            sendResponse(exchange, 200, "{\"success\":true,\"thresholdMs\":" + threshold + "}");
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Invalid threshold\"}");
        }
    }

    private static void handleFlatTrigger(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String trigger = params.get("trigger");
        if (trigger != null) {
            parseTriggerAndApply(trigger, true);
        }
        String response = getTriggerResponse(AgentConfig.flatTriggerOnTimeout, AgentConfig.flatTriggerOnException);
        sendResponse(exchange, 200, "{\"success\":true,\"trigger\":\"" + response + "\"}");
    }

    // ==================== Tree Mode Handlers ====================

    private static void handleTreeEntryAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String method = params.get("method");
        if (method == null || method.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing method\"}");
            return;
        }
        AgentConfig.addTreeEntryMethod(method);
        MethodProbeAgent.retransformClasses();
        sendResponse(exchange, 200, "{\"success\":true,\"method\":\"" + method + "\"}");
    }

    private static void handleTreePackageAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String packageName = params.get("packageName");
        if (packageName == null || packageName.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing packageName\"}");
            return;
        }
        AgentConfig.addTreePackage(packageName);
        MethodProbeAgent.retransformClasses();
        sendResponse(exchange, 200, "{\"success\":true,\"packageName\":\"" + packageName + "\"}");
    }

    private static void handleTreeEntryRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String method = params.get("method");
        if (method == null || method.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing method\"}");
            return;
        }
        boolean removed = AgentConfig.removeTreeEntryMethod(method);
        sendResponse(exchange, 200, "{\"success\":" + removed + ",\"method\":\"" + method + "\"}");
    }

    private static void handleTreePackageRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String packageName = params.get("packageName");
        if (packageName == null || packageName.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing packageName\"}");
            return;
        }
        boolean removed = AgentConfig.removeTreePackage(packageName);
        sendResponse(exchange, 200, "{\"success\":" + removed + ",\"packageName\":\"" + packageName + "\"}");
        ;
    }

    private static void handleTreeThreshold(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String thresholdStr = params.get("threshold");
        if (thresholdStr == null || thresholdStr.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing threshold\"}");
            return;
        }
        try {
            long threshold = Long.parseLong(thresholdStr);
            AgentConfig.setTreeThresholdMs(threshold);
            sendResponse(exchange, 200, "{\"success\":true,\"thresholdMs\":" + threshold + "}");
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Invalid threshold\"}");
        }
    }

    // ==================== Exception Filter Handlers ====================

    private static void handleExceptionIncludeAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String pattern = params.get("pattern");
        if (pattern == null || pattern.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing pattern\"}");
            return;
        }
        AgentConfig.addExceptionInclude(pattern);
        sendResponse(exchange, 200, "{\"success\":true,\"pattern\":\"" + pattern + "\"}");
    }

    private static void handleExceptionIncludeRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String pattern = params.get("pattern");
        if (pattern == null || pattern.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing pattern\"}");
            return;
        }
        boolean removed = AgentConfig.removeExceptionInclude(pattern);
        sendResponse(exchange, 200, "{\"success\":" + removed + ",\"pattern\":\"" + pattern + "\"}");
    }

    private static void handleExceptionExcludeAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String pattern = params.get("pattern");
        if (pattern == null || pattern.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing pattern\"}");
            return;
        }
        AgentConfig.addExceptionExclude(pattern);
        sendResponse(exchange, 200, "{\"success\":true,\"pattern\":\"" + pattern + "\"}");
    }

    private static void handleExceptionExcludeRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String pattern = params.get("pattern");
        if (pattern == null || pattern.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing pattern\"}");
            return;
        }
        boolean removed = AgentConfig.removeExceptionExclude(pattern);
        sendResponse(exchange, 200, "{\"success\":" + removed + ",\"pattern\":\"" + pattern + "\"}");
    }

    private static void handleExceptionDepth(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String depthStr = params.get("depth");
        if (depthStr == null || depthStr.isEmpty()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing depth\"}");
            return;
        }
        try {
            int depth = Integer.parseInt(depthStr);
            AgentConfig.setExceptionStackDepth(depth);
            sendResponse(exchange, 200, "{\"success\":true,\"depth\":" + depth + "}");
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "{\"error\":\"Invalid depth\"}");
        }
    }

    private static void handleTreeTrigger(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormData(exchange);
        String trigger = params.get("trigger");
        if (trigger != null) {
            parseTriggerAndApply(trigger, false);
        }
        String response = getTriggerResponse(AgentConfig.treeTriggerOnTimeout, AgentConfig.treeTriggerOnException);
        sendResponse(exchange, 200, "{\"success\":true,\"trigger\":\"" + response + "\"}");
    }

    // ==================== General Handlers ====================

    private static void handleGetConfig(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        sendResponse(exchange, 200, StatsReporter.getConfigAsJson());
    }

    private static void handleHelp(HttpExchange exchange) throws IOException {
        String help = "{\n" +
                "  \"flat\": {\n" +
                "    \"/flat/class/add|remove\": \"POST className=xxx\",\n" +
                "    \"/flat/package/add|remove\": \"POST packageName=xxx\",\n" +
                "    \"/flat/threshold\": \"POST threshold=xxx\",\n" +
                "    \"/flat/trigger\": \"POST timeout=true/false, exception=true/false\"\n" +
                "  },\n" +
                "  \"tree\": {\n" +
                "    \"/tree/entry/add|remove\": \"POST method=xxx\",\n" +
                "    \"/tree/package/add|remove\": \"POST packageName=xxx\",\n" +
                "    \"/tree/threshold\": \"POST threshold=xxx\",\n" +
                "    \"/tree/trigger\": \"POST timeout=true/false, exception=true/false\"\n" +
                "  },\n" +
                "  \"snapshot\": {\"/snapshot/config\": \"POST enabled,threshold,mode,dir\"},\n" +
                "  \"GET /config\": \"Get configuration\",\n" +
                "  \"GET /admin\": \"Management page\"\n" +
                "}";
        sendResponse(exchange, 200, help);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        try {
            os.write(bytes);
        } finally {
            os.close();
        }
    }

    private static Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        Map<String, String> params = new HashMap<String, String>();

        InputStream is = exchange.getRequestBody();
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
        }
        String body = sb.toString();

        if (!body.isEmpty()) {
            for (String param : body.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    String key = URLDecoder.decode(kv[0], "UTF-8");
                    String value = URLDecoder.decode(kv[1], "UTF-8");
                    params.put(key, value);
                }
            }
        }

        return params;
    }

    /**
     * Handle admin page: GET /admin
     * Serves an HTML management interface from resources
     */
    private static void handleAdminPage(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String html = loadResourceAsString("admin.html");
        if (html == null) {
            sendResponse(exchange, 500, "{\"error\":\"Admin page not found\"}");
            return;
        }
        sendHtmlResponse(exchange, 200, html);
    }

    private static void sendHtmlResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        try {
            os.write(bytes);
        } finally {
            os.close();
        }
    }

    private static String loadResourceAsString(String resourceName) {
        try (InputStream is = HttpConfigServer.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                System.err.println("[MethodProbe] Resource not found: " + resourceName);
                return null;
            }
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
            }
            return sb.toString();
        } catch (IOException e) {
            System.err.println("[MethodProbe] Failed to load resource: " + resourceName);
            return null;
        }
    }

    /**
     * Handle snapshot configuration: /snapshot/config
     * POST params: enabled, threshold, mode, dir
     */
    private static void handleSnapshotConfig(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            Map<String, String> params = parseFormData(exchange);
            StringBuilder changes = new StringBuilder();

            String enabled = params.get("enabled");
            if (enabled != null) {
                AgentConfig.snapshotEnabled = Boolean.parseBoolean(enabled);
                changes.append("enabled=").append(AgentConfig.snapshotEnabled).append("; ");

                // Initialize writer if enabling and not already initialized
                if (AgentConfig.snapshotEnabled) {
                    com.methodprobe.agent.snapshot.SnapshotWriter.init(AgentConfig.snapshotDir);
                }
            }

            String mode = params.get("mode");
            if (mode != null) {
                AgentConfig.snapshotSerializeSync = "sync".equalsIgnoreCase(mode);
                changes.append("serializeMode=").append(AgentConfig.snapshotSerializeSync ? "sync" : "async")
                        .append("; ");
            }

            System.out.println("[MethodProbe] Snapshot config updated: " + changes);
            sendResponse(exchange, 200, "{\"success\":true,\"changes\":\"" + changes + "\"}");

        } catch (Exception e) {
            sendResponse(exchange, 400, "Error: " + e.getMessage());
        }
    }

    // ==================== Trigger Helper Methods ====================

    /**
     * Parse trigger string and apply to config
     * 
     * @param trigger the trigger value: timeout, exception, both, or
     *                timeout,exception
     * @param isFlat  true for flat mode, false for tree mode
     */
    private static void parseTriggerAndApply(String trigger, boolean isFlat) {
        boolean onTimeout = trigger.contains("timeout") || "both".equalsIgnoreCase(trigger);
        boolean onException = trigger.contains("exception") || "both".equalsIgnoreCase(trigger);
        if (isFlat) {
            AgentConfig.flatTriggerOnTimeout = onTimeout;
            AgentConfig.flatTriggerOnException = onException;
        } else {
            AgentConfig.treeTriggerOnTimeout = onTimeout;
            AgentConfig.treeTriggerOnException = onException;
        }
    }

    /**
     * Get trigger string from boolean flags
     */
    private static String getTriggerResponse(boolean onTimeout, boolean onException) {
        if (onTimeout && onException)
            return "both";
        if (onTimeout)
            return "timeout";
        if (onException)
            return "exception";
        return "timeout";
    }
}
