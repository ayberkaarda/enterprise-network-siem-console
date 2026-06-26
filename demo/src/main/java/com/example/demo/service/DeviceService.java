package com.example.demo.service;

import com.example.demo.entity.Device;
import com.example.demo.entity.AuditLog;
import com.example.demo.exception.InvalidIpException;
import com.example.demo.event.DeviceStatusChangedEvent;
import com.example.demo.repository.DeviceRepository;
import com.example.demo.repository.AuditLogRepository;
import org.springframework.context.ApplicationEventPublisher; // YENİ: Event yayınlama aracı
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

@Service // Bu sınıfın bir iş mantığı (Service) katmanı olduğunu Spring'e bildirir
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher; // YENİ: Event tetikleyici enjekte edildi

    // Constructor Injection: Spring Boot bağımlılıkları otomatik bağlar
    public DeviceService(DeviceRepository deviceRepository,
                         AuditLogRepository auditLogRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.deviceRepository = deviceRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
    }

    // 1. Tüm cihazları listeleme fonksiyonu
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    // 2. Yeni cihaz ekleme fonksiyonu (IP DOĞRULAMA + CUSTOM EXCEPTION + TİPOLOJİ)
    public Device saveDevice(Device device) {
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

        if (device.getIpAddress() == null || !device.getIpAddress().matches(ipPattern)) {
            System.out.println(">>> [GÜVENLİK ENGELİ] Geçersiz IP denemesi reddedildi: " + device.getIpAddress());
            // ESKİ HATA YERİNE: Kurumsal custom exception fırlatılıyor
            throw new InvalidIpException("Verilen '" + device.getIpAddress() + "' adresi kurumsal IPv4 standartlarına uymuyor!");
        }

        device.setStatus("UNKNOWN");
        device.setLatency(0L); // İlk eklemede gecikmeyi 0ms yapıyoruz

        // Formdan gelen tipi koru, eğer boşsa varsayılan olarak SERVER ata
        if (device.getDeviceType() == null || device.getDeviceType().isEmpty()) {
            device.setDeviceType("SERVER");
        }

        return deviceRepository.save(device);
    }

    // 5. Cihaz Silme ve Loglama Fonksiyonu (KORUNDU)
    public void deleteDevice(Long id) {
        Device device = deviceRepository.findById(id).orElse(null);

        if (device != null) {
            String logMessage = "Cihaz: " + device.getName() + " [" + device.getIpAddress() + "] sistemden tamamen kaldırıldı.";
            deviceRepository.deleteById(id);
            auditLogRepository.save(new com.example.demo.entity.AuditLog(logMessage, java.time.LocalDateTime.now()));
            System.out.println(">>> [AUDIT LOG GÜVENLİK] " + logMessage);
        }
    }

    // 3. Gerçek Ağ Kontrolü (Ping) Fonksiyonu + ASENKRON OLAY GÜDÜMLÜ MİMARİ
    public void checkDeviceStatus(Long id) {
        Device device = deviceRepository.findById(id).orElse(null);

        if (device != null) {
            String oldStatus = device.getStatus();
            String newStatus = "UNKNOWN";
            Long measuredLatency = 0L;

            try {
                InetAddress address = InetAddress.getByName(device.getIpAddress());

                // Kronometreyi başlatıyoruz
                long startTime = System.currentTimeMillis();

                if (address.isReachable(3000)) {
                    // Yanıt geldiği an kronometreyi durdurup geçen süreyi hesaplıyoruz
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

            // Yeni ölçülen gecikme süresini ve durumu nesneye atıyoruz
            device.setLatency(measuredLatency);
            device.setStatus(newStatus);

            // ÖNEMLİ KURAL (DECOUPLING): Durum değiştiğinde servis log veritabanına doğrudan yazmaz!
            // Sadece ortaya bir olay fırlatır, loglayıcı asenkron olarak kendi işine bakar.
            if (!oldStatus.equals(newStatus)) {
                eventPublisher.publishEvent(new DeviceStatusChangedEvent(device, oldStatus, newStatus, measuredLatency));
            }

            // Latency ve durum bilgisini her taramada güncelliyoruz
            deviceRepository.save(device);
        }
    }

    // 4. Otomatik Arka Plan Taraması (Her 30 saniyede bir çalışır)
    @Scheduled(fixedRate = 30000)
    public void checkAllDevicesStatusAutomatically() {
        System.out.println(">>> [OTOMATİK GÖREV] Arka plan ağ taraması başladı...");

        List<Device> devices = deviceRepository.findAll();

        for (Device device : devices) {
            System.out.println("Taranıyor: " + device.getName() + " (" + device.getIpAddress() + ")");
            checkDeviceStatus(device.getId());
        }

        System.out.println(">>> [OTOMATİK GÖREV] Arka plan ağ taraması tamamlandı.");
    }
}