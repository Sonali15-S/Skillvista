package com.skillvista.repo;

import com.skillvista.model.TestConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TestConfigRepository extends JpaRepository<TestConfig, Long> {
    Optional<TestConfig> findByMentorGmailIgnoreCaseAndKind(String mentorGmail, String kind);
}
