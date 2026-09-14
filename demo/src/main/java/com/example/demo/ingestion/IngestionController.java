package com.example.demo.ingestion;

import com.example.demo.correlation.CorrelationEngine;
import com.example.demo.metrics.SiemMetrics;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestion endpoint for normalised events from outside this service.
 *
 * <p>The event is persisted first and correlated second: a correlation failure
 * must not cost the record itself, because the stored event is the evidence and
 * the incident is only an interpretation of it.
 */
@RestController
@RequestMapping("/api/v1/events")
public class IngestionController {

    private final IngestedEventRepository ingestedEventRepository;
    private final IngestedEventMapper ingestedEventMapper;
    private final CorrelationEngine correlationEngine;
    private final SiemMetrics siemMetrics;

    public IngestionController(IngestedEventRepository ingestedEventRepository,
                               IngestedEventMapper ingestedEventMapper,
                               CorrelationEngine correlationEngine,
                               SiemMetrics siemMetrics) {
        this.ingestedEventRepository = ingestedEventRepository;
        this.ingestedEventMapper = ingestedEventMapper;
        this.correlationEngine = correlationEngine;
        this.siemMetrics = siemMetrics;
    }

    @PostMapping
    public ResponseEntity<IngestedEventResponse> ingest(@Valid @RequestBody IngestedEventRequest request) {
        IngestedEvent saved = ingestedEventRepository.save(ingestedEventMapper.toEntity(request));
        // Counted after the event is stored, so the meter tracks accepted
        // evidence rather than attempts; a rejected payload never gets this far.
        siemMetrics.countIngestedEvent();
        correlationEngine.onIngestedEvent(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(ingestedEventMapper.toResponse(saved));
    }

    @GetMapping
    public Page<IngestedEventResponse> getEvents(Pageable pageable) {
        return ingestedEventRepository.findAll(pageable).map(ingestedEventMapper::toResponse);
    }
}
