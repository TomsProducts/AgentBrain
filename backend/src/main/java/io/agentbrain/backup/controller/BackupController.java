package io.agentbrain.backup.controller;

import io.agentbrain.backup.service.BackupService;
import io.agentbrain.backup.service.BackupService.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() throws IOException {
        byte[] zip = backupService.exportZip();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"agentbrain-backup.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importBackup(@RequestParam("file") MultipartFile file) throws IOException {
        return backupService.importZip(file.getBytes());
    }

    @GetMapping("/list")
    public List<String> list() {
        return backupService.listBackups();
    }

    @PostMapping("/restore/{backupName}")
    public ImportResult restore(@PathVariable String backupName) throws IOException {
        return backupService.restoreFromBackup(backupName);
    }

    @PostMapping("/now")
    public Map<String, String> triggerNow() {
        backupService.autoBackup();
        return Map.of("status", "ok");
    }
}
