package com.skillvista.web;

import com.skillvista.model.*;
import com.skillvista.repo.*;
import com.skillvista.service.ActivityService;
import com.skillvista.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Student side of the weekly tests. Multiple-choice questions are marked instantly; written questions are stored
 * and wait for the mentor to mark them using the model answer and rubric (see MentorController).
 */
@RestController
@RequestMapping("/api/tests")
public class TestController {
    private final AuthService auth;
    private final TestQuestionRepository questions;
    private final TestAttemptRepository attempts;
    private final AttemptAnswerRepository answerRepo;
    private final TestConfigRepository configs;
    private final ActivityService activity;

    public TestController(AuthService auth, TestQuestionRepository questions, TestAttemptRepository attempts,
                          AttemptAnswerRepository answerRepo, TestConfigRepository configs, ActivityService activity) {
        this.auth = auth;
        this.questions = questions;
        this.attempts = attempts;
        this.answerRepo = answerRepo;
        this.configs = configs;
        this.activity = activity;
    }

    static String checkKind(String kind) {
        if (!kind.equals("aptitude") && !kind.equals("technical")) throw new ApiException(400, "Unknown test type.");
        return kind;
    }

    /** 0 = Sunday ... 6 = Saturday, -1 = any day. Default is Tuesday for aptitude and Thursday for technical. */
    static int releaseDay(TestConfigRepository configs, String mentorGmail, String kind) {
        if (mentorGmail == null) return "aptitude".equals(kind) ? 2 : 4;
        return configs.findByMentorGmailIgnoreCaseAndKind(mentorGmail, kind).map(c -> c.releaseDay)
                .orElse("aptitude".equals(kind) ? 2 : 4);
    }

    static int todayIndex() { return LocalDate.now().getDayOfWeek().getValue() % 7; }

    /** One attempt with all its answers. Students do not see the model answer or rubric until the attempt is graded. */
    static Map<String, Object> attemptView(TestAttempt t, List<AttemptAnswer> rows, boolean revealAnswers) {
        boolean reveal = revealAnswers || !t.isPending();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("kind", t.kind);
        m.put("date", t.attemptDate.format(StudentController.DATE));
        m.put("status", t.isPending() ? "Pending" : "Graded");
        m.put("score", t.score);
        m.put("total", t.total);
        m.put("feedback", t.feedback == null ? "" : t.feedback);
        List<Map<String, Object>> list = new ArrayList<>();
        for (AttemptAnswer a : rows) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", a.id);
            x.put("position", a.position + 1);
            x.put("type", a.isWritten() ? "written" : "mcq");
            x.put("question", a.questionText);
            x.put("studentAnswer", a.studentAnswer == null ? "" : a.studentAnswer);
            x.put("correctAnswer", reveal ? (a.correctAnswer == null ? "" : a.correctAnswer) : "");
            x.put("rubric", reveal ? (a.rubric == null ? "" : a.rubric) : "");
            x.put("maxMarks", a.maxMarks);
            x.put("marks", a.marks);
            x.put("comment", a.comment == null ? "" : a.comment);
            list.add(x);
        }
        m.put("answers", list);
        return m;
    }

    private List<TestQuestion> mentorQuestions(Account s, String kind) {
        if (s.mentorGmail == null) return List.of();
        return questions.findByMentorGmailIgnoreCaseAndKindOrderByIdAsc(s.mentorGmail, kind);
    }

    private void checkOpenToday(Account s, String kind) {
        int day = releaseDay(configs, s.mentorGmail, kind);
        if (day != -1 && day != todayIndex()) throw new ApiException(409, "This test is not open today.");
        if (attempts.existsByStudentGmailIgnoreCaseAndKindAndAttemptDate(s.gmail, kind, LocalDate.now()))
            throw new ApiException(409, "You have already taken today's test.");
    }

    @GetMapping("/{kind}")
    public Map<String, Object> get(HttpServletRequest req, @PathVariable String kind) {
        Account s = auth.require(req, "student");
        checkKind(kind);
        checkOpenToday(s, kind);
        List<Map<String, Object>> list = new ArrayList<>();
        for (TestQuestion q : mentorQuestions(s, kind)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.id);
            m.put("q", q.questionText);
            m.put("type", q.type());
            m.put("options", q.options());
            m.put("maxMarks", q.marks());
            list.add(m);
        }
        if (list.isEmpty()) throw new ApiException(404, "Your mentor has not uploaded this test yet.");
        return Map.of("kind", kind, "questions", list);
    }

    @PostMapping("/{kind}/submit")
    public Map<String, Object> submit(HttpServletRequest req, @PathVariable String kind, @RequestBody Map<String, Object> body) {
        Account s = auth.require(req, "student");
        checkKind(kind);
        checkOpenToday(s, kind);
        List<TestQuestion> qs = mentorQuestions(s, kind);
        if (qs.isEmpty()) throw new ApiException(404, "Your mentor has not uploaded this test yet.");

        Object raw = body.get("answers");
        List<?> given = raw instanceof List<?> l ? l : List.of();

        int score = 0, total = 0;
        boolean hasWritten = false;
        List<AttemptAnswer> rows = new ArrayList<>();
        for (int i = 0; i < qs.size(); i++) {
            TestQuestion q = qs.get(i);
            Object a = i < given.size() ? given.get(i) : null;
            AttemptAnswer r = new AttemptAnswer();
            r.position = i;
            r.qtype = q.type();
            r.questionText = q.questionText;
            r.rubric = q.rubric;
            r.maxMarks = q.marks();
            if (q.isWritten()) {
                hasWritten = true;
                String text = a == null ? "" : a.toString().trim();
                r.studentAnswer = text.length() > 4000 ? text.substring(0, 4000) : text;
                r.correctAnswer = q.modelAnswer;
                r.marks = null;
            } else {
                int sel = a instanceof Number n ? n.intValue() : -1;
                List<String> opts = q.options();
                r.studentAnswer = sel >= 0 && sel < opts.size() ? opts.get(sel) : "(not answered)";
                r.correctAnswer = opts.get(q.correctIndex);
                r.marks = sel == q.correctIndex ? r.maxMarks : 0;
                score += r.marks;
            }
            total += r.maxMarks;
            rows.add(r);
        }

        TestAttempt t = new TestAttempt();
        t.studentGmail = s.gmail;
        t.kind = kind;
        t.attemptDate = LocalDate.now();
        t.score = score;
        t.total = total;
        t.status = hasWritten ? "Pending" : "Graded";
        attempts.save(t);
        for (AttemptAnswer r : rows) r.attemptId = t.id;
        answerRepo.saveAll(rows);

        String label = kind.equals("aptitude") ? "Aptitude" : "Technical";
        if (hasWritten) activity.record(s, "Submitted " + label + " test", "Waiting for mentor to grade");
        else activity.record(s, "Completed " + label + " test", score + " / " + total);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", score);
        out.put("total", total);
        out.put("status", t.status);
        return out;
    }
}
