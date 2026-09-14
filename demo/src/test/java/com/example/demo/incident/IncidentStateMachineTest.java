package com.example.demo.incident;

import com.example.demo.common.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The lifecycle is the part of this system an auditor reads. These tests pin
 * both halves of it: the path an incident is allowed to take, and the shortcuts
 * it must not be able to take.
 */
class IncidentStateMachineTest {

    @Test
    void fullLifecyclePathIsLegalStepByStep() {
        assertThatCode(() -> {
            IncidentStateMachine.validateTransition(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED);
            IncidentStateMachine.validateTransition(IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);
            IncidentStateMachine.validateTransition(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED);
            IncidentStateMachine.validateTransition(IncidentStatus.RESOLVED, IncidentStatus.CLOSED);
        }).doesNotThrowAnyException();
    }

    @Test
    void resolvedIncidentMayBeReopened() {
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.RESOLVED, IncidentStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void closingAnOpenIncidentDirectlyIsRejected() {
        assertThatThrownBy(() ->
                IncidentStateMachine.validateTransition(IncidentStatus.OPEN, IncidentStatus.CLOSED))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessage("Cannot transition incident from OPEN to CLOSED");
    }

    @Test
    void skippingAcknowledgementIsRejected() {
        assertThatThrownBy(() ->
                IncidentStateMachine.validateTransition(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessage("Cannot transition incident from OPEN to IN_PROGRESS");
    }

    @Test
    void closedIsTerminal() {
        for (IncidentStatus target : IncidentStatus.values()) {
            assertThat(IncidentStateMachine.canTransition(IncidentStatus.CLOSED, target))
                    .as("CLOSED -> %s", target)
                    .isFalse();
        }
        assertThat(IncidentStateMachine.allowedTargets(IncidentStatus.CLOSED)).isEmpty();
    }

    @Test
    void transitioningToTheSameStateIsRejected() {
        for (IncidentStatus status : IncidentStatus.values()) {
            assertThat(IncidentStateMachine.canTransition(status, status))
                    .as("%s -> %s", status, status)
                    .isFalse();
        }
    }

    @Test
    void nullStatesAreNeverTransitionable() {
        assertThat(IncidentStateMachine.canTransition(null, IncidentStatus.OPEN)).isFalse();
        assertThat(IncidentStateMachine.canTransition(IncidentStatus.OPEN, null)).isFalse();
        assertThat(IncidentStateMachine.allowedTargets(null)).isEmpty();
    }

    @Test
    void allowedTargetsMatchTheDocumentedLifecycle() {
        assertThat(IncidentStateMachine.allowedTargets(IncidentStatus.OPEN))
                .containsExactly(IncidentStatus.ACKNOWLEDGED);
        assertThat(IncidentStateMachine.allowedTargets(IncidentStatus.ACKNOWLEDGED))
                .containsExactly(IncidentStatus.IN_PROGRESS);
        assertThat(IncidentStateMachine.allowedTargets(IncidentStatus.IN_PROGRESS))
                .containsExactly(IncidentStatus.RESOLVED);
        assertThat(IncidentStateMachine.allowedTargets(IncidentStatus.RESOLVED))
                .containsExactlyInAnyOrder(IncidentStatus.IN_PROGRESS, IncidentStatus.CLOSED);
    }
}
