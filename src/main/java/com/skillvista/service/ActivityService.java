package com.skillvista.service;

import com.skillvista.model.Account;
import com.skillvista.model.ActivityLog;
import com.skillvista.model.ScoreSnapshot;
import com.skillvista.repo.ActivityLogRepository;
import com.skillvista.repo.ScoreSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/** Called after every change a student makes: logs it, refreshes the score history and notifies live pages. */
@Service
public class ActivityService {
    private final ActivityLogRepository logs;
    private final ScoreSnapshotRepository snapshots;
    private final MetricsService metrics;
    private final EventHub hub;

    public ActivityService(ActivityLogRepository logs, ScoreSnapshotRepository snapshots, MetricsService metrics, EventHub hub) {
        this.logs = logs;
        this.snapshots = snapshots;
        this.metrics = metrics;
        this.hub = hub;
    }

    public void record(Account student, String activity, String detail) {
        ActivityLog log = new ActivityLog();
        log.studentGmail = student.gmail;
        log.activity = activity;
        log.detail = detail;
        logs.save(log);
        refresh(student);
    }

    /** Recomputes the score, stores this month's reading and pushes a live update to the student and their mentor. */
    public void refresh(Account student) {
        int score = metrics.overallScore(student);
        String ym = YearMonth.now().toString();
        ScoreSnapshot snap = snapshots.findByStudentGmailIgnoreCaseAndYm(student.gmail, ym).orElseGet(() -> {
            ScoreSnapshot s = new ScoreSnapshot();
            s.studentGmail = student.gmail;
            s.ym = ym;
            return s;
        });
        snap.score = score;
        snapshots.save(snap);
        hub.publish(student.gmail, student.mentorGmail);
    }
}
