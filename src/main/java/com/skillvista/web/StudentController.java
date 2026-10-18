package com.skillvista.web;

import com.skillvista.model.*;
import com.skillvista.repo.*;
import com.skillvista.service.ActivityService;
import com.skillvista.service.AuthService;
import com.skillvista.service.FileStore;
import com.skillvista.service.MetricsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final AuthService auth;
    private final MetricsService metrics;
    private final ActivityService activity;
    private final AcademicRecordRepository academics;
    private final InternshipRepository internships;
    private final ProjectRepository projects;
    private final LeetSubmissionRepository leets;
    private final FeedbackRepository feedback;
    private final ActivityLogRepository logs;
    private final TestQuestionRepository questions;
    private final TestAttemptRepository attempts;
    private final AttemptAnswerRepository answerRepo;
    private final TestConfigRepository configs;
    private final FileStore files;

    public StudentController(AuthService auth, MetricsService metrics, ActivityService activity,
                             AcademicRecordRepository academics, InternshipRepository internships,
                             ProjectRepository projects, LeetSubmissionRepository leets,
                             FeedbackRepository feedback, ActivityLogRepository logs,
                             TestQuestionRepository questions, TestAttemptRepository attempts,
                             AttemptAnswerRepository answerRepo, TestConfigRepository configs, FileStore files) {
        this.auth = auth;
        this.metrics = metrics;
        this.activity = activity;
        this.academics = academics;
        this.internships = internships;
        this.projects = projects;
        this.leets = leets;
        this.feedback = feedback;
        this.logs = logs;
        this.questions = questions;
        this.attempts = attempts;
        this.answerRepo = answerRepo;
        this.configs = configs;
        this.files = files;
    }

    static List<Map<String, Object>> feedbackView(List<Feedback> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Feedback f : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", f.createdAt.toLocalDate().format(DATE));
            m.put("text", f.feedbackText);
            out.add(m);
        }
        return out;
    }

    /** One LeetCode submission as the browser sees it (same shape for the student and the mentor). */
    static Map<String, Object> leetView(LeetSubmission l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id);
        m.put("date", l.entryDate.format(DATE));
        m.put("createdAt", l.createdAt == null ? "" : l.createdAt.toString());
        m.put("title", l.title == null ? "" : l.title);
        m.put("questionFile", l.questionFile);
        m.put("codeFile", l.codeFile);
        m.put("explanation", l.explanation);
        m.put("hasQuestion", l.questionStored != null);
        m.put("hasCode", l.codeStored != null);
        m.put("questionIsImage", l.questionStored != null && isImage(l.questionStored));
        m.put("codeIsImage", l.codeStored != null && isImage(l.codeStored));
        m.put("verdict", l.verdict == null ? "Pending" : l.verdict);
        m.put("feedback", l.mentorFeedback == null ? "" : l.mentorFeedback);
        m.put("reviewedAt", l.reviewedAt == null ? "" : l.reviewedAt.toLocalDate().format(DATE));
        return m;
    }

    static List<Map<String, Object>> leetViews(List<LeetSubmission> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LeetSubmission l : list) out.add(leetView(l));
        return out;
    }

    private static boolean isImage(String stored) {
        String ext = FileStore.extensionOf(stored);
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("webp");
    }

    private Map<String, Object> testStatus(Account s, String kind) {
        boolean available = s.mentorGmail != null
                && !questions.findByMentorGmailIgnoreCaseAndKindOrderByIdAsc(s.mentorGmail, kind).isEmpty();
        int day = TestController.releaseDay(configs, s.mentorGmail, kind);
        boolean done = attempts.existsByStudentGmailIgnoreCaseAndKindAndAttemptDate(s.gmail, kind, LocalDate.now());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", available);
        m.put("attemptedToday", done);
        m.put("releaseDay", day);
        return m;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(HttpServletRequest req) {
        Account s = auth.require(req, "student");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("account", s.toPublic());
        out.put("metrics", metrics.compute(s));
        out.put("internships", internships.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail));
        out.put("projects", projects.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail));
        out.put("academics", academics.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail));
        out.put("leetToday", leetViews(leets.findByStudentGmailIgnoreCaseAndEntryDateOrderByIdAsc(s.gmail, LocalDate.now())));
        out.put("leetAll", leetViews(leets.findByStudentGmailIgnoreCaseOrderByIdDesc(s.gmail)));

        List<Map<String, Object>> results = new ArrayList<>();
        for (TestAttempt t : attempts.findByStudentGmailIgnoreCaseOrderByIdDesc(s.gmail)) {
            results.add(TestController.attemptView(t, answerRepo.findByAttemptIdOrderByPositionAsc(t.id), false));
            if (results.size() >= 12) break;
        }
        out.put("testResults", results);
        out.put("feedback", feedbackView(feedback.findByStudentGmailIgnoreCaseOrderByIdDesc(s.gmail)));

        List<Map<String, Object>> acts = new ArrayList<>();
        for (ActivityLog a : logs.findTop8ByStudentGmailIgnoreCaseOrderByIdDesc(s.gmail)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", a.createdAt.toLocalDate().format(DATE));
            m.put("activity", a.activity);
            m.put("detail", a.detail == null ? "" : a.detail);
            acts.add(m);
        }
        out.put("activity", acts);

        Map<String, Object> tests = new LinkedHashMap<>();
        tests.put("aptitude", testStatus(s, "aptitude"));
        tests.put("technical", testStatus(s, "technical"));
        out.put("tests", tests);
        return out;
    }

    // ---------- academics ----------
    @PostMapping("/academics")
    public Map<String, Object> addSemester(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Account s = auth.require(req, "student");
        AcademicRecord r = new AcademicRecord();
        r.studentGmail = s.gmail;
        r.sem = Req.require(body, "sem", "Please enter the semester name.");
        r.gpa = Req.require(body, "gpa", "Please enter the GPA.");
        r.cgpa = Req.require(body, "cgpa", "Please enter the CGPA.");
        academics.save(r);
        activity.record(s, "Added academic record — " + r.sem, "GPA " + r.gpa + " · CGPA " + r.cgpa);
        return Map.of("ok", true);
    }

    // ---------- internships ----------
    private void applyInternship(Internship i, Map<String, Object> body) {
        i.domain = Req.require(body, "domain", "Please enter the domain.");
        i.company = Req.require(body, "company", "Please enter the company name.");
        i.duration = Req.str(body, "duration").isEmpty() ? "—" : Req.str(body, "duration");
        i.status = "Completed".equals(Req.str(body, "status")) ? "Completed" : "In Progress";
        i.knowledge = Math.max(0, Math.min(100, Req.integer(body, "knowledge", 0)));
        String cert = Req.str(body, "certName");
        if (i.status.equals("Completed") && !cert.isEmpty()) {
            i.certName = cert;
            i.certDate = LocalDate.now().format(DATE);
        }
        if (!i.status.equals("Completed")) { i.certName = null; i.certDate = null; }
    }

    @PostMapping("/internships")
    public Map<String, Object> addInternship(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Account s = auth.require(req, "student");
        Internship i = new Internship();
        i.studentGmail = s.gmail;
        applyInternship(i, body);
        internships.save(i);
        activity.record(s, "Added internship — " + i.domain, i.company);
        return Map.of("ok", true, "id", i.id);
    }

    @PutMapping("/internships/{id}")
    public Map<String, Object> updateInternship(HttpServletRequest req, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Account s = auth.require(req, "student");
        Internship i = internships.findById(id).orElseThrow(() -> new ApiException(404, "Internship not found."));
        if (!i.studentGmail.equalsIgnoreCase(s.gmail)) throw new ApiException(403, "That internship belongs to someone else.");
        boolean wasCompleted = "Completed".equals(i.status);
        String keepCert = i.certName, keepDate = i.certDate;
        applyInternship(i, body);
        if (i.status.equals("Completed") && Req.str(body, "certName").isEmpty()) { i.certName = keepCert; i.certDate = keepDate; }
        internships.save(i);
        String what = (!wasCompleted && i.status.equals("Completed")) ? "Completed internship — " : "Updated internship — ";
        activity.record(s, what + i.domain, i.company);
        return Map.of("ok", true);
    }

    // ---------- projects ----------
    private void applyProject(Project p, Map<String, Object> body) {
        p.title = Req.require(body, "title", "Please enter a project title.");
        p.summary = Req.str(body, "summary");
        String ppt = Req.str(body, "pptName");
        if (!ppt.isEmpty()) { p.pptName = ppt; p.pptDate = LocalDate.now().format(DATE); }
    }

    @PostMapping("/projects")
    public Map<String, Object> addProject(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        Account s = auth.require(req, "student");
        Project p = new Project();
        p.studentGmail = s.gmail;
        applyProject(p, body);
        projects.save(p);
        activity.record(s, "Saved project — " + p.title, null);
        return Map.of("ok", true);
    }

    @PutMapping("/projects/{id}")
    public Map<String, Object> updateProject(HttpServletRequest req, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Account s = auth.require(req, "student");
        Project p = projects.findById(id).orElseThrow(() -> new ApiException(404, "Project not found."));
        if (!p.studentGmail.equalsIgnoreCase(s.gmail)) throw new ApiException(403, "That project belongs to someone else.");
        applyProject(p, body);
        projects.save(p);
        activity.record(s, "Updated project — " + p.title, null);
        return Map.of("ok", true);
    }

    // ---------- LeetCode ----------
    /** Daily LeetCode upload: question screenshot + code file + explanation. The real files are stored for the mentor to open. */
    @PostMapping(value = "/leetcode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> addLeetcode(HttpServletRequest req,
                                           @RequestParam(value = "questionFile", required = false) MultipartFile questionFile,
                                           @RequestParam(value = "codeFile", required = false) MultipartFile codeFile,
                                           @RequestParam(value = "explanation", required = false) String explanation,
                                           @RequestParam(value = "title", required = false) String title) {
        Account s = auth.require(req, "student");
        if (questionFile == null || questionFile.isEmpty()) throw new ApiException(400, "Please attach the question screenshot.");
        if (codeFile == null || codeFile.isEmpty()) throw new ApiException(400, "Please attach your code.");
        if (explanation == null || explanation.isBlank()) throw new ApiException(400, "Please explain the question and your code.");

        LeetSubmission l = new LeetSubmission();
        l.studentGmail = s.gmail;
        l.entryDate = LocalDate.now();
        l.title = title == null || title.isBlank() ? null : title.trim();
        l.explanation = explanation.trim();
        l.questionStored = files.save(questionFile, "leetq", FileStore.QUESTION_TYPES, "Question screenshot");
        try {
            l.codeStored = files.save(codeFile, "leetc", FileStore.CODE_TYPES, "Code file");
        } catch (ApiException e) {
            files.delete(l.questionStored);
            throw e;
        }
        l.questionFile = FileStore.cleanName(questionFile);
        l.codeFile = FileStore.cleanName(codeFile);
        leets.save(l);
        activity.record(s, "Solved a LeetCode problem", l.title != null ? l.title : l.questionFile);
        return Map.of("ok", true);
    }
}
