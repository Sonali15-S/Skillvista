package com.skillvista.repo;

import com.skillvista.model.TestAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, Long> {
    List<TestAttempt> findByStudentGmailIgnoreCase(String gmail);
    List<TestAttempt> findByStudentGmailIgnoreCaseOrderByIdDesc(String gmail);
    List<TestAttempt> findByStudentGmailInOrderByIdDesc(java.util.Collection<String> gmails);
    boolean existsByStudentGmailIgnoreCaseAndKindAndAttemptDate(String gmail, String kind, LocalDate attemptDate);
}
