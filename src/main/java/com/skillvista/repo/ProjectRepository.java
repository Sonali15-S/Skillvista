package com.skillvista.repo;

import com.skillvista.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByStudentGmailIgnoreCaseOrderByIdAsc(String gmail);
}
