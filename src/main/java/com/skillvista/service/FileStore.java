package com.skillvista.service;

import com.skillvista.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Saves uploaded files (resumes, LeetCode screenshots and code) in the ./uploads folder under random names. */
@Service
public class FileStore {
    public static final Set<String> RESUME_TYPES = Set.of("pdf", "doc", "docx");
    public static final Set<String> QUESTION_TYPES = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf");
    public static final Set<String> CODE_TYPES = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf", "txt", "java", "py", "cpp", "c",
            "cs", "js", "ts", "go", "rs", "kt", "rb", "php", "sql", "swift", "h", "md");

    private final Path root;

    public FileStore(@Value("${skillvista.upload-dir:./uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Stores the file and returns its stored name, or null when no file was sent. */
    public String save(MultipartFile file, String prefix, Set<String> allowed, String what) {
        if (file == null || file.isEmpty()) return null;
        String ext = extensionOf(file.getOriginalFilename());
        if (!allowed.contains(ext))
            throw new ApiException(400, what + " must be one of: " + String.join(", ", allowed.stream().sorted().toList()) + ".");
        String stored = prefix + "-" + UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(500, "Could not save the uploaded file.");
        }
        return stored;
    }

    public static String cleanName(MultipartFile file) {
        String n = file.getOriginalFilename();
        if (n == null || n.isBlank()) return "file";
        n = n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    public Resource load(String stored) {
        if (stored == null) throw new ApiException(404, "File not found.");
        Path p = root.resolve(stored).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) throw new ApiException(404, "File not found.");
        return new FileSystemResource(p);
    }

    public void delete(String stored) {
        if (stored == null) return;
        try {
            Path p = root.resolve(stored).normalize();
            if (p.startsWith(root)) Files.deleteIfExists(p);
        } catch (IOException ignored) { }
    }
}
