package com.example.demo.device;

import com.example.demo.common.AuditLogMapper;
import com.example.demo.common.AuditLogRepository;
import com.example.demo.common.AuditLogResponse;
import com.example.demo.common.AuditLogSpecifications;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import java.time.LocalDateTime;

/**
 * Versioned device and audit log API: DTO based, paged and filterable.
 * The unversioned /api/devices endpoints remain available unchanged for the
 * currently deployed frontend.
 */
@RestController
@RequestMapping("/api/v1")
public class DeviceQueryController {

    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    public DeviceQueryController(DeviceService deviceService,
                                 DeviceMapper deviceMapper,
                                 AuditLogRepository auditLogRepository,
                                 AuditLogMapper auditLogMapper) {
        this.deviceService = deviceService;
        this.deviceMapper = deviceMapper;
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
    }

    @GetMapping("/devices")
    public Page<DeviceResponse> getDevices(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(required = false) String ipPrefix,
                                           Pageable pageable) {
        return deviceService.searchDevices(status, type, ipPrefix, pageable)
                .map(deviceMapper::toResponse);
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

    @GetMapping("/logs")
    public Page<AuditLogResponse> getLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        return auditLogRepository.findAll(AuditLogSpecifications.timestampBetween(from, to), pageable)
                .map(auditLogMapper::toResponse);
    }
}
