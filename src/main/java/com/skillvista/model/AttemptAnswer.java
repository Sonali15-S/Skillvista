package com.skillvista.model;

import jakarta.persistence.*;

/** One answered question inside a test attempt. The question, model answer and rubric are copied here so
 *  grading still works even if the mentor later edits or re-publishes the test. */
@Entity
@Table(name = "attempt_answers")
public class AttemptAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public Long attemptId;
    public int position;
    public String qtype;                                   // "mcq" or "written"
    @Column(length = 2000) public String questionText;
    @Column(length = 4000) public String studentAnswer;    // typed answer, or the chosen option's text
    @Column(length = 4000) public String correctAnswer;    // correct option text, or the mentor's model answer
    @Column(length = 2000) public String rubric;
    public int maxMarks;
    public Integer marks;                                  // null until graded
    @Column(length = 1000) public String comment;

    public boolean isWritten() { return "written".equals(qtype); }
}
