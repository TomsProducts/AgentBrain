package io.agentbrain.sync.sync;

import io.agentbrain.sync.config.SyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncState {

    private final SyncConfig config;
    private final Map<String, String> hashes = new ConcurrentHashMap<>();

    public String computeHash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(content.hashCode());
        }
    }

    public boolean hasChanged(String relativePath, String content) {
        String newHash = computeHash(content);
        String oldHash = hashes.get(relativePath);
        return !newHash.equals(oldHash);
    }

    public void update(String relativePath, String content) {
        hashes.put(relativePath, computeHash(content));
    }

    public void remove(String relativePath) {
        hashes.remove(relativePath);
    }

    public Map<String, String> getAll() {
        return Map.copyOf(hashes);
    }

    public void clear() {
        hashes.clear();
    }

    public void loadFromDisk() {
        Path root = Path.of(config.getClaudeHome());
        try {
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(p -> size(p) < 1_048_576)
                    .forEach(p -> {
                        try {
                            String relative = root.relativize(p).toString();
                            String content = Files.readString(p);
                            hashes.put(relative, computeHash(content));
                        } catch (Exception e) {
                            log.warn("Could not hash {}: {}", p, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Could not load disk state: {}", e.getMessage());
        }
    }

    // workaround for checked exception in lambda
    private long size(Path p) {
        try { return Files.size(p); } catch (Exception e) { return 0; }
    }
}
