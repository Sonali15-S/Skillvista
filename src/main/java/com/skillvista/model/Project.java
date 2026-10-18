package com.skillvista.model;

import jakarta.persistence.*;

@Entity
@Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String studentGmail;
    @Column(nullable = false) public String title;
    @Column(length = 4000) public String summary;
    public String pptName;
    public String pptDate;
}
