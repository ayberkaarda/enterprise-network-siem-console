package com.example.demo.incident;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentCommentRepository extends JpaRepository<IncidentComment, Long> {

    List<IncidentComment> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
