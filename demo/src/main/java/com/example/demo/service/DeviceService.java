package com.example.demo.service;

import com.example.demo.entity.Device;
import com.example.demo.entity.AuditLog;
import com.example.demo.exception.InvalidIpException;
import com.example.demo.event.DeviceStatusChangedEvent;
import com.example.demo.repository.DeviceRepository;
import com.example.demo.repository.AuditLogRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate; // WebSocket mesajlaşma aracı
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import io.micrometer.core.instrument.MeterRegistry; // Grafana Metrik motoru
import io.micrometer.core.instrument.Counter; // Grafana sayaçları

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate; // EKLENDİ: Arayüze alarm basmak için

    // Grafana metrik sayaçları
    private final Counter ddosCounter;
    private final Counter sshCounter;

    // Constructor Injection: Spring Boot tüm bu sınıfları otomatik bağlar
    public DeviceService(DeviceRepository deviceRepository,
                         AuditLogRepository auditLogRepository,
                         ApplicationEventPublisher eventPublisher,
                         SimpMessagingTemplate messagingTemplate,
                         MeterRegistry meterRegistry) {
        this.deviceRepository = deviceRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;

        // Saldırı türlerine göre sayaçlarımızı MeterRegistry'e kaydediyoruz
        this.ddosCounter = meterRegistry.counter("siem_attacks_total", "type", "ddos_portscan");
        this.sshCounter = meterRegistry.counter("siem_attacks_total", "type", "ssh_bruteforce");
    }

    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    public Device saveDevice(Device device) {
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

        if (device.getIpAddress() == null || !device.getIpAddress().matches(ipPattern)) {
            System.out.println(">>> [GÜVENLİK ENGELİ] Geçersiz IP denemesi reddedildi: " + device.getIpAddress());
            throw new InvalidIpException("Verilen '" + device.getIpAddress() + "' adresi kurumsal IPv4 standartlarına uymuyor!");
        }

        device.setStatus("UNKNOWN");
        device.setLatency(0L);

        if (device.getDeviceType() == null || device.getDeviceType().isEmpty()) {
            device.setDeviceType("SERVER");
        }

        return deviceRepository.save(device);
    }

    public void deleteDevice(Long id) {
        Device device = deviceRepository.findById(id).orElse(null);

        if (device != null) {
            String logMessage = "Cihaz: " + device.getName() + " [" + device.getIpAddress() + "] sistemden tamamen kaldırıldı.";
            deviceRepository.deleteById(id);
            auditLogRepository.save(new AuditLog(logMessage, LocalDateTime.now()));
            System.out.println(">>> [AUDIT LOG GÜVENLİK] " + logMessage);
        }
    }

    public void checkDeviceStatus(Long id) {
        Device device = deviceRepository.findById(id).orElse(null);

        if (device != null) {
            String oldStatus = device.getStatus();
            String newStatus = "UNKNOWN";
            Long measuredLatency = 0L;

            try {
                InetAddress address = InetAddress.getByName(device.getIpAddress());
                long startTime = System.currentTimeMillis();

                if (address.isReachable(3000)) {
                    long endTime = System.currentTimeMillis();
                    measuredLatency = endTime - startTime;
                    newStatus = "ACTIVE";
                } else {
                    newStatus = "INACTIVE";
                    measuredLatency = -1L;
                }
            } catch (Exception e) {
                newStatus = "INACTIVE";
                measuredLatency = -1L;
            }

            device.setLatency(measuredLatency);
            device.setStatus(newStatus);

            if (!oldStatus.equals(newStatus)) {
                eventPublisher.publishEvent(new DeviceStatusChangedEvent(device, oldStatus, newStatus, measuredLatency));
            }

            deviceRepository.save(device);
        }
    }

    // 1. Tehdit Simülasyon Metodu (DDoS / Port Scan)
    public void simulateCyberAttack(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cihaz bulunamadı!"));

        device.setStatus("CRITICAL_THREAT");
        deviceRepository.save(device);

        String alertMessage = "CRITICAL_ALERT: " + device.getIpAddress() + " IP adresli cihazda olağandışı trafik (Port Scan / DDoS) tespit edildi!";
        auditLogRepository.save(new AuditLog(alertMessage, LocalDateTime.now()));

        messagingTemplate.convertAndSend("/topic/alerts", "⚠️ SİSTEM UYARISI: " + device.getIpAddress() + " saldırı altında!");

        ddosCounter.increment(); // Metriği Prometheus için 1 artır
    }

    // 2. Tehdit Simülasyon Metodu (SSH / PAM Kaba Kuvvet)
    public void simulateSshBruteForce(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cihaz bulunamadı!"));

        device.setStatus("AUTH_FAILURE");
        deviceRepository.save(device);

        String alertMessage = "PAM İHLALİ: " + device.getIpAddress() + " IP adresinde ardışık 5 hatalı SSH giriş denemesi (Kaba Kuvvet)!";
        auditLogRepository.save(new AuditLog(alertMessage, LocalDateTime.now()));

        messagingTemplate.convertAndSend("/topic/alerts", "🚨 GÜVENLİK İHLALİ: " + device.getIpAddress() + " SSH Brute Force tespit edildi!");

        sshCounter.increment(); // Metriği Prometheus için 1 artır
    }

    @Scheduled(fixedRate = 30000)
    public void checkAllDevicesStatusAutomatically() {
        System.out.println(">>> [OTOMATİK GÖREV] Arka plan ağ taraması başladı...");

        List<Device> devices = deviceRepository.findAll();
        for (Device device : devices) {
            checkDeviceStatus(device.getId());
        }

        System.out.println(">>> [OTOMATİK GÖREV] Arka plan ağ taraması tamamlandı.");
    }
}