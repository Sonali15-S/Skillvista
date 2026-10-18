package com.skillvista.web;

import com.skillvista.model.Account;
import com.skillvista.repo.AccountRepository;
import com.skillvista.service.ActivityService;
import com.skillvista.service.AuthService;
import com.skillvista.service.EventHub;
import com.skillvista.service.FileStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/** "My Profile": every logged-in user can view and edit their own details. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final AuthService auth;
    private final AccountRepository accounts;
    private final FileStore files;
    private final EventHub hub;
    private final ActivityService activity;

    public ProfileController(AuthService auth, AccountRepository accounts, FileStore files, EventHub hub, ActivityService activity) {
        this.auth = auth;
        this.accounts = accounts;
        this.files = files;
        this.hub = hub;
        this.activity = activity;
    }

    @GetMapping
    public Map<String, Object> get(HttpServletRequest req) {
        return Map.of("account", auth.require(req, null).toPublic());
    }

    @PutMapping
    public Map<String, Object> update(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Account a = auth.require(req, null);
        String oldMentor = a.mentorGmail;

        a.firstName = Req.require(body, "firstName", "Please enter your first name.");
        a.lastName = Req.require(body, "lastName", "Please enter your last name.");
        int age = Req.integer(body, "age", 0);
        if (age < 10 || age > 100) throw new ApiException(400, "Please enter a valid age.");
        a.age = age;
        a.deptSec = Req.require(body, "deptSec", "Please enter your department and section.");

        if ("student".equals(a.role)) {
            a.regNo = Req.require(body, "regNo", "Please enter your register number.");
            a.studyYear = Req.require(body, "year", "Please select your year.");
            a.cgpa = Req.require(body, "cgpa", "Please enter your CGPA.");
            a.linkedinUrl = Req.url(body, "linkedinUrl", "linkedin.com", "Please enter a valid LinkedIn profile link (linkedin.com/in/...).");
            a.leetcodeUrl = Req.url(body, "leetcodeUrl", "leetcode.com", "Please enter a valid LeetCode profile link (leetcode.com/u/...).");
            int solved = Req.integer(body, "leetcodeSolved", -1);
            if (solved < 0 || solved > 5000) throw new ApiException(400, "LeetCode solved count must be a number between 0 and 5000.");
            a.leetcodeSolved = solved;

            String mentorGmail = Req.require(body, "mentorGmail", "Please enter your mentor's email.").toLowerCase();
            if (a.mentorGmail == null || !a.mentorGmail.equalsIgnoreCase(mentorGmail)) {
                Account mentor = accounts.findByGmailIgnoreCase(mentorGmail).filter(m -> "mentor".equals(m.role)).orElseThrow(() ->
                        new ApiException(400, "No mentor account was found for " + mentorGmail + "."));
                a.mentorGmail = mentor.gmail;
                a.mentorName = mentor.fullName();
            }
        }
        accounts.save(a);
        if ("student".equals(a.role)) activity.refresh(a);     // score + live update for the student and their mentor
        hub.publish(a.gmail, oldMentor, a.mentorGmail);        // the old mentor's list updates too if the student switched
        return Map.of("ok", true, "account", a.toPublic());
    }

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> resume(HttpServletRequest req, @RequestParam(value = "resume", required = false) MultipartFile resume) {
        Account a = auth.require(req, "student");
        if (resume == null || resume.isEmpty()) throw new ApiException(400, "Please choose a resume file (PDF, DOC or DOCX).");
        String stored = files.save(resume, "resume", FileStore.RESUME_TYPES, "Resume");
        files.delete(a.resumeFile);
        a.resumeFile = stored;
        a.resumeName = FileStore.cleanName(resume);
        accounts.save(a);
        hub.publish(a.gmail, a.mentorGmail);
        return Map.of("ok", true, "account", a.toPublic());
    }

    @PutMapping("/password")
    public Map<String, Object> password(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Account a = auth.require(req, null);
        String current = Req.str(body, "currentPassword");
        String next = Req.str(body, "newPassword");
        if (!auth.verify(current, a.passwordHash)) throw new ApiException(400, "Your current password is not correct.");
        if (next.length() < 6) throw new ApiException(400, "The new password should be at least 6 characters.");
        a.passwordHash = auth.hash(next);
        accounts.save(a);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        return out;
    }
}
