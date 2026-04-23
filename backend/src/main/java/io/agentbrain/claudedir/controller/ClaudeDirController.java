package io.agentbrain.claudedir.controller;

import io.agentbrain.claudedir.service.ClaudeDirService;
import io.agentbrain.claudedir.service.ClaudeDirService.FileNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/claude")
@RequiredArgsConstructor
public class ClaudeDirController {

    private final ClaudeDirService claudeDirService;

    @GetMapping("/tree")
    public List<FileNode> tree() {
        return claudeDirService.listTree();
    }

    @GetMapping("/file")
    public ResponseEntity<String> readFile(@RequestParam String path) {
        String content = claudeDirService.readFile(path);
        return ResponseEntity.ok(content);
    }

    @PutMapping("/file")
    public ResponseEntity<Void> writeFile(@RequestParam String path,
                                           @RequestBody Map<String, String> body) {
        claudeDirService.writeFile(path, body.get("content"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/file")
    public ResponseEntity<Void> createFile(@RequestParam String path,
                                            @RequestBody(required = false) Map<String, String> body) {
        String content = body != null ? body.getOrDefault("content", "") : "";
        claudeDirService.createFile(path, content);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/file")
    public ResponseEntity<Void> deleteFile(@RequestParam String path) {
        claudeDirService.deleteFile(path);
        return ResponseEntity.noContent().build();
    }
}
