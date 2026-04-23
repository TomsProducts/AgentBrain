package io.agentbrain.claudedir.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ClaudeDirService {

    @Value("${agentbrain.claude-dir}")
    private String claudeDir;

    public List<FileNode> listTree() {
        Path root = resolveRoot();
        List<FileNode> nodes = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(root)) return FileVisitResult.CONTINUE;
                    String relative = root.relativize(dir).toString();
                    nodes.add(new FileNode(dir.getFileName().toString(), relative, "directory",
                            0L, attrs.lastModifiedTime().toMillis()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relative = root.relativize(file).toString();
                    nodes.add(new FileNode(file.getFileName().toString(), relative, "file",
                            attrs.size(), attrs.lastModifiedTime().toMillis()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Error walking claude directory: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot read directory tree");
        }
        return nodes;
    }

    public String readFile(String relativePath) {
        Path target = resolveSafe(relativePath);
        if (!Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + relativePath);
        }
        if (!Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path is not a file: " + relativePath);
        }
        try {
            return Files.readString(target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot read file: " + e.getMessage());
        }
    }

    public void writeFile(String relativePath, String content) {
        Path target = resolveSafe(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot write file: " + e.getMessage());
        }
    }

    public void deleteFile(String relativePath) {
        Path target = resolveSafe(relativePath);
        if (!Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + relativePath);
        }
        try {
            Files.delete(target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot delete file: " + e.getMessage());
        }
    }

    public void createFile(String relativePath, String content) {
        Path target = resolveSafe(relativePath);
        if (Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "File already exists: " + relativePath);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content != null ? content : "", StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot create file: " + e.getMessage());
        }
    }

    /**
     * Resolves a relative path safely — validates it stays inside claudeDir.
     * Prevents path traversal attacks.
     */
    Path resolveSafe(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path must not be blank");
        }
        Path root = resolveRoot();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path traversal attempt detected");
        }
        return target;
    }

    private Path resolveRoot() {
        return Path.of(claudeDir).toAbsolutePath().normalize();
    }

    public record FileNode(String name, String path, String type, long size, long modified) {}
}
