package com.example.demo.incident;

import com.example.demo.common.AuditActor;
import com.example.demo.common.AuditLog;
import com.example.demo.common.AuditLogRepository;
import com.example.demo.common.IncidentNotFoundException;
import com.example.demo.realtime.IncidentChangedEvent;
import com.example.demo.realtime.IncidentRealtimeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Owns the incident lifecycle. Every state change goes through
 * {@link IncidentStateMachine} and leaves an audit trail, so the history of an
 * incident can be reconstructed from the log alone.
 *
 * <p>This service deliberately has no dependency on the device layer; the
 * device layer depends on it. Keeping the arrow pointing one way is what lets
 * the correlation engine and the attack simulations both open incidents without
 * a dependency cycle.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository incidentCommentRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentService(IncidentRepository incidentRepository,
                           IncidentCommentRepository incidentCommentRepository,
                           AuditLogRepository auditLogRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.incidentCommentRepository = incidentCommentRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Opens an incident. Callers never choose the initial status: everything
     * starts at {@link IncidentStatus#OPEN}.
     */
    @Transactional
    public Incident create(Incident incident) {
        incident.setId(null);
        incident.setStatus(IncidentStatus.OPEN);
        if (incident.getSeverity() == null) {
            incident.setSeverity(Severity.MEDIUM);
        }
        Incident saved = incidentRepository.save(incident);
        audit("Incident #" + saved.getId() + " opened with severity " + saved.getSeverity()
                + ": " + saved.getTitle());
        announce(saved);
        return saved;
    }

    /**
     * Convenience entry point for automated producers (correlation rules,
     * attack simulations) that build an incident from a handful of values.
     */
    @Transactional
    public Incident create(String title,
                           String description,
                           Severity severity,
                           Long sourceDeviceId,
                           String mitreTechniqueId) {
        Incident incident = new Incident();
        incident.setTitle(title);
        incident.setDescription(description);
        incident.setSeverity(severity);
        incident.setSourceDeviceId(sourceDeviceId);
        incident.setMitreTechniqueId(mitreTechniqueId);
        return create(incident);
    }

    @Transactional(readOnly = true)
    public Incident getById(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException("Incident " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<Incident> findWithFilters(IncidentStatus status, Severity severity, Pageable pageable) {
        return findWithFilters(status, severity, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Incident> findWithFilters(IncidentStatus status,
                                          Severity severity,
                                          Long sourceDeviceId,
                                          Pageable pageable) {
        return incidentRepository.findAll(
                IncidentSpecifications.filterBy(status, severity, sourceDeviceId), pageable);
    }

    /**
     * Advances an incident to {@code newStatus}.
     *
     * @throws com.example.demo.common.IncidentNotFoundException      if no such incident exists
     * @throws com.example.demo.common.IllegalStateTransitionException if the lifecycle forbids the edge
     */
    @Transactional
    public Incident transition(Long incidentId, IncidentStatus newStatus) {
        Incident incident = getById(incidentId);
        IncidentStatus oldStatus = incident.getStatus();

        IncidentStateMachine.validateTransition(oldStatus, newStatus);

        incident.setStatus(newStatus);
        // Flushed rather than merely saved so the update callback has run and
        // updatedAt already carries the time of this transition: the snapshot
        // handed to the live consoles is built from this instance, and a value
        // written at commit time would arrive there one transition stale.
        Incident saved = incidentRepository.saveAndFlush(incident);
        audit("Incident #" + saved.getId() + " transitioned from " + oldStatus + " to " + newStatus);
        announce(saved);
        return saved;
    }

    @Transactional
    public IncidentComment addComment(Long incidentId, String author, String body) {
        Incident incident = getById(incidentId);
        IncidentComment comment = incidentCommentRepository.save(
                new IncidentComment(incident.getId(), author, body));
        audit("Incident #" + incident.getId() + " commented on by " + author);
        return comment;
    }

    @Transactional(readOnly = true)
    public List<IncidentComment> findComments(Long incidentId) {
        Incident incident = getById(incidentId);
        return incidentCommentRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
    }

    private void audit(String message) {
        auditLogRepository.save(new AuditLog(message, LocalDateTime.now(), AuditActor.current()));
    }

    /**
     * Announces an incident that has just been written.
     *
     * <p>The outbound snapshot is taken here, while the row is still in front of
     * us and the transaction is still open. Delivery is somebody else's problem:
     * the listener waits for the commit before it pushes anything, so a console
     * never learns about an incident that a rollback then takes away.
     */
    private void announce(Incident incident) {
        eventPublisher.publishEvent(new IncidentChangedEvent(IncidentRealtimeEvent.from(incident)));
    }
}
