package com.example.demo.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    /** Queue depth: how many incidents are in any of the unfinished states. */
    long countByStatusIn(Collection<IncidentStatus> statuses);

    /**
     * The same queue depth narrowed to the severities that drive the console's
     * threat level, counted in the database so the live snapshot does not have
     * to load the incident table to filter it.
     */
    long countByStatusInAndSeverityIn(Collection<IncidentStatus> statuses,
                                      Collection<Severity> severities);
}
