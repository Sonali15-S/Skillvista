package com.skillvista.model;

import jakarta.persistence.*;

/** Which weekday a mentor releases a test on (0 = Sunday ... 6 = Saturday, -1 = any day). */
@Entity
@Table(name = "test_config")
public class TestConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String mentorGmail;
    @Column(nullable = false) public String kind;
    public int releaseDay;
}
