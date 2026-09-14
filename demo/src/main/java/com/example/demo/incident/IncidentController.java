package com.example.demo.incident;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incident lifecycle API. Failures are reported as RFC 7807 problem documents
 * by the global exception handler: 404 for an unknown incident, 409 for a
 * transition the lifecycle does not allow.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentMapper incidentMapper;

    public IncidentController(IncidentService incidentService, IncidentMapper incidentMapper) {
        this.incidentService = incidentService;
        this.incidentMapper = incidentMapper;
    }

    @GetMapping
    public Page<IncidentResponse> getIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Severity severity,
            Pageable pageable) {
        return incidentService.findWithFilters(status, severity, pageable).map(incidentMapper::toResponse);
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(@Valid @RequestBody IncidentCreateRequest request) {
        Incident saved = incidentService.create(incidentMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentMapper.toResponse(saved));
    }

    @GetMapping("/{id}")
    public IncidentResponse getIncident(@PathVariable Long id) {
        return incidentMapper.toResponse(incidentService.getById(id));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public IncidentResponse transitionIncident(
            @PathVariable Long id, @Valid @RequestBody IncidentTransitionRequest request) {
        return incidentMapper.toResponse(incidentService.transition(id, request.newStatus()));
    }

    @GetMapping("/{id}/comments")
    public List<IncidentCommentResponse> getComments(@PathVariable Long id) {
        return incidentService.findComments(id).stream()
                .map(incidentMapper::toCommentResponse)
                .toList();
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public ResponseEntity<IncidentCommentResponse> addComment(
            @PathVariable Long id, @Valid @RequestBody IncidentCommentRequest request) {
        IncidentComment saved = incidentService.addComment(id, request.author(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentMapper.toCommentResponse(saved));
    }
}
