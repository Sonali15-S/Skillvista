package com.skillvista.repo;

import com.skillvista.model.LeetSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface LeetSubmissionRepository extends JpaRepository<LeetSubmission, Long> {
    List<LeetSubmission> findByStudentGmailIgnoreCaseOrderByIdAsc(String gmail);
    List<LeetSubmission> findByStudentGmailIgnoreCaseOrderByIdDesc(String gmail);
    List<LeetSubmission> findByStudentGmailIgnoreCaseAndEntryDateOrderByIdAsc(String gmail, LocalDate entryDate);
}
