package com.skillvista.repo;

import com.skillvista.model.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {
    List<AttemptAnswer> findByAttemptIdOrderByPositionAsc(Long attemptId);
}
