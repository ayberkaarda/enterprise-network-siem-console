package com.example.demo.device;

import com.example.demo.common.AuditLog;
import com.example.demo.common.AuditLogRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Legacy device API kept for the currently deployed frontend. The response
 * shapes here are flat entity representations and must stay that way; the
 * paged, DTO based replacement lives in {@link DeviceQueryController} under
 * /api/v1.
 *
 * <p>CORS is handled once, centrally, by {@code SecurityConfig}'s allow-list —
 * not per class or per method here, which is how this controller ended up with
 * a wildcard origin on some endpoints and not others.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final AuditLogRepository auditLogRepository;

    public DeviceController(DeviceService deviceService, AuditLogRepository auditLogRepository) {
        this.deviceService = deviceService;
        this.auditLogRepository = auditLogRepository;
    }

    // 1. Tüm Cihazları Getir (GET http://localhost:8080/api/devices)
    @GetMapping
    public List<Device> getAllDevices() {
        return deviceService.getAllDevices();
    }

    // 2. Yeni Cihaz Ekle (POST http://localhost:8080/api/devices)
    @PostMapping
    public Device addDevice(@RequestBody Device device) {
        return deviceService.saveDevice(device);
    }

    // 3. Cihazın Durumunu Tetikle (POST http://localhost:8080/api/devices/1/check)
    @PostMapping("/{id}/check")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public void checkDeviceStatus(@PathVariable Long id) {
        deviceService.checkDeviceStatus(id);
    }

    // 4. Tüm Güvenlik Loglarını Getir (GET http://localhost:8080/api/devices/logs)
    @GetMapping("/logs")
    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }

    // 5. Cihaz Sil
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public void deleteDevice(@PathVariable Long id) {
        deviceService.deleteDevice(id);
    }

    // 6. Tüm Ağı Elle Tetikleyerek Tara (POST http://localhost:8080/api/devices/scan)
    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public void scanAllDevices() {
        System.out.println(">>> [MANUEL TETİKLEME] Kullanıcı tüm ağ taramasını başlattı.");
        // Calls the sweep itself rather than the scheduled entry point, which is
        // guarded by a lock shared across instances. An operator asking for a
        // scan must get one, not be told a background sweep already ran.
        deviceService.scanAllDevices();
    }

    // 7. Siber Saldırı Simülasyonu
    @PostMapping("/{id}/attack")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public void simulateAttack(@PathVariable Long id) {
        System.out.println(">>> [KIRMIZI TAKIM] Tehdit simülasyonu başlatıldı: Cihaz ID " + id);
        deviceService.simulateCyberAttack(id);
    }

    @PostMapping("/{id}/ssh-bruteforce")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    public void simulateSshBruteForce(@PathVariable Long id) {
        System.out.println(">>> [KIRMIZI TAKIM] SSH Kaba Kuvvet simülasyonu başlatıldı: Cihaz ID " + id);
        deviceService.simulateSshBruteForce(id);
    }
}
