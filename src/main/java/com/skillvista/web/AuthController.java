package com.skillvista.web;

import com.skillvista.model.Account;
import com.skillvista.repo.AccountRepository;
import com.skillvista.service.AuthService;
import com.skillvista.service.EventHub;
import com.skillvista.service.FileStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountRepository accounts;
    private final AuthService auth;
    private final FileStore files;
    private final EventHub hub;

    public AuthController(AccountRepository accounts, AuthService auth, FileStore files, EventHub hub) {
        this.accounts = accounts;
        this.auth = auth;
        this.files = files;
        this.hub = hub;
    }

    /** Sign-up is a multipart form so a student can attach a resume. All text fields arrive as normal form fields. */
    @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> signup(@RequestParam Map<String, String> params,
                                      @RequestParam(value = "resume", required = false) MultipartFile resume) {
        Map<String, Object> body = new HashMap<>(params);
        String role = "mentor".equals(Req.str(body, "role")) ? "mentor" : "student";
        String firstName = Req.str(body, "firstName");
        String lastName = Req.str(body, "lastName");
        String age = Req.str(body, "age");
        String deptSec = Req.str(body, "deptSec");
        String gmail = Req.str(body, "gmail").toLowerCase();
        String password = Req.str(body, "password");
        String confirm = Req.str(body, "confirmPassword");

        if (firstName.isEmpty() || lastName.isEmpty() || age.isEmpty() || deptSec.isEmpty() || gmail.isEmpty() || password.isEmpty())
            throw new ApiException(400, "Please fill in all required fields.");
        if (!gmail.contains("@")) throw new ApiException(400, "Please enter a valid email address.");
        if (!password.equals(confirm)) throw new ApiException(400, "Password and Confirm Password do not match.");
        if (password.length() < 6) throw new ApiException(400, "Password should be at least 6 characters.");
        if (accounts.existsByGmailIgnoreCase(gmail)) throw new ApiException(409, "An account with this email already exists.");

        Account a = new Account();
        a.role = role;
        a.firstName = firstName;
        a.lastName = lastName;
        a.age = Req.integer(body, "age", 0);
        a.deptSec = deptSec;
        a.gmail = gmail;
        a.passwordHash = auth.hash(password);

        if (role.equals("student")) {
            a.regNo = Req.require(body, "regNo", "Please enter your register number.");
            a.studyYear = Req.require(body, "year", "Please select your year.");
            a.cgpa = Req.require(body, "cgpa", "Please enter your CGPA.");

            // The student is linked to a mentor by the mentor's email, and that mentor must already have an account.
            String mentorGmail = Req.require(body, "mentorGmail", "Please enter your mentor's email.").toLowerCase();
            Account mentor = accounts.findByGmailIgnoreCase(mentorGmail).filter(m -> "mentor".equals(m.role)).orElseThrow(() ->
                    new ApiException(400, "No mentor account was found for " + mentorGmail + ". Ask your mentor to create a Mentor account first, then use the same email here."));
            a.mentorGmail = mentor.gmail;
            a.mentorName = mentor.fullName();

            a.linkedinUrl = Req.url(body, "linkedinUrl", "linkedin.com", "Please enter a valid LinkedIn profile link (linkedin.com/in/...).");
            a.leetcodeUrl = Req.url(body, "leetcodeUrl", "leetcode.com", "Please enter a valid LeetCode profile link (leetcode.com/u/...).");
            String solved = Req.require(body, "leetcodeSolved", "Please enter how many LeetCode problems you have solved so far.");
            int n;
            try { n = Integer.parseInt(solved); } catch (NumberFormatException e) { n = -1; }
            if (n < 0 || n > 5000) throw new ApiException(400, "LeetCode solved count must be a number between 0 and 5000.");
            a.leetcodeSolved = n;

            if (resume == null || resume.isEmpty()) throw new ApiException(400, "Please upload your resume (PDF, DOC or DOCX).");
            a.resumeFile = files.save(resume, "resume", FileStore.RESUME_TYPES, "Resume");
            a.resumeName = FileStore.cleanName(resume);
        }
        accounts.save(a);
        if (a.mentorGmail != null) hub.publish(a.mentorGmail);   // the mentor's page shows the new student straight away
        return Map.of("ok", true);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String role = "mentor".equals(Req.str(body, "role")) ? "mentor" : "student";
        String gmail = Req.str(body, "gmail");
        String password = Req.str(body, "password");
        if (gmail.isEmpty() || password.isEmpty())
            throw new ApiException(400, "Please enter your email and password.");

        Account a = accounts.findByGmailIgnoreCase(gmail).orElse(null);
        boolean ok = a != null && a.role.equals(role) && auth.verify(password, a.passwordHash);
        if (!ok) throw new ApiException(401, "No " + role + " account matches that email and password. Check them, pick the right Student/Mentor tab, or create an account.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", auth.issueToken(a));
        out.put("role", role);
        out.put("account", a.toPublic());
        return out;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        auth.revoke(auth.tokenOf(req));
        return Map.of("ok", true);
    }
}
