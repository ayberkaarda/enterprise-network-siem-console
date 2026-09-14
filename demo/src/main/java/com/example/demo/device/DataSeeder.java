package com.example.demo.device;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time demo data seed so a freshly started stack does not render an empty
 * dashboard. Gated behind {@code siem.seed.enabled} (default {@code false});
 * the {@code h2} profile turns it on for local/dev iteration, the
 * {@code postgres} profile leaves it unset so a real deployment never gets
 * silently populated with fake devices.
 *
 * <p>This is a seed, not a simulator: it inserts a fixed set of rows once and
 * stops. It does not keep generating events or moving device status around —
 * that behaviour belongs to the actual scan loop
 * ({@link DeviceService#checkAllDevicesStatusAutomatically()}), which starts
 * probing these rows for real on the next scheduled sweep.
 *
 * <p>Same "is the table empty" check as {@code AdminUserBootstrap} uses for
 * the operator account: it respects an operator who deleted every seeded
 * device instead of re-inserting them on the next restart.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final DeviceRepository deviceRepository;
    private final boolean seedEnabled;

    public DataSeeder(DeviceRepository deviceRepository, @Value("${siem.seed.enabled:false}") boolean seedEnabled) {
        this.deviceRepository = deviceRepository;
        this.seedEnabled = seedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        if (deviceRepository.count() > 0) {
            return;
        }

        List<Device> seedDevices = List.of(
                device("FW-EDGE-01", "10.10.0.1", "FIREWALL", "ACTIVE", 4L),
                device("RTR-WAN-01", "10.10.0.2", "ROUTER", "ACTIVE", 7L),
                device("SW-CORE-01", "10.10.1.1", "SWITCH", "ACTIVE", 2L),
                device("SW-CORE-02", "10.10.1.2", "SWITCH", "ACTIVE", 3L),
                device("SW-ACCESS-05", "10.10.1.15", "SWITCH", "UNKNOWN", 0L),
                device("SRV-DB-01", "10.10.10.5", "SERVER", "ACTIVE", 12L),
                device("SRV-APP-01", "10.10.10.6", "SERVER", "ACTIVE", 9L),
                device("SRV-WEB-01", "10.10.10.7", "SERVER", "INACTIVE", -1L),
                device("SRV-BACKUP-01", "10.10.10.20", "SERVER", "ACTIVE", 21L));

        deviceRepository.saveAll(seedDevices);
        log.info("Seeded {} demo devices (siem.seed.enabled=true).", seedDevices.size());
    }

    private static Device device(String name, String ipAddress, String deviceType, String status, Long latency) {
        Device device = new Device();
        device.setName(name);
        device.setIpAddress(ipAddress);
        device.setDeviceType(deviceType);
        device.setStatus(status);
        device.setLatency(latency);
        return device;
    }
}
