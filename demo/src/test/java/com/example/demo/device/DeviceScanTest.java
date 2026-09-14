package com.example.demo.device;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the two scalability properties of the background sweep: that it probes
 * the whole estate at once rather than one host at a time, and that only one
 * instance of the deployment performs it.
 */
@SpringBootTest
@ActiveProfiles("h2")
class DeviceScanTest {

    /**
     * Addresses from the range reserved for documentation, which by definition
     * routes nowhere. Each probe therefore runs into the three second
     * reachability timeout, which is the cost the fan-out exists to absorb.
     */
    private static final String UNROUTABLE_PREFIX = "192.0.2.";

    private static final int DEVICE_COUNT = 12;

    /** Probe timeout in DeviceService, in milliseconds. */
    private static final long PROBE_TIMEOUT_MS = 3_000L;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyDeviceIsProbedAndTheSweepDoesNotCostOneTimeoutPerDevice() {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= DEVICE_COUNT; i++) {
            Device device = new Device();
            device.setName("scan-probe-" + i);
            device.setIpAddress(UNROUTABLE_PREFIX + i);
            device.setStatus("UNKNOWN");
            device.setLatency(0L);
            device.setDeviceType("SERVER");
            ids.add(deviceRepository.save(device).getId());
        }

        long startedAt = System.nanoTime();
        deviceService.scanAllDevices();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        List<Device> scanned = deviceRepository.findAllById(ids);
        assertThat(scanned).hasSize(DEVICE_COUNT);
        assertThat(scanned)
                .as("every device must have been probed, none left at its pre-scan state")
                .noneMatch(device -> "UNKNOWN".equals(device.getStatus()));

        assertThat(elapsedMs)
                .as("%d unreachable devices probed one after another would cost about %d ms",
                        DEVICE_COUNT, DEVICE_COUNT * PROBE_TIMEOUT_MS)
                .isLessThan(DEVICE_COUNT * PROBE_TIMEOUT_MS / 2);
    }

    @Test
    void theScheduledSweepIsGuardedByALockInTheApplicationDatabase() {
        deviceService.checkAllDevicesStatusAutomatically();

        Long rows = jdbcTemplate.queryForObject(
                "select count(*) from shedlock where name = ?",
                Long.class,
                "checkAllDevicesStatusAutomatically");

        assertThat(rows)
                .as("the scheduled sweep must have registered its lock")
                .isEqualTo(1L);
    }

    @Test
    void aHeldLockCannotBeTakenTwice() {
        LockConfiguration configuration = new LockConfiguration(
                Instant.now(),
                "scan-test-" + UUID.randomUUID(),
                Duration.ofSeconds(30),
                Duration.ZERO);

        Optional<SimpleLock> first = lockProvider.lock(configuration);
        assertThat(first).as("the lock table must be usable on this profile").isPresent();

        assertThat(lockProvider.lock(configuration))
                .as("a second holder must be refused while the first still holds it")
                .isEmpty();

        first.get().unlock();
    }
}
