package com.methodprobe.agent.log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LogWebSocketServer extends WebSocketServer {

    private final Set<WebSocket> clients = Collections.synchronizedSet(new HashSet<>());

    public LogWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        System.out.println("[MethodProbe] Log Monitor connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        System.out.println("[MethodProbe] Log Monitor disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // No incoming messages expected
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // Ignore specific error to avoid spam
        if (ex.getMessage() != null && ex.getMessage().contains("Address already in use")) {
            System.err.println("[MethodProbe] Log WS Error: Address already in use");
        }
    }

    @Override
    public void onStart() {
        System.out.println("[MethodProbe] Log WebSocket Server started on port " + getPort());
    }

    public void broadcastLog(String log) {
        if (clients.isEmpty())
            return;

        synchronized (clients) {
            for (WebSocket client : clients) {
                if (client.isOpen()) {
                    client.send(log);
                }
            }
        }
    }

    public boolean hasClients() {
        return !clients.isEmpty();
    }
}
