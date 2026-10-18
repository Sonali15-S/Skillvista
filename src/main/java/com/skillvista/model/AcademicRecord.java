package com.skillvista.model;

import jakarta.persistence.*;

@Entity
@Table(name = "academic_records")
public class AcademicRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String studentGmail;
    @Column(nullable = false) public String sem;
    @Column(nullable = false) public String gpa;
    @Column(nullable = false) public String cgpa;
}
