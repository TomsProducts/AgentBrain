package io.agentbrain.backup.service;

import io.agentbrain.memory.domain.EpisodicMemory;
import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.domain.WorkingMemory;
import io.agentbrain.memory.repository.EpisodicMemoryRepository;
import io.agentbrain.memory.repository.LessonRepository;
import io.agentbrain.memory.repository.WorkingMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final DateTimeFormatter TS_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_AUTO_BACKUPS = 30;

    private final WorkingMemoryRepository workingRepo;
    private final EpisodicMemoryRepository episodicRepo;
    private final LessonRepository lessonRepo;

    @Value("${agentbrain.backup.dir:/app/data/backups}")
    private String backupBaseDir;

    // ── Auto-backup every hour ─────────────────────────────────────────────

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional(readOnly = true)
    public void autoBackup() {
        try {
            Path dir = Path.of(backupBaseDir, LocalDateTime.now().format(DIR_FMT));
            Files.createDirectories(dir);
            writeFiles(dir);
            pruneOldBackups();
            log.info("Auto-backup completed → {}", dir);
        } catch (Exception e) {
            log.error("Auto-backup failed", e);
        }
    }

    // ── Export as ZIP (in-memory) ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            writeZipEntry(zip, "working-memory.md",    buildWorkingMemoryMd());
            writeZipEntry(zip, "episodic-memory.md",   buildEpisodicMemoryMd());
            writeZipEntry(zip, "staged-lessons.md",    buildLessonsMd(Lesson.LessonStatus.STAGED));
            writeZipEntry(zip, "accepted-lessons.md",  buildLessonsMd(Lesson.LessonStatus.ACCEPTED));
        }
        return baos.toByteArray();
    }

    // ── Import from ZIP bytes ──────────────────────────────────────────────

    @Transactional
    public ImportResult importZip(byte[] zipBytes) throws IOException {
        int w = 0, e = 0, l = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replaceAll(".*/", ""); // strip path
                String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                switch (name) {
                    case "working-memory.md"   -> w += importWorkingMemory(content);
                    case "episodic-memory.md"  -> e += importEpisodicMemory(content);
                    case "staged-lessons.md"   -> l += importLessons(content, Lesson.LessonStatus.STAGED);
                    case "accepted-lessons.md" -> l += importLessons(content, Lesson.LessonStatus.ACCEPTED);
                }
                zis.closeEntry();
            }
        }
        ImportResult result = new ImportResult(w, e, l);
        log.info("Import done: {}", result);
        return result;
    }

    // ── List auto-backups ──────────────────────────────────────────────────

    public List<String> listBackups() {
        Path base = Path.of(backupBaseDir);
        if (!Files.exists(base)) return List.of();
        try {
            return Files.list(base)
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    // ── Restore from a named auto-backup ──────────────────────────────────

    @Transactional
    public ImportResult restoreFromBackup(String backupName) throws IOException {
        Path dir = Path.of(backupBaseDir, backupName);
        if (!Files.exists(dir)) throw new FileNotFoundException("Backup not found: " + backupName);

        int w = 0, e = 0, l = 0;
        for (Path file : Files.list(dir).collect(Collectors.toList())) {
            String name = file.getFileName().toString();
            String content = Files.readString(file);
            switch (name) {
                case "working-memory.md"   -> w += importWorkingMemory(content);
                case "episodic-memory.md"  -> e += importEpisodicMemory(content);
                case "staged-lessons.md"   -> l += importLessons(content, Lesson.LessonStatus.STAGED);
                case "accepted-lessons.md" -> l += importLessons(content, Lesson.LessonStatus.ACCEPTED);
            }
        }
        return new ImportResult(w, e, l);
    }

    // ── Markdown builders ─────────────────────────────────────────────────

    private String buildWorkingMemoryMd() {
        var sb = new StringBuilder("# Working Memory Backup\n");
        sb.append("<!-- exported: ").append(LocalDateTime.now().format(TS_FMT)).append(" -->\n\n");
        for (WorkingMemory m : workingRepo.findAll()) {
            sb.append("## Entry [").append(m.getId()).append("]\n");
            sb.append("**tags:** ").append(nvl(m.getTags())).append("\n");
            sb.append("**created:** ").append(fmt(m.getCreatedAt())).append("\n");
            sb.append("**expires:** ").append(fmt(m.getExpiresAt())).append("\n\n");
            sb.append(m.getContent()).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    private String buildEpisodicMemoryMd() {
        var sb = new StringBuilder("# Episodic Memory Backup\n");
        sb.append("<!-- exported: ").append(LocalDateTime.now().format(TS_FMT)).append(" -->\n\n");
        for (EpisodicMemory m : episodicRepo.findAll()) {
            sb.append("## Entry [").append(m.getId()).append("]\n");
            sb.append("**tags:** ").append(nvl(m.getTags())).append("\n");
            sb.append("**occurred:** ").append(fmt(m.getOccurredAt())).append("\n");
            sb.append("**salience:** ").append(m.getSalienceScore() != null ? m.getSalienceScore() : "").append("\n\n");
            sb.append(m.getContent()).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    private String buildLessonsMd(Lesson.LessonStatus status) {
        var sb = new StringBuilder("# ").append(cap(status.name())).append(" Lessons Backup\n");
        sb.append("<!-- exported: ").append(LocalDateTime.now().format(TS_FMT)).append(" -->\n\n");
        for (Lesson l : lessonRepo.findByStatus(status)) {
            sb.append("## Lesson [").append(l.getId()).append("]\n");
            sb.append("**status:** ").append(l.getStatus()).append("\n");
            sb.append("**patternId:** ").append(nvl(l.getPatternId())).append("\n");
            sb.append("**salience:** ").append(l.getSalience() != null ? l.getSalience() : "").append("\n");
            sb.append("**created:** ").append(fmt(l.getCreatedAt())).append("\n");
            sb.append("**graduated:** ").append(fmt(l.getGraduatedAt())).append("\n\n");
            sb.append("### Claim\n").append(nvl(l.getClaim())).append("\n\n");
            if (l.getConditions() != null && !l.getConditions().isBlank()) {
                sb.append("### Conditions\n").append(l.getConditions()).append("\n\n");
            }
            if (l.getRationale() != null && !l.getRationale().isBlank()) {
                sb.append("### Rationale\n").append(l.getRationale()).append("\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    // ── Markdown parsers ──────────────────────────────────────────────────

    private int importWorkingMemory(String md) {
        int count = 0;
        for (String block : splitBlocks(md)) {
            String content = extractContent(block);
            if (content.isBlank()) continue;
            String tags = extractMeta(block, "tags");
            if (workingRepo.findAll().stream().anyMatch(m -> m.getContent().equals(content))) continue;
            workingRepo.save(WorkingMemory.builder()
                    .content(content).tags(tags)
                    .createdAt(parseTs(extractMeta(block, "created")))
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build());
            count++;
        }
        return count;
    }

    private int importEpisodicMemory(String md) {
        int count = 0;
        for (String block : splitBlocks(md)) {
            String content = extractContent(block);
            if (content.isBlank()) continue;
            if (episodicRepo.findAll().stream().anyMatch(m -> m.getContent().equals(content))) continue;
            String salStr = extractMeta(block, "salience");
            episodicRepo.save(EpisodicMemory.builder()
                    .content(content)
                    .tags(extractMeta(block, "tags"))
                    .occurredAt(parseTs(extractMeta(block, "occurred")))
                    .salienceScore(salStr.isBlank() ? null : Double.parseDouble(salStr))
                    .staged(false)
                    .build());
            count++;
        }
        return count;
    }

    private int importLessons(String md, Lesson.LessonStatus status) {
        int count = 0;
        for (String block : splitBlocks(md)) {
            String claim = extractSection(block, "Claim");
            if (claim.isBlank()) continue;
            if (lessonRepo.existsByClaimIgnoreCase(claim)) continue;
            String salStr = extractMeta(block, "salience");
            lessonRepo.save(Lesson.builder()
                    .claim(claim)
                    .conditions(extractSection(block, "Conditions"))
                    .rationale(extractSection(block, "Rationale"))
                    .status(status)
                    .patternId(extractMeta(block, "patternId"))
                    .salience(salStr.isBlank() ? null : Double.parseDouble(salStr))
                    .createdAt(parseTs(extractMeta(block, "created")))
                    .graduatedAt(parseTs(extractMeta(block, "graduated")))
                    .build());
            count++;
        }
        return count;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void writeFiles(Path dir) throws IOException {
        Files.writeString(dir.resolve("working-memory.md"),   buildWorkingMemoryMd());
        Files.writeString(dir.resolve("episodic-memory.md"),  buildEpisodicMemoryMd());
        Files.writeString(dir.resolve("staged-lessons.md"),   buildLessonsMd(Lesson.LessonStatus.STAGED));
        Files.writeString(dir.resolve("accepted-lessons.md"), buildLessonsMd(Lesson.LessonStatus.ACCEPTED));
    }

    private void pruneOldBackups() throws IOException {
        Path base = Path.of(backupBaseDir);
        List<Path> dirs = Files.list(base)
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(Path::getFileName))
                .collect(Collectors.toList());
        while (dirs.size() > MAX_AUTO_BACKUPS) {
            Path oldest = dirs.remove(0);
            try { deleteDir(oldest); } catch (Exception ignored) {}
        }
    }

    private void deleteDir(Path dir) throws IOException {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.delete(p); } catch (IOException ignored) {}
        });
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private List<String> splitBlocks(String md) {
        return Arrays.stream(md.split("\n---\n"))
                .map(String::trim)
                .filter(s -> s.startsWith("## "))
                .collect(Collectors.toList());
    }

    private String extractMeta(String block, String key) {
        for (String line : block.lines().toList()) {
            if (line.startsWith("**" + key + ":**")) {
                return line.substring(("**" + key + ":**").length()).trim();
            }
        }
        return "";
    }

    private String extractContent(String block) {
        // Content is lines after the metadata lines (after the blank line following meta)
        List<String> lines = block.lines().collect(Collectors.toList());
        boolean metaDone = false;
        var content = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("## ") || line.startsWith("**")) continue;
            if (line.isBlank() && !metaDone) { metaDone = true; continue; }
            if (metaDone && !line.startsWith("###")) content.append(line).append("\n");
        }
        return content.toString().strip();
    }

    private String extractSection(String block, String sectionName) {
        String[] parts = block.split("### " + sectionName + "\n");
        if (parts.length < 2) return "";
        return parts[1].split("\n###")[0].split("\n---")[0].strip();
    }

    private LocalDateTime parseTs(String s) {
        if (s == null || s.isBlank()) return LocalDateTime.now();
        try { return LocalDateTime.parse(s, TS_FMT); } catch (Exception e) { return LocalDateTime.now(); }
    }

    private String fmt(LocalDateTime dt) { return dt != null ? dt.format(TS_FMT) : ""; }
    private String nvl(String s) { return s != null ? s : ""; }
    private String cap(String s) { return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(); }

    public record ImportResult(int working, int episodic, int lessons) {
        public ImportResult() { this(0, 0, 0); }
        @Override public String toString() {
            return "working=" + working + ", episodic=" + episodic + ", lessons=" + lessons;
        }
    }
}
