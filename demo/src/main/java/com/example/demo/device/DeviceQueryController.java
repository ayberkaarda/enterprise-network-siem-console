package com.example.demo.device;

import com.example.demo.common.AuditLogMapper;
import com.example.demo.common.AuditLogRepository;
import com.example.demo.common.AuditLogResponse;
import com.example.demo.common.AuditLogSpecifications;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned device and audit log API: DTO based, paged and filterable.
 * The unversioned /api/devices endpoints remain available unchanged for the
 * currently deployed frontend.
 */
@RestController
@RequestMapping("/api/v1")
public class DeviceQueryController {

    private static final int DEFAULT_LATENCY_LIMIT = 100;
    private static final int MAX_LATENCY_LIMIT = 1000;

    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final LatencySampleRepository latencySampleRepository;

    public DeviceQueryController(
            DeviceService deviceService,
            DeviceMapper deviceMapper,
            AuditLogRepository auditLogRepository,
            AuditLogMapper auditLogMapper,
            LatencySampleRepository latencySampleRepository) {
        this.deviceService = deviceService;
        this.deviceMapper = deviceMapper;
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
        this.latencySampleRepository = latencySampleRepository;
    }

    @GetMapping("/devices")
    public Page<DeviceResponse> getDevices(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String ipPrefix,
            Pageable pageable) {
        return deviceService.searchDevices(status, type, ipPrefix, pageable).map(deviceMapper::toResponse);
    }

    @GetMapping("/devices/{id}")
    public DeviceResponse getDevice(@PathVariable Long id) {
        return deviceMapper.toResponse(deviceService.getDeviceById(id));
    }

    @PostMapping("/devices")
    public ResponseEntity<DeviceResponse> createDevice(@Valid @RequestBody DeviceCreateRequest request) {
        Device saved = deviceService.saveDevice(deviceMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceMapper.toResponse(saved));
    }

    /**
     * Flat, chronologically ascending (oldest first) history of latency
     * readings for one device, capped at {@code limit} most recent samples.
     * Not paginated: this feeds a sparkline/chart, not a table, so the
     * response is a plain array rather than a {@code Page} envelope.
     */
    @GetMapping("/devices/{id}/latency")
    public List<LatencySampleResponse> getDeviceLatencyHistory(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_LATENCY_LIMIT) int limit) {
        // Validates the device exists (404 DEVICE_NOT_FOUND otherwise) before
        // querying its history; an unknown id has no history to be empty about.
        deviceService.getDeviceById(id);

        int cappedLimit = Math.min(Math.max(limit, 1), MAX_LATENCY_LIMIT);
        List<LatencySample> newestFirst = latencySampleRepository.findByDeviceIdOrderByRecordedAtDesc(
                id, PageRequest.of(0, cappedLimit, Sort.by(Sort.Direction.DESC, "recordedAt")));

        List<LatencySampleResponse> chronological = newestFirst.stream()
                .map(sample -> new LatencySampleResponse(sample.getLatency(), sample.getRecordedAt()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(chronological);
        return chronological;
    }

    @GetMapping("/logs")
    public Page<AuditLogResponse> getLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        return auditLogRepository
                .findAll(AuditLogSpecifications.timestampBetween(from, to), pageable)
                .map(auditLogMapper::toResponse);
    }
}
