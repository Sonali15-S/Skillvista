package com.skillvista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback")
public class Feedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String studentGmail;
    @Column(nullable = false) public String mentorGmail;
    @Column(length = 4000, nullable = false) public String feedbackText;
    public LocalDateTime createdAt = LocalDateTime.now();
}
