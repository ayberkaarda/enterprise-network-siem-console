package com.example.demo.event;

import com.example.demo.entity.AuditLog;
import com.example.demo.repository.AuditLogRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class NetworkEventListener {

    private final AuditLogRepository auditLogRepository;

    public NetworkEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @EventListener
    public void handleDeviceStatusChanged(DeviceStatusChangedEvent event) {
        String latencyStr = event.getLatency() >= 0 ? event.getLatency() + " ms" : "N/A";
        String logMessage = "Cihaz: " + event.getDevice().getName() + " [" + event.getDevice().getIpAddress() +
                "] durumu '" + event.getOldStatus() + "' değerinden '" + event.getNewStatus() +
                "' değerine değişti. (Gecikme: " + latencyStr + ")";

        AuditLog log = new AuditLog(logMessage, LocalDateTime.now());
        auditLogRepository.save(log);

        System.out.println(">>> [EVENT-DRIVEN AUDIT LOG] " + logMessage);
    }
}