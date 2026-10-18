package com.skillvista.repo;

import com.skillvista.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findTop8ByStudentGmailIgnoreCaseOrderByIdDesc(String gmail);
    List<ActivityLog> findTop10ByStudentGmailInOrderByIdDesc(Collection<String> gmails);
}
