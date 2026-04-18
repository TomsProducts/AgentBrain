package io.agentbrain.sync.controller;

import io.agentbrain.claudedir.service.ClaudeDirService;
import io.agentbrain.claudedir.service.ClaudeDirService.FileNode;
import io.agentbrain.sync.domain.SyncLog;
import io.agentbrain.sync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Slf4j
public class SyncController {

    private final ClaudeDirService claudeDirService;
    private final SyncLogRepository syncLogRepository;

    @Value("${agentbrain.sync.token}")
    private String syncToken;

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> push(
            @RequestBody SyncPushRequest req,
            @RequestHeader("Authorization") String auth) {
        validateToken(auth);
        claudeDirService.writeFile(req.path(), req.content());
        SyncLog log = SyncLog.builder()
                .path(req.path())
                .contentHash(req.hash())
                .ts(req.ts())
                .source("LOCAL")
                .deleted(false)
                .syncedAt(LocalDateTime.now())
                .build();
        syncLogRepository.save(log);
        return ResponseEntity.ok(Map.of("ok", true, "conflict", false));
    }

    @GetMapping("/pending")
    public List<SyncLog> pending(
            @RequestParam long since,
            @RequestHeader("Authorization") String auth) {
        validateToken(auth);
        return syncLogRepository.findBySourceAndTsGreaterThan("WEBUI", since);
    }

    @GetMapping("/snapshot")
    public List<FileNode> snapshot(@RequestHeader("Authorization") String auth) {
        validateToken(auth);
        return claudeDirService.listTree();
    }

    @DeleteMapping("/file")
    public ResponseEntity<Void> deleteFile(
            @RequestParam String path,
            @RequestHeader("Authorization") String auth) {
        validateToken(auth);
        claudeDirService.deleteFile(path);
        SyncLog entry = SyncLog.builder()
                .path(path)
                .ts(System.currentTimeMillis())
                .source("LOCAL")
                .deleted(true)
                .syncedAt(LocalDateTime.now())
                .build();
        syncLogRepository.save(entry);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestHeader("Authorization") String auth) {
        validateToken(auth);
        long total = syncLogRepository.count();
        return ResponseEntity.ok(Map.of("totalLogs", total, "ok", true));
    }

    private void validateToken(String authHeader) {
        String expected = "Bearer " + syncToken;
        if (!expected.equals(authHeader)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid sync token");
        }
    }

    public record SyncPushRequest(String path, String content, String hash, long ts) {}
}
