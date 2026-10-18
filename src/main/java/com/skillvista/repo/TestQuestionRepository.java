package com.skillvista.repo;

import com.skillvista.model.TestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestQuestionRepository extends JpaRepository<TestQuestion, Long> {
    List<TestQuestion> findByMentorGmailIgnoreCaseAndKindOrderByIdAsc(String mentorGmail, String kind);
    void deleteByMentorGmailIgnoreCaseAndKind(String mentorGmail, String kind);
}
