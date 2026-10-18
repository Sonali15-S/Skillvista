package com.skillvista.model;

import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false) public String role;            // "student" or "mentor"
    @Column(nullable = false) public String firstName;
    @Column(nullable = false) public String lastName;
    public Integer age;
    @Column(nullable = false, unique = true) public String gmail;
    @Column(nullable = false) public String passwordHash;
    public String deptSec;
    public String regNo;
    @Column(name = "study_year") public String studyYear;
    public String mentorName;
    public String mentorGmail;
    public String cgpa;
    @Column(length = 500) public String linkedinUrl;
    @Column(length = 500) public String leetcodeUrl;
    /** Problems the student had already solved on LeetCode when they signed up (self-reported base count). */
    public Integer leetcodeSolved;
    public String resumeName;   // original file name, shown to people
    public String resumeFile;   // random name of the file stored on disk

    public String fullName() { return (firstName + " " + lastName).trim(); }

    /** Account details that are safe to send to the browser (no password hash). */
    public Map<String, Object> toPublic() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("role", role); m.put("firstName", firstName); m.put("lastName", lastName);
        m.put("name", fullName()); m.put("age", age); m.put("gmail", gmail); m.put("deptSec", deptSec);
        m.put("regNo", regNo); m.put("year", studyYear); m.put("mentorName", mentorName);
        m.put("mentorGmail", mentorGmail); m.put("cgpa", cgpa);
        m.put("linkedinUrl", linkedinUrl); m.put("leetcodeUrl", leetcodeUrl);
        m.put("leetcodeSolved", leetcodeSolved == null ? 0 : leetcodeSolved);
        m.put("resumeName", resumeName); m.put("hasResume", resumeFile != null);
        return m;
    }
}
