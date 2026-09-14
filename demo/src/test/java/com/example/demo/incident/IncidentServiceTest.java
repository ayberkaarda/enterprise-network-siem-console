package com.example.demo.incident;

import com.example.demo.common.AuditLog;
import com.example.demo.common.AuditLogRepository;
import com.example.demo.common.IllegalStateTransitionException;
import com.example.demo.common.IncidentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The audit trail is the reason the lifecycle exists, so it is asserted here
 * against a real persistence context rather than against a mock.
 */
@SpringBootTest
@ActiveProfiles("h2")
class IncidentServiceTest {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void everySuccessfulTransitionLeavesAnAuditEntry() {
        Incident incident = incidentService.create(
                "audited incident", "opened by a test", Severity.HIGH, 5L, "T1110");

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getCreatedAt()).isNotNull();
        assertThat(incident.getUpdatedAt()).isNotNull();

        incidentService.transition(incident.getId(), IncidentStatus.ACKNOWLEDGED);
        incidentService.transition(incident.getId(), IncidentStatus.IN_PROGRESS);

        assertThat(messages())
                .contains("Incident #" + incident.getId() + " transitioned from OPEN to ACKNOWLEDGED")
                .contains("Incident #" + incident.getId() + " transitioned from ACKNOWLEDGED to IN_PROGRESS");
    }

    @Test
    void aRejectedTransitionChangesNothingAndLeavesNoAuditEntry() {
        Incident incident = incidentService.create(
                "unchanged incident", "opened by a test", Severity.LOW, null, null);

        assertThatThrownBy(() -> incidentService.transition(incident.getId(), IncidentStatus.CLOSED))
                .isInstanceOf(IllegalStateTransitionException.class);

        assertThat(incidentService.getById(incident.getId()).getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(messages())
                .noneMatch(message -> message.contains("Incident #" + incident.getId() + " transitioned"));
    }

    @Test
    void commentsAreReturnedOldestFirst() {
        Incident incident = incidentService.create(
                "commented incident", null, Severity.MEDIUM, null, null);

        incidentService.addComment(incident.getId(), "analyst-1", "first");
        incidentService.addComment(incident.getId(), "analyst-2", "second");

        assertThat(incidentService.findComments(incident.getId()))
                .extracting(IncidentComment::getBody)
                .containsExactly("first", "second");
    }

    @Test
    void unknownIncidentIsReportedAsSuch() {
        assertThatThrownBy(() -> incidentService.getById(987654324L))
                .isInstanceOf(IncidentNotFoundException.class);
        assertThatThrownBy(() -> incidentService.addComment(987654324L, "a", "b"))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    private List<String> messages() {
        return auditLogRepository.findAll().stream()
                .map(AuditLog::getMessage)
                .toList();
    }
}
