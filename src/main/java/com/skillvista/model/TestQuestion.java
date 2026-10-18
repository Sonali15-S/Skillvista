package com.skillvista.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "test_questions")
public class TestQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String mentorGmail;
    @Column(nullable = false) public String kind;        // "aptitude" or "technical"
    @Column(length = 2000, nullable = false) public String questionText;
    @Column(length = 500) public String opt0;
    @Column(length = 500) public String opt1;
    @Column(length = 500) public String opt2;
    @Column(length = 500) public String opt3;
    public int correctIndex;
    /** "mcq" (auto-graded) or "written" (graded by the mentor with the model answer and rubric). */
    public String qtype;
    @Column(length = 4000) public String modelAnswer;
    @Column(length = 2000) public String rubric;
    public Integer maxMarks;

    public boolean isWritten() { return "written".equals(qtype); }
    public String type() { return isWritten() ? "written" : "mcq"; }
    public int marks() { return maxMarks == null || maxMarks < 1 ? (isWritten() ? 10 : 1) : maxMarks; }
    public List<String> options() { return isWritten() ? List.of() : List.of(opt0, opt1, opt2, opt3); }
}
