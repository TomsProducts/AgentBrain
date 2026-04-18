package io.agentbrain.sync.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentbrain.sync.config.SyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncClient {

    private final SyncConfig config;
    private final SyncState syncState;
    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final AtomicLong lastPollTs = new AtomicLong(System.currentTimeMillis());

    public void push(String relativePath, String content) {
        if (!syncState.hasChanged(relativePath, content)) return;
        try {
            String hash = syncState.computeHash(content);
            String body = objectMapper.writeValueAsString(Map.of(
                    "path", relativePath,
                    "content", content,
                    "hash", hash,
                    "ts", System.currentTimeMillis()
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/sync/push"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getSyncToken())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
            syncState.update(relativePath, content);
            log.debug("Pushed: {}", relativePath);
        } catch (Exception e) {
            log.warn("Push failed for {}: {}", relativePath, e.getMessage());
        }
    }

    public void delete(String relativePath) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/sync/file?path=" + encode(relativePath)))
                    .header("Authorization", "Bearer " + config.getSyncToken())
                    .DELETE()
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
            syncState.remove(relativePath);
            log.debug("Deleted: {}", relativePath);
        } catch (Exception e) {
            log.warn("Delete failed for {}: {}", relativePath, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void pullPending() {
        try {
            long since = lastPollTs.get();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/sync/pending?since=" + since))
                    .header("Authorization", "Bearer " + config.getSyncToken())
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;

            List<Map<String, Object>> items = objectMapper.readValue(resp.body(), List.class);
            lastPollTs.set(System.currentTimeMillis());

            for (Map<String, Object> item : items) {
                String path = (String) item.get("path");
                boolean deleted = Boolean.TRUE.equals(item.get("deleted"));
                Path local = Path.of(config.getClaudeHome(), path);
                if (deleted) {
                    Files.deleteIfExists(local);
                    log.debug("Pulled delete: {}", path);
                } else {
                    String content = (String) item.get("content");
                    if (content != null) {
                        Files.createDirectories(local.getParent());
                        Files.writeString(local, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        syncState.update(path, content);
                        log.debug("Pulled update: {}", path);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("pullPending failed: {}", e.getMessage());
        }
    }

    public void fullSync() {
        log.info("Running full sync...");
        syncState.clear();  // reset so hasChanged() returns true for all files
        syncState.loadFromDisk();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/sync/snapshot"))
                    .header("Authorization", "Bearer " + config.getSyncToken())
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Snapshot returned {}", resp.statusCode());
                return;
            }

            List<Map<String, Object>> serverFiles = objectMapper.readValue(resp.body(), List.class);
            Map<String, String> localHashes = syncState.getAll();

            // Push local files not on server or newer locally
            Path root = Path.of(config.getClaudeHome());
            for (Map.Entry<String, String> entry : localHashes.entrySet()) {
                String relPath = entry.getKey();
                boolean onServer = serverFiles.stream().anyMatch(f -> relPath.equals(f.get("path")));
                if (!onServer) {
                    try {
                        String content = Files.readString(root.resolve(relPath));
                        forcePush(relPath, content);
                    } catch (Exception e) {
                        log.warn("Could not push {}: {}", relPath, e.getMessage());
                    }
                }
            }

            // Pull server-only files
            for (Map<String, Object> serverFile : serverFiles) {
                String path = (String) serverFile.get("path");
                if (!localHashes.containsKey(path)) {
                    pullFile(path);
                }
            }

            int synced = serverFiles.size();
            log.info("Full sync complete. {} server files checked.", synced);
        } catch (Exception e) {
            log.error("Full sync failed: {}", e.getMessage());
        }
    }

    private void forcePush(String relativePath, String content) {
        try {
            String hash = syncState.computeHash(content);
            String body = objectMapper.writeValueAsString(Map.of(
                    "path", relativePath,
                    "content", content,
                    "hash", hash,
                    "ts", System.currentTimeMillis()
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/sync/push"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getSyncToken())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
            syncState.update(relativePath, content);
            log.debug("Pushed: {}", relativePath);
        } catch (Exception e) {
            log.warn("Push failed for {}: {}", relativePath, e.getMessage());
        }
    }

    private void pullFile(String relativePath) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/claude/file?path=" + encode(relativePath)))
                    .header("Authorization", "Bearer " + config.getSyncToken())
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Path local = Path.of(config.getClaudeHome(), relativePath);
                Files.createDirectories(local.getParent());
                Files.writeString(local, resp.body(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                syncState.update(relativePath, resp.body());
                log.debug("Pulled new file: {}", relativePath);
            }
        } catch (Exception e) {
            log.warn("Could not pull {}: {}", relativePath, e.getMessage());
        }
    }

    private String encode(String path) {
        return java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8);
    }
}
