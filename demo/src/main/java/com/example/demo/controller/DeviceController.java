package com.example.demo.controller;

import com.example.demo.entity.Device;
import com.example.demo.entity.AuditLog; // 1. Log entity import edildi
import com.example.demo.repository.AuditLogRepository; // 2. Log repository import edildi
import com.example.demo.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // Bu sınıfın JSON veri dönen bir API Controller olduğunu belirtir
@RequestMapping("/api/devices") // Bu controller'ın kök URL adresini belirler
@CrossOrigin(origins = "*") // Angular frontend uygulmana tarayıcı engeline takılmadan erişim izni verir
public class DeviceController {

    private final DeviceService deviceService;
    private final AuditLogRepository auditLogRepository; // 3. Log repository enjekte edildi

    // Constructor Injection: İki bağımlılığı da içeri alıyoruz
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
    public void checkDeviceStatus(@PathVariable Long id) {
        deviceService.checkDeviceStatus(id);
    }

    // 4. Tüm Güvenlik Loglarını Getir (GET http://localhost:8080/api/devices/logs)
    @GetMapping("/logs")
    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }

    // 5. Cihaz Sil (Metot seviyesinde CORS iznini garantiye alıyoruz)
    @DeleteMapping("/{id}")
    @CrossOrigin(origins = "*") // Bu satırı metodun hemen üstüne ekle
    public void deleteDevice(@PathVariable Long id) {
        deviceService.deleteDevice(id);
    }

    // 6. Tüm Ağı Elle Tetikleyerek Tara (POST http://localhost:8080/api/devices/scan)
    @PostMapping("/scan")
    @CrossOrigin(origins = "*")
    public void scanAllDevices() {
        System.out.println(">>> [MANUEL TETİKLEME] Kullanıcı tüm ağ taramasını başlattı.");
        deviceService.checkAllDevicesStatusAutomatically();
    }

    // 7. Siber Saldırı Simülasyonu (YENİ EKLENEN METOT)
    @PostMapping("/{id}/attack")
    @CrossOrigin(origins = "*")
    public void simulateAttack(@PathVariable Long id) {
        System.out.println(">>> [KIRMIZI TAKIM] Tehdit simülasyonu başlatıldı: Cihaz ID " + id);
        deviceService.simulateCyberAttack(id);
    }

    @PostMapping("/{id}/ssh-bruteforce")
    @CrossOrigin(origins = "*")
    public void simulateSshBruteForce(@PathVariable Long id) {
        System.out.println(">>> [KIRMIZI TAKIM] SSH Kaba Kuvvet simülasyonu başlatıldı: Cihaz ID " + id);
        deviceService.simulateSshBruteForce(id);
    }
}