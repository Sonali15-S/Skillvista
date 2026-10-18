package com.skillvista.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "test_attempts")
public class TestAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String studentGmail;
    @Column(nullable = false) public String kind;
    @Column(name = "attempt_date", nullable = false) public LocalDate attemptDate;
    public int score;
    public int total;
    /** "Graded" or "Pending" (waiting for the mentor to mark the written answers). Old rows with null count as Graded. */
    public String status;
    @Column(length = 2000) public String feedback;
    public java.time.LocalDateTime gradedAt;

    public boolean isPending() { return "Pending".equals(status); }
}
