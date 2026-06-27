package com.example.demo.event;

import com.example.demo.entity.AuditLog;
import com.example.demo.repository.AuditLogRepository;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class NetworkEventListener {

    private final AuditLogRepository auditLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Her iki servisi de constructor (yapıcı metot) üzerinden inject ediyoruz
    public NetworkEventListener(AuditLogRepository auditLogRepository, SimpMessagingTemplate messagingTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleDeviceStatusChanged(DeviceStatusChangedEvent event) {
        // 1. Log Mesajını Hazırlama
        String latencyStr = event.getLatency() >= 0 ? event.getLatency() + " ms" : "N/A";
        String logMessage = "Cihaz: " + event.getDevice().getName() + " [" + event.getDevice().getIpAddress() +
                "] durumu '" + event.getOldStatus() + "' değerinden '" + event.getNewStatus() +
                "' değerine değişti. (Gecikme: " + latencyStr + ")";

        // 2. Veritabanına Kalıcı Olarak Kaydetme (Senin yazdığın kısım)
        AuditLog log = new AuditLog(logMessage, LocalDateTime.now());
        auditLogRepository.save(log);
        System.out.println(">>> [EVENT-DRIVEN AUDIT LOG] " + logMessage);

        // 3. WebSocket ile Angular Arayüzüne Canlı Olarak Fırlatma (Yeni eklenen kısım)
        messagingTemplate.convertAndSend("/topic/alerts", event);
    }
}