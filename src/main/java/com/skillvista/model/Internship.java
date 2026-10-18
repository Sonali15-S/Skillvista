
package com.skillvista.model;

import jakarta.persistence.*;

@Entity
@Table(name = "internships")
public class Internship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "internship_id")
    public Long id;

    @Column(name = "student_gmail", nullable = false)
    public String studentGmail;

    @Column(nullable = false)
    public String domain;

    @Column(nullable = false)
    public String company;

    public String duration;

    @Column(nullable = false)
    public String status;

    @Column(name = "cert_name")
    public String certName;

    @Column(name = "cert_date")
    public String certDate;

    public int knowledge;
}
