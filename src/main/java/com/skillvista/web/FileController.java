package com.skillvista.web;

import com.skillvista.model.Account;
import com.skillvista.model.LeetSubmission;
import com.skillvista.repo.AccountRepository;
import com.skillvista.repo.LeetSubmissionRepository;
import com.skillvista.service.AuthService;
import com.skillvista.service.FileStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/** Serves uploaded resumes and LeetCode files, only to the student who owns them and that student's mentor. */
@RestController
@RequestMapping("/api/files")
public class FileController {
    private final AuthService auth;
    private final AccountRepository accounts;
    private final LeetSubmissionRepository leets;
    private final FileStore files;

    public FileController(AuthService auth, AccountRepository accounts, LeetSubmissionRepository leets, FileStore files) {
        this.auth = auth;
        this.accounts = accounts;
        this.leets = leets;
        this.files = files;
    }

    private void checkAccess(Account viewer, String studentGmail) {
        if (viewer.gmail.equalsIgnoreCase(studentGmail)) return;
        Account s = accounts.findByGmailIgnoreCase(studentGmail).orElseThrow(() -> new ApiException(404, "File not found."));
        boolean myMentee = "mentor".equals(viewer.role) && s.mentorGmail != null && s.mentorGmail.equalsIgnoreCase(viewer.gmail);
        if (!myMentee) throw new ApiException(403, "You do not have access to this file.");
    }

    private ResponseEntity<Resource> serve(String stored, String shownName) {
        Resource res = files.load(stored);
        String ext = FileStore.extensionOf(stored);
        MediaType type;
        boolean inline = true;
        switch (ext) {
            case "png" -> type = MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> type = MediaType.IMAGE_JPEG;
            case "gif" -> type = MediaType.IMAGE_GIF;
            case "webp" -> type = MediaType.parseMediaType("image/webp");
            case "pdf" -> type = MediaType.APPLICATION_PDF;
            case "doc" -> { type = MediaType.parseMediaType("application/msword"); inline = false; }
            case "docx" -> { type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"); inline = false; }
            default -> type = new MediaType("text", "plain", StandardCharsets.UTF_8);   // code files are shown as plain text
        }
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(shownName == null ? "file" : shownName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(res);
    }

    @GetMapping("/resume/{gmail}")
    public ResponseEntity<Resource> resume(HttpServletRequest req, @PathVariable String gmail) {
        Account viewer = auth.require(req, null);
        checkAccess(viewer, gmail);
        Account s = accounts.findByGmailIgnoreCase(gmail).orElseThrow(() -> new ApiException(404, "Student not found."));
        if (s.resumeFile == null) throw new ApiException(404, "This student has not uploaded a resume.");
        return serve(s.resumeFile, s.resumeName);
    }

    @GetMapping("/leet/{id}/{which}")
    public ResponseEntity<Resource> leet(HttpServletRequest req, @PathVariable Long id, @PathVariable String which) {
        Account viewer = auth.require(req, null);
        LeetSubmission l = leets.findById(id).orElseThrow(() -> new ApiException(404, "File not found."));
        checkAccess(viewer, l.studentGmail);
        boolean question = "question".equals(which);
        if (!question && !"code".equals(which)) throw new ApiException(404, "File not found.");
        return serve(question ? l.questionStored : l.codeStored, question ? l.questionFile : l.codeFile);
    }
}
