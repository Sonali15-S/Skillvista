package com.skillvista.repo;

import com.skillvista.model.ScoreSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScoreSnapshotRepository extends JpaRepository<ScoreSnapshot, Long> {
    List<ScoreSnapshot> findByStudentGmailIgnoreCaseOrderByYmAsc(String gmail);
    Optional<ScoreSnapshot> findByStudentGmailIgnoreCaseAndYm(String gmail, String ym);
}
