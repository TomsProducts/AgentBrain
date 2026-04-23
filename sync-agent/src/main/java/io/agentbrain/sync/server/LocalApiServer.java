package io.agentbrain.sync.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.agentbrain.sync.config.SyncConfig;
import io.agentbrain.sync.sync.SyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalApiServer {

    private final SyncConfig config;
    private final SyncClient syncClient;
    private final ObjectMapper objectMapper;

    private HttpServer server;

    public void start() throws IOException {
        int port = config.getLocalApiPort();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        server.createContext("/sync/trigger", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            AtomicInteger synced = new AtomicInteger(0);
            Thread.ofVirtual().start(() -> {
                syncClient.fullSync();
                synced.set(1);
            });
            // Return quickly, sync runs async
            String body = objectMapper.writeValueAsString(Map.of("ok", true, "synced", "triggered"));
            sendJson(exchange, 200, body);
        });

        server.createContext("/health", exchange -> {
            String body = objectMapper.writeValueAsString(Map.of("ok", true));
            sendJson(exchange, 200, body);
        });

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        log.info("Local API server listening on localhost:{}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("Local API server stopped");
        }
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
