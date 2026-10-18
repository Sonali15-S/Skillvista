package com.skillvista.repo;

import com.skillvista.model.AcademicRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AcademicRecordRepository extends JpaRepository<AcademicRecord, Long> {
    List<AcademicRecord> findByStudentGmailIgnoreCaseOrderByIdAsc(String gmail);
}
