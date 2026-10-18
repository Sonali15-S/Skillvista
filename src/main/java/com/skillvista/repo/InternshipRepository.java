package com.skillvista.repo;

import com.skillvista.model.Internship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InternshipRepository extends JpaRepository<Internship, Long> {
    List<Internship> findByStudentGmailIgnoreCaseOrderByIdAsc(String gmail);
}
