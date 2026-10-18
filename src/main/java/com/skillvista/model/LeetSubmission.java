package com.skillvista.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leet_submissions")
public class LeetSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String studentGmail;
    @Column(name = "entry_date", nullable = false) public LocalDate entryDate;
    public LocalDateTime createdAt = LocalDateTime.now();
    @Column(length = 300) public String title;
    public String questionFile;
    public String questionStored;
    public String codeStored;
    public String verdict;                       // null/"Pending", "Approved", "Needs Improvement"
    @Column(length = 4000) public String mentorFeedback;
    public LocalDateTime reviewedAt;
    public String codeFile;
    @Column(length = 4000) public String explanation;
}
