package com.skillvista.service;

import com.skillvista.model.*;
import com.skillvista.repo.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Turns a student's real records into the numbers shown on the dashboards.
 * Nothing here is pre-filled: a brand-new student has zeros everywhere until they add data.
 *
 * Score weights (same as the Score Breakdown on the site):
 *   Academic 15% + Internship 25% + LeetCode 20% + Aptitude 20% + Technical 20%
 */
@Service
public class MetricsService {
    private final AcademicRecordRepository academics;
    private final InternshipRepository internships;
    private final LeetSubmissionRepository leets;
    private final TestAttemptRepository attempts;
    private final ScoreSnapshotRepository snapshots;

    public MetricsService(AcademicRecordRepository academics, InternshipRepository internships,
                          LeetSubmissionRepository leets, TestAttemptRepository attempts,
                          ScoreSnapshotRepository snapshots) {
        this.academics = academics;
        this.internships = internships;
        this.leets = leets;
        this.attempts = attempts;
        this.snapshots = snapshots;
    }

    private static final class Parts {
        double academic, internship, leet, aptitude, technical, overall;
    }

    private static double parse(String s) {
        try { return Double.parseDouble(s == null ? "" : s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    public String currentCgpa(Account s) {
        List<AcademicRecord> acad = academics.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail);
        if (!acad.isEmpty()) return acad.get(acad.size() - 1).cgpa;
        return s.cgpa == null ? "0" : s.cgpa;
    }

    private double testAverage(List<TestAttempt> all, String kind) {
        double sum = 0; int n = 0;
        for (TestAttempt a : all) {
            if (kind.equals(a.kind) && a.total > 0 && !a.isPending()) { sum += a.score * 100.0 / a.total; n++; }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** Base count typed at signup (editable in the profile) + every problem submitted on SkillVista. */
    public int leetTotal(Account s) {
        int base = s.leetcodeSolved == null ? 0 : Math.max(0, s.leetcodeSolved);
        return base + leets.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail).size();
    }

    private Parts parts(Account s) {
        Parts p = new Parts();
        p.academic = clamp(parse(currentCgpa(s)) * 10);
        List<Internship> ints = internships.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail);
        p.internship = ints.isEmpty() ? 0 : clamp(ints.stream().mapToInt(i -> i.knowledge).average().orElse(0));
        p.leet = clamp(leetTotal(s)); // 100 problems = 100%
        List<TestAttempt> att = attempts.findByStudentGmailIgnoreCase(s.gmail);
        p.aptitude = clamp(testAverage(att, "aptitude"));
        p.technical = clamp(testAverage(att, "technical"));
        p.overall = 0.15 * p.academic + 0.25 * p.internship + 0.20 * p.leet + 0.20 * p.aptitude + 0.20 * p.technical;
        return p;
    }

    public int overallScore(Account s) { return (int) Math.round(parts(s).overall); }

    public static String statusKey(int overall) { return overall >= 75 ? "good" : overall >= 50 ? "average" : "attention"; }

    public static String statusLabel(int overall) {
        return overall >= 75 ? "On Track — Good" : overall >= 50 ? "Average — Monitor" : "Needs Support";
    }

    public static String readinessLevel(int overall) { return overall >= 75 ? "High" : overall >= 50 ? "Medium" : "Low"; }

    private static Map<String, Object> bar(String label, double value, String display) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", (int) Math.round(value));
        if (display != null) m.put("display", display);
        return m;
    }

    private static Map<String, Object> row(String label, String weight, double pct, String sub) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label); m.put("w", weight); m.put("pct", (int) Math.round(pct)); m.put("sub", sub);
        return m;
    }

    public Map<String, Object> compute(Account s) {
        Parts p = parts(s);
        int overall = (int) Math.round(p.overall);
        List<Internship> ints = internships.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail);
        List<LeetSubmission> leet = leets.findByStudentGmailIgnoreCaseOrderByIdAsc(s.gmail);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("internshipCount", ints.size());
        m.put("leetTotal", leetTotal(s));
        m.put("leetBase", s.leetcodeSolved == null ? 0 : s.leetcodeSolved);
        m.put("leetSubmitted", leet.size());
        m.put("aptitudeProgress", (int) Math.round(p.aptitude));
        m.put("technicalProgress", (int) Math.round(p.technical));
        m.put("cgpa", currentCgpa(s));
        m.put("overallScore", overall);
        m.put("statusKey", statusKey(overall));
        m.put("statusLabel", statusLabel(overall));
        m.put("readinessLevel", readinessLevel(overall));

        m.put("scoreBreakdown", List.of(
                row("Academic Achievements", "15%", p.academic, "Based on your latest CGPA"),
                row("Internship Progress", "25%", p.internship, "Average knowledge gained across internships"),
                row("LeetCode Skill Progress", "20%", p.leet, "Total problems solved incl. your uploads (100 problems = 100%)"),
                row("Aptitude Test Progress", "20%", p.aptitude, "Average of weekly aptitude tests"),
                row("Technical Test Progress", "20%", p.technical, "Average of weekly technical tests")));

        m.put("assessmentBars", List.of(
                bar("Assessment Progress", (p.aptitude + p.technical) / 2, null),
                bar("Aptitude", p.aptitude, null),
                bar("Technical Test", p.technical, null),
                bar("LeetCode", p.leet, null),
                bar("CGPA", p.academic, String.format(Locale.ROOT, "%.1f/10", p.academic / 10)),
                bar("Skill Development", p.overall, null)));

        List<Map<String, Object>> growth = new ArrayList<>();
        for (int i = 0; i < ints.size(); i++) {
            Internship it = ints.get(i);
            String label = "Internship " + (i + 1) + " — " + it.domain + ("In Progress".equals(it.status) ? " (Current)" : "");
            growth.add(bar(label, it.knowledge, null));
        }
        m.put("growthBars", growth);

        Map<String, List<Integer>> byDomain = new LinkedHashMap<>();
        for (Internship it : ints) byDomain.computeIfAbsent(it.domain, k -> new ArrayList<>()).add(it.knowledge);
        List<Map<String, Object>> domains = new ArrayList<>();
        byDomain.forEach((name, vals) -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", name);
            d.put("pct", (int) Math.round(vals.stream().mapToInt(Integer::intValue).average().orElse(0)));
            domains.add(d);
        });
        m.put("domainSkills", domains);

        List<Map<String, Object>> daily = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long count = leet.stream().filter(x -> d.equals(x.entryDate)).count();
            daily.add(bar(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    Math.min(100, count * 10), String.valueOf(count)));
        }
        m.put("leetcodeDaily", daily);

        List<Map<String, Object>> history = new ArrayList<>();
        for (ScoreSnapshot snap : snapshots.findByStudentGmailIgnoreCaseOrderByYmAsc(s.gmail)) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("label", YearMonth.parse(snap.ym).getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            h.put("score", snap.score);
            history.add(h);
        }
        m.put("history", history);
        return m;
    }
}
