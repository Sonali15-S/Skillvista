package com.skillvista.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_tokens")
public class AuthToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true) public String token;
    @Column(nullable = false) public String gmail;
    public LocalDateTime createdAt = LocalDateTime.now();
}
