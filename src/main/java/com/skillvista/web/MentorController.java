package com.skillvista.web;

import com.skillvista.model.*;
import com.skillvista.repo.*;
import com.skillvista.service.ActivityService;
import com.skillvista.service.AuthService;
import com.skillvista.service.EventHub;
import com.skillvista.service.MetricsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/mentor")
public class MentorController {
    private final AuthService auth;
    private final AccountRepository accounts;
    private final MetricsService metrics;
    private final EventHub hub;
    private final InternshipRepository internships;
    private final ProjectRepository projects;
    private final AcademicRecordRepository academics;
    private final FeedbackRepository feedback;
    private final ActivityLogRepository logs;
    private final TestQuestionRepository questions;
    private final LeetSubmissionRepository leets;
    private final TestAttemptRepository attempts;
    private final AttemptAnswerRepository answerRepo;
    private final TestConfigRepository configs;
    private final ActivityService activity;

    public MentorController(AuthService auth, AccountRepository accounts, MetricsService metrics, EventHub hub,
                            InternshipRepository internships, ProjectRepository projects,
                            AcademicRecordRepository academics, FeedbackRepository feedback,
                            ActivityLogRepository logs, TestQuestionRepository questions,
                            LeetSubmissionRepository leets, TestAttemptRepository attempts,
                            AttemptAnswerRepository answerRepo, TestConfigRepository configs, ActivityService activity) {
        this.auth = auth;
        this.accounts = accounts;
        this.metrics = metrics;
        this.hub = hub;
        this.internships = internships;
        this.projects = projects;
        this.academics = academics;
        this.feedback = feedback;
        this.logs = logs;
        this.questions = questions;
        this.leets = leets;
        this.attempts = attempts;
        this.answerRepo = answerRepo;
        this.configs = configs;
        this.activity = activity;
    }

    private static boolean isPendingLeet(LeetSubmission l) { return l.verdict == null || "Pending".equals(l.verdict); }

    private List<Account> mentees(Account mentor) {
        return accounts.findByRoleAndMentorGmailIgnoreCase("student", mentor.gmail);
    }

    private Account menteeOf(Account mentor, String gmail) {
        Account s = accounts.findByGmailIgnoreCase(gmail).orElseThrow(() -> new ApiException(404, "Student not found."));
        if (!"student".equals(s.role) || s.mentorGmail == null || !s.mentorGmail.equalsIgnoreCase(mentor.gmail))
            throw new ApiException(403, "This student is not assigned to you.");
        return s;
    }

    private String primaryDomain(List<Internship> ints) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Internship i : ints) counts.merge(i.domain, 1, Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("—");
    }

    /** Dashboard numbers, student table, placement-readiness list and recent activity - all in one call. */
    @GetMapping("/overview")
    public Map<String, Object> overview(HttpServletRequest req) {
        Account mentor = auth.require(req, "mentor");
        List<Account> list = mentees(mentor);

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, String> names = new HashMap<>();
        int sum = 0, onTrack = 0, needs = 0, ready = 0, pendingLeetTotal = 0;
        for (Account s : list) {
            List<Internship> ints = internships.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail);
            int overall = metrics.overallScore(s);
            sum += overall;
            if (overall >= 50) onTrack++; else needs++;
            if (overall >= 75) ready++;
            names.put(s.gmail.toLowerCase(), s.fullName());

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", s.fullName());
            r.put("gmail", s.gmail);
            int pendingLeet = (int) leets.findByStudentGmailIgnoreCaseOrderByIdDesc(s.gmail).stream().filter(MentorController::isPendingLeet).count();
            pendingLeetTotal += pendingLeet;
            r.put("deptSec", s.deptSec);
            r.put("leetTotal", metrics.leetTotal(s));
            r.put("pendingLeet", pendingLeet);
            r.put("domain", primaryDomain(ints));
            r.put("internships", ints.size());
            r.put("cgpa", metrics.currentCgpa(s));
            r.put("skillScore", overall);
            r.put("readinessLevel", MetricsService.readinessLevel(overall));
            r.put("statusKey", MetricsService.statusKey(overall));
            rows.add(r);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalStudents", list.size());
        stats.put("overallProgress", list.isEmpty() ? 0 : Math.round(sum / (double) list.size()));
        stats.put("onTrack", onTrack);
        stats.put("needingSupport", needs);
        stats.put("placementReady", ready);
        stats.put("pendingLeet", pendingLeetTotal);
        int pendingTests = 0;
        if (!list.isEmpty()) {
            List<String> gm = list.stream().map(a -> a.gmail).toList();
            pendingTests = (int) attempts.findByStudentGmailInOrderByIdDesc(gm).stream().filter(TestAttempt::isPending).count();
        }
        stats.put("pendingTests", pendingTests);

        List<Map<String, Object>> recent = new ArrayList<>();
        if (!list.isEmpty()) {
            List<String> gmails = list.stream().map(a -> a.gmail).toList();
            for (ActivityLog a : logs.findTop10ByStudentGmailInOrderByIdDesc(gmails)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("student", names.getOrDefault(a.studentGmail.toLowerCase(), a.studentGmail));
                m.put("activity", a.activity);
                m.put("detail", a.detail == null ? "" : a.detail);
                m.put("date", a.createdAt.toLocalDate().format(StudentController.DATE));
                recent.add(m);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("account", mentor.toPublic());
        out.put("stats", stats);
        out.put("students", rows);
        out.put("recent", recent);
        return out;
    }

    /** Full history of one assigned student, for the Evaluate Student page. */
    @GetMapping("/students/{gmail}")
    public Map<String, Object> student(HttpServletRequest req, @PathVariable String gmail) {
        Account mentor = auth.require(req, "mentor");
        Account s = menteeOf(mentor, gmail);
        List<Internship> ints = internships.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("account", s.toPublic());
        out.put("primaryDomain", primaryDomain(ints));
        out.put("metrics", metrics.compute(s));
        out.put("internships", ints);
        out.put("projects", projects.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail));
        out.put("academics", academics.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail));
        out.put("feedback", StudentController.feedbackView(feedback.findByStudentGmailIgnoreCaseOrderByIdDesc(s.gmail)));
        out.put("leet", StudentController.leetViews(leets.findByStudentGmailIgnoreCaseOrderByIdDesc(s.gmail)));
        return out;
    }

    @PostMapping("/students/{gmail}/feedback")
    public Map<String, Object> addFeedback(HttpServletRequest req, @PathVariable String gmail, @RequestBody Map<String, Object> body) {
        Account mentor = auth.require(req, "mentor");
        Account s = menteeOf(mentor, gmail);
        Feedback f = new Feedback();
        f.studentGmail = s.gmail;
        f.mentorGmail = mentor.gmail;
        f.feedbackText = Req.require(body, "text", "Feedback text is required.");
        feedback.save(f);
        hub.publish(s.gmail, mentor.gmail);
        return Map.of("ok", true);
    }

    // ---------- LeetCode review ----------
    @PostMapping("/leetcode/{id}/review")
    public Map<String, Object> reviewLeet(HttpServletRequest req, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Account mentor = auth.require(req, "mentor");
        LeetSubmission l = leets.findById(id).orElseThrow(() -> new ApiException(404, "Submission not found."));
        Account s = menteeOf(mentor, l.studentGmail);
        String verdict = Req.str(body, "verdict");
        if (!verdict.equals("Approved") && !verdict.equals("Needs Improvement"))
            throw new ApiException(400, "Choose a verdict: Approved or Needs Improvement.");
        String text = Req.str(body, "feedback");
        if (text.isEmpty()) throw new ApiException(400, "Please write some feedback for the student.");
        l.verdict = verdict;
        l.mentorFeedback = text.length() > 4000 ? text.substring(0, 4000) : text;
        l.reviewedAt = java.time.LocalDateTime.now();
        leets.save(l);
        activity.record(s, "Mentor reviewed a LeetCode submission", verdict);
        return Map.of("ok", true);
    }

    // ---------- test grading (written answers) ----------
    @GetMapping("/attempts")
    public Map<String, Object> attemptList(HttpServletRequest req) {
        Account mentor = auth.require(req, "mentor");
        List<Account> list = mentees(mentor);
        Map<String, String> names = new HashMap<>();
        for (Account s : list) names.put(s.gmail.toLowerCase(), s.fullName());
        List<Map<String, Object>> out = new ArrayList<>();
        if (!list.isEmpty()) {
            List<String> gmails = list.stream().map(a -> a.gmail).toList();
            for (TestAttempt t : attempts.findByStudentGmailInOrderByIdDesc(gmails)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.id);
                m.put("student", names.getOrDefault(t.studentGmail.toLowerCase(), t.studentGmail));
                m.put("kind", t.kind);
                m.put("date", t.attemptDate.format(StudentController.DATE));
                m.put("status", t.isPending() ? "Pending" : "Graded");
                m.put("score", t.score);
                m.put("total", t.total);
                out.add(m);
                if (out.size() >= 100) break;
            }
        }
        return Map.of("attempts", out);
    }

    @GetMapping("/attempts/{id}")
    public Map<String, Object> attemptDetail(HttpServletRequest req, @PathVariable Long id) {
        Account mentor = auth.require(req, "mentor");
        TestAttempt t = attempts.findById(id).orElseThrow(() -> new ApiException(404, "Attempt not found."));
        Account s = menteeOf(mentor, t.studentGmail);
        Map<String, Object> m = TestController.attemptView(t, answerRepo.findByAttemptIdOrderByPositionAsc(t.id), true);
        m.put("student", s.fullName());
        return m;
    }

    @PostMapping("/attempts/{id}/grade")
    @Transactional
    public Map<String, Object> grade(HttpServletRequest req, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Account mentor = auth.require(req, "mentor");
        TestAttempt t = attempts.findById(id).orElseThrow(() -> new ApiException(404, "Attempt not found."));
        Account s = menteeOf(mentor, t.studentGmail);
        List<AttemptAnswer> rows = answerRepo.findByAttemptIdOrderByPositionAsc(t.id);

        Map<Long, Map<?, ?>> given = new HashMap<>();
        if (body.get("marks") instanceof List<?> ml) {
            for (Object o : ml) {
                if (o instanceof Map<?, ?> mm && mm.get("id") instanceof Number n) given.put(n.longValue(), mm);
            }
        }
        int score = 0, total = 0;
        for (AttemptAnswer a : rows) {
            total += a.maxMarks;
            if (a.isWritten()) {
                Map<?, ?> g = given.get(a.id);
                if (g == null || !(g.get("marks") instanceof Number mk))
                    throw new ApiException(400, "Please enter marks for every written answer (Q" + (a.position + 1) + " is missing).");
                int v = mk.intValue();
                if (v < 0 || v > a.maxMarks)
                    throw new ApiException(400, "Marks for Q" + (a.position + 1) + " must be between 0 and " + a.maxMarks + ".");
                a.marks = v;
                Object c = g.get("comment");
                String comment = c == null ? "" : c.toString().trim();
                a.comment = comment.length() > 1000 ? comment.substring(0, 1000) : comment;
            }
            score += a.marks == null ? 0 : a.marks;
        }
        answerRepo.saveAll(rows);
        t.score = score;
        t.total = total;
        t.status = "Graded";
        t.gradedAt = java.time.LocalDateTime.now();
        String fb = Req.str(body, "feedback");
        t.feedback = fb.length() > 2000 ? fb.substring(0, 2000) : fb;
        attempts.save(t);
        String label = "aptitude".equals(t.kind) ? "Aptitude" : "Technical";
        activity.record(s, "Mentor graded your " + label + " test", score + " / " + total);
        return Map.of("ok", true, "score", score, "total", total);
    }

    // ---------- test builder ----------
    @GetMapping("/tests/{kind}")
    public Map<String, Object> getTest(HttpServletRequest req, @PathVariable String kind) {
        Account mentor = auth.require(req, "mentor");
        TestController.checkKind(kind);
        List<Map<String, Object>> list = new ArrayList<>();
        for (TestQuestion q : questions.findByMentorGmailIgnoreCaseAndKindOrderByIdAsc(mentor.gmail, kind)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("q", q.questionText);
            m.put("type", q.type());
            m.put("options", q.isWritten() ? List.of("", "", "", "") : q.options());
            m.put("correct", q.correctIndex);
            m.put("modelAnswer", q.modelAnswer == null ? "" : q.modelAnswer);
            m.put("rubric", q.rubric == null ? "" : q.rubric);
            m.put("maxMarks", q.marks());
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.put("releaseDay", TestController.releaseDay(configs, mentor.gmail, kind));
        out.put("questions", list);
        return out;
    }

    @PutMapping("/tests/{kind}")
    @Transactional
    public Map<String, Object> publishTest(HttpServletRequest req, @PathVariable String kind, @RequestBody Map<String, Object> body) {
        Account mentor = auth.require(req, "mentor");
        TestController.checkKind(kind);
        Object raw = body.get("questions");
        if (!(raw instanceof List<?> incoming) || incoming.isEmpty()) throw new ApiException(400, "Add at least one question.");

        List<TestQuestion> toSave = new ArrayList<>();
        int number = 0;
        for (Object o : incoming) {
            number++;
            if (!(o instanceof Map<?, ?> qm)) throw new ApiException(400, "Invalid question format.");
            Object text = qm.get("q");
            if (text == null || text.toString().isBlank()) throw new ApiException(400, "Question " + number + " has no question text.");
            boolean written = "written".equals(String.valueOf(qm.get("type")));

            TestQuestion q = new TestQuestion();
            q.mentorGmail = mentor.gmail;
            q.kind = kind;
            q.questionText = text.toString().trim();
            q.qtype = written ? "written" : "mcq";
            int marks = qm.get("maxMarks") instanceof Number mn ? mn.intValue() : (written ? 10 : 1);
            if (marks < 1 || marks > 100) throw new ApiException(400, "Question " + number + ": marks must be between 1 and 100.");
            q.maxMarks = marks;

            if (written) {
                String model = qm.get("modelAnswer") == null ? "" : qm.get("modelAnswer").toString().trim();
                if (model.isEmpty()) throw new ApiException(400, "Question " + number + " is a written question: please add the correct (model) answer.");
                q.modelAnswer = model.length() > 4000 ? model.substring(0, 4000) : model;
                String rub = qm.get("rubric") == null ? "" : qm.get("rubric").toString().trim();
                q.rubric = rub.length() > 2000 ? rub.substring(0, 2000) : rub;
            } else {
                Object opts = qm.get("options");
                Object correct = qm.get("correct");
                if (!(opts instanceof List<?> ol) || ol.size() != 4 || ol.stream().anyMatch(x -> x == null || x.toString().isBlank())
                        || !(correct instanceof Number cn) || cn.intValue() < 0 || cn.intValue() > 3)
                    throw new ApiException(400, "Question " + number + ": a multiple-choice question needs 4 options and a marked correct answer.");
                q.opt0 = ol.get(0).toString().trim();
                q.opt1 = ol.get(1).toString().trim();
                q.opt2 = ol.get(2).toString().trim();
                q.opt3 = ol.get(3).toString().trim();
                q.correctIndex = cn.intValue();
            }
            toSave.add(q);
        }

        int day = body.get("releaseDay") instanceof Number dn ? dn.intValue() : ("aptitude".equals(kind) ? 2 : 4);
        if (day < -1 || day > 6) day = "aptitude".equals(kind) ? 2 : 4;
        TestConfig cfg = configs.findByMentorGmailIgnoreCaseAndKind(mentor.gmail, kind).orElseGet(() -> {
            TestConfig c = new TestConfig();
            c.mentorGmail = mentor.gmail;
            c.kind = kind;
            return c;
        });
        cfg.releaseDay = day;
        configs.save(cfg);

        questions.deleteByMentorGmailIgnoreCaseAndKind(mentor.gmail, kind);
        questions.saveAll(toSave);
        for (Account s : mentees(mentor)) hub.publish(s.gmail);
        return Map.of("ok", true, "count", toSave.size());
    }
}
