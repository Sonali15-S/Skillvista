package com.skillvista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
public class ActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String studentGmail;
    @Column(nullable = false) public String activity;
    public String detail;
    public LocalDateTime createdAt = LocalDateTime.now();
}
