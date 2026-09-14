package com.example.demo.device;

import com.example.demo.common.ValidIpAddress;
import jakarta.validation.constraints.NotBlank;

/**
 * Write model for registering a device. Status and latency are owned by the
 * monitoring loop and therefore cannot be supplied by the caller.
 */
public record DeviceCreateRequest(
        @NotBlank String name,
        @ValidIpAddress String ipAddress,
        String deviceType) {
}
