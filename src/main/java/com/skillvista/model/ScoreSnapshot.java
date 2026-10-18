package com.skillvista.model;

import jakarta.persistence.*;

/** One overall-score reading per student per month, used for the "Skill Score Progress" chart. */
@Entity
@Table(name = "score_snapshots")
public class ScoreSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String studentGmail;
    @Column(name = "ym", nullable = false) public String ym;   // e.g. 2026-10
    public int score;
}
