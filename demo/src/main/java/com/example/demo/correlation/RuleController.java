package com.example.demo.correlation;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correlation rule CRUD API. Rules are data, not code (see {@link Rule}'s
 * Javadoc); this controller is the only place that data is written from
 * outside a migration or the built-in seed. Listing is open to any
 * authenticated role; writes require {@code ADMIN} since a bad rule can flood
 * the incident queue or silently blind the correlation engine.
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService ruleService;
    private final RuleMapper ruleMapper;

    public RuleController(RuleService ruleService, RuleMapper ruleMapper) {
        this.ruleService = ruleService;
        this.ruleMapper = ruleMapper;
    }

    @GetMapping
    public Page<RuleResponse> getRules(Pageable pageable) {
        return ruleService.list(pageable).map(ruleMapper::toResponse);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RuleResponse> createRule(@Valid @RequestBody RuleCreateRequest request) {
        Rule saved = ruleService.create(ruleMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleMapper.toResponse(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RuleResponse updateRule(@PathVariable Long id, @Valid @RequestBody RuleUpdateRequest request) {
        Rule changes = ruleMapper.toEntity(request);
        return ruleMapper.toResponse(ruleService.update(id, changes));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
