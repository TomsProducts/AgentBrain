package io.agentbrain.sync.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentbrain.sync.config.SyncConfig;
import io.agentbrain.sync.sync.SyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalApiServer {

    private final SyncConfig config;
    private final SyncClient syncClient;
    private final ObjectMapper objectMapper;

    private HttpServer server;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public void start() throws IOException {
        int port = config.getLocalApiPort();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        // GET /ping — health check for hooks
        server.createContext("/ping", exchange -> {
            sendJson(exchange, 200, objectMapper.writeValueAsString(
                Map.of("status", "ok", "serverUrl", config.getServerUrl())));
        });

        // GET /health — alias for ping
        server.createContext("/health", exchange -> {
            sendJson(exchange, 200, objectMapper.writeValueAsString(Map.of("status", "ok")));
        });

        // GET /context?q=<task> — fetch ranked memory context from server, formatted as Markdown
        server.createContext("/context", exchange -> {
            String query = queryParam(exchange.getRequestURI().getQuery(), "q");
            String encoded = URLEncoder.encode(query != null ? query : "", StandardCharsets.UTF_8);
            String url = config.getServerUrl() + "/api/context?q=" + encoded;
            try {
                HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).GET()
                        .header("Accept", "application/json").build(),
                    HttpResponse.BodyHandlers.ofString());
                String markdown = formatContextAsMarkdown(resp.body(), query);
                sendText(exchange, 200, markdown);
            } catch (Exception e) {
                log.warn("Context fetch failed: {}", e.getMessage());
                sendText(exchange, 503, "⚠️  AgentBrain server unreachable: " + config.getServerUrl());
            }
        });

        // POST /working — create working memory (proxied to server)
        server.createContext("/working", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }
            proxyPost(exchange, "/api/memory/working");
        });

        // POST /episodic — create episodic memory (proxied to server)
        server.createContext("/episodic", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }
            proxyPost(exchange, "/api/memory/episodic");
        });

        // POST /sync-trigger — trigger immediate full sync
        server.createContext("/sync-trigger", exchange -> {
            Thread.ofVirtual().start(syncClient::fullSync);
            sendJson(exchange, 200, objectMapper.writeValueAsString(Map.of("ok", true, "message", "sync triggered")));
        });

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        log.info("Local API server listening on localhost:{}", port);
    }

    public void stop() {
        if (server != null) { server.stop(0); }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void proxyPost(HttpExchange exchange, String apiPath) throws IOException {
        try {
            byte[] body = exchange.getRequestBody().readAllBytes();
            HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + apiPath))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .header("Content-Type", "application/json")
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            sendJson(exchange, resp.statusCode(), resp.body());
        } catch (Exception e) {
            log.warn("Proxy POST to {} failed: {}", apiPath, e.getMessage());
            sendJson(exchange, 503, "{\"error\":\"AgentBrain server unreachable\"}");
        }
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private String queryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0]))
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String formatContextAsMarkdown(String json, String query) {
        try {
            var root = objectMapper.readValue(json, Map.class);
            var sb = new StringBuilder();
            sb.append("# 🧠 AgentBrain Context");
            if (query != null && !query.isBlank()) sb.append(" — ").append(query);
            sb.append("\n\n");

            var episodes = (java.util.List<?>) root.get("episodes");
            if (episodes != null && !episodes.isEmpty()) {
                sb.append("## 📼 Relevant Episodes\n\n");
                for (Object o : episodes) {
                    var ep = (Map<?, ?>) o;
                    sb.append("- **").append(ep.get("content")).append("**");
                    if (ep.get("tags") != null) sb.append("  `").append(ep.get("tags")).append("`");
                    sb.append("\n");
                }
                sb.append("\n");
            }

            var lessons = (java.util.List<?>) root.get("lessons");
            if (lessons != null && !lessons.isEmpty()) {
                sb.append("## 🎓 Accepted Lessons\n\n");
                for (Object o : lessons) {
                    var l = (Map<?, ?>) o;
                    sb.append("- ").append(l.get("claim")).append("\n");
                    if (l.get("rationale") != null)
                        sb.append("  > ").append(l.get("rationale")).append("\n");
                }
            }

            if ((episodes == null || episodes.isEmpty()) && (lessons == null || lessons.isEmpty())) {
                sb.append("_No relevant context found. Add memories via `brain-note`._\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return json; // fallback: return raw JSON
        }
    }
}
