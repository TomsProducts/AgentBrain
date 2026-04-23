package io.agentbrain.sync.watcher;

import io.agentbrain.sync.config.SyncConfig;
import io.agentbrain.sync.sync.SyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.*;

import static java.nio.file.StandardWatchEventKinds.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClaudeDirWatcher {

    private final SyncConfig config;
    private final SyncClient syncClient;

    private WatchService watchService;
    private final Map<WatchKey, Path> keyToPath = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debouncer = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean running = false;

    private static final long DEBOUNCE_MS = 300;
    private static final long MAX_FILE_SIZE = 1_048_576; // 1MB

    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(Path.of(config.getClaudeHome()));
            running = true;

            // Watch loop in background thread
            Thread watchThread = Thread.ofVirtual().start(this::watchLoop);

            // Poll server every 2 seconds for WebUI changes
            poller.scheduleAtFixedRate(() -> {
                try { syncClient.pullPending(); }
                catch (Exception e) { log.debug("Poll error: {}", e.getMessage()); }
            }, 2, 2, TimeUnit.SECONDS);

            log.info("Watching {} for changes", config.getClaudeHome());
        } catch (IOException e) {
            log.error("Could not start file watcher: {}", e.getMessage());
        }
    }

    public void stop() {
        running = false;
        poller.shutdownNow();
        debouncer.shutdownNow();
        try { if (watchService != null) watchService.close(); } catch (IOException ignored) {}
    }

    private void registerAll(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        keyToPath.put(key, dir);
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }
            if (key == null) continue;

            Path dir = keyToPath.get(key);
            if (dir == null) { key.cancel(); continue; }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                Path name = ((WatchEvent<Path>) event).context();
                Path fullPath = dir.resolve(name);
                Path root = Path.of(config.getClaudeHome());
                String relative = root.relativize(fullPath).toString();

                // Skip history.jsonl storms and hidden files
                if (relative.equals("history.jsonl") || name.toString().startsWith(".")) continue;
                // Skip git
                if (relative.startsWith(".git/")) continue;

                if (kind == ENTRY_DELETE) {
                    debounceAction(relative, () -> syncClient.delete(relative));
                } else if (Files.isRegularFile(fullPath)) {
                    long size = fileSize(fullPath);
                    if (size > MAX_FILE_SIZE) { log.debug("Skipping large file: {}", relative); continue; }
                    debounceAction(relative, () -> {
                        try {
                            String content = Files.readString(fullPath);
                            syncClient.push(relative, content);
                        } catch (IOException e) {
                            log.warn("Could not read {}: {}", relative, e.getMessage());
                        }
                    });
                } else if (Files.isDirectory(fullPath) && kind == ENTRY_CREATE) {
                    try { register(fullPath); } catch (IOException e) { log.warn("Could not watch {}", fullPath); }
                }
            }
            key.reset();
        }
    }

    private void debounceAction(String key, Runnable action) {
        ScheduledFuture<?> existing = pending.remove(key);
        if (existing != null) existing.cancel(false);
        ScheduledFuture<?> future = debouncer.schedule(() -> {
            pending.remove(key);
            action.run();
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pending.put(key, future);
    }

    private long fileSize(Path p) {
        try { return Files.size(p); } catch (IOException e) { return 0; }
    }
}
