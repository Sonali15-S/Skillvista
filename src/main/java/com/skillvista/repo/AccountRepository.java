package com.skillvista.repo;

import com.skillvista.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByGmailIgnoreCase(String gmail);
    boolean existsByGmailIgnoreCase(String gmail);
    List<Account> findByRoleAndMentorGmailIgnoreCase(String role, String mentorGmail);
}
