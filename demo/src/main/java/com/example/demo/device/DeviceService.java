package com.example.demo.device;

import com.example.demo.common.AuditActor;
import com.example.demo.common.AuditLog;
import com.example.demo.common.AuditLogRepository;
import com.example.demo.common.DeviceNotFoundException;
import com.example.demo.common.InvalidIpException;
import com.example.demo.common.IpAddressValidator;
import com.example.demo.common.VirtualThreadFanOut;
import com.example.demo.event.DeviceStatusChangedEvent;
import com.example.demo.incident.IncidentService;
import com.example.demo.incident.Severity;
import com.example.demo.metrics.SiemMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final AuditLogRepository auditLogRepository;
    private final LatencySampleRepository latencySampleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final IncidentService incidentService;
    private final SiemMetrics siemMetrics;
    private final Duration latencySampleRetention;

    // Attack simulation counters exported to Prometheus.
    private final Counter ddosCounter;
    private final Counter sshCounter;

    public DeviceService(
            DeviceRepository deviceRepository,
            AuditLogRepository auditLogRepository,
            LatencySampleRepository latencySampleRepository,
            ApplicationEventPublisher eventPublisher,
            SimpMessagingTemplate messagingTemplate,
            IncidentService incidentService,
            SiemMetrics siemMetrics,
            MeterRegistry meterRegistry,
            @Value("${siem.latency.retention:P30D}") Duration latencySampleRetention) {
        this.deviceRepository = deviceRepository;
        this.auditLogRepository = auditLogRepository;
        this.latencySampleRepository = latencySampleRepository;
        this.eventPublisher = eventPublisher;
        this.messagingTemplate = messagingTemplate;
        this.incidentService = incidentService;
        this.siemMetrics = siemMetrics;
        this.latencySampleRetention = latencySampleRetention;

        this.ddosCounter = meterRegistry.counter("siem_attacks_total", "type", "ddos_portscan");
        this.sshCounter = meterRegistry.counter("siem_attacks_total", "type", "ssh_bruteforce");
    }

    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    /**
     * Paged lookup with optional status, device type and IP prefix filters.
     */
    public Page<Device> searchDevices(String status, String deviceType, String ipPrefix, Pageable pageable) {
        return deviceRepository.findAll(DeviceSpecifications.filterBy(status, deviceType, ipPrefix), pageable);
    }

    public Device getDeviceById(Long id) {
        return deviceRepository.findById(id).orElseThrow(() -> new DeviceNotFoundException("Cihaz bulunamadı!"));
    }

    public Device saveDevice(Device device) {
        if (!IpAddressValidator.isValidIpv4(device.getIpAddress())) {
            System.out.println(">>> [GÜVENLİK ENGELİ] Geçersiz IP denemesi reddedildi: " + device.getIpAddress());
            throw new InvalidIpException(
                    "Verilen '" + device.getIpAddress() + "' adresi kurumsal IPv4 standartlarına uymuyor!");
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
            String logMessage =
                    "Cihaz: " + device.getName() + " [" + device.getIpAddress() + "] sistemden tamamen kaldırıldı.";
            deviceRepository.deleteById(id);
            auditLogRepository.save(new AuditLog(logMessage, LocalDateTime.now(), AuditActor.current()));
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
            latencySampleRepository.save(new LatencySample(device.getId(), measuredLatency));

            if (!oldStatus.equals(newStatus)) {
                eventPublisher.publishEvent(
                        new DeviceStatusChangedEvent(device, oldStatus, newStatus, measuredLatency));
            }

            deviceRepository.save(device);
        }
    }

    // Threat simulation: DDoS / port scan.
    // Raises a tracked incident in addition to the existing audit entry and live
    // alert, so a simulated finding follows the same workflow an analyst uses for
    // a real one instead of only flipping a status string.
    public void simulateCyberAttack(Long id) {
        Device device =
                deviceRepository.findById(id).orElseThrow(() -> new DeviceNotFoundException("Cihaz bulunamadı!"));

        device.setStatus("CRITICAL_THREAT");
        deviceRepository.save(device);

        String alertMessage = "CRITICAL_ALERT: " + device.getIpAddress()
                + " IP adresli cihazda olağandışı trafik (Port Scan / DDoS) tespit edildi!";
        auditLogRepository.save(new AuditLog(alertMessage, LocalDateTime.now(), AuditActor.current()));

        incidentService.create(
                "Anomalous traffic on " + device.getName(),
                "Port scan or denial of service pattern observed against " + device.getName() + " ["
                        + device.getIpAddress() + "].",
                Severity.CRITICAL,
                device.getId(),
                "T1046");

        messagingTemplate.convertAndSend(
                "/topic/alerts", "⚠️ SİSTEM UYARISI: " + device.getIpAddress() + " saldırı altında!");

        ddosCounter.increment();
    }

    // Threat simulation: SSH / PAM brute force.
    public void simulateSshBruteForce(Long id) {
        Device device =
                deviceRepository.findById(id).orElseThrow(() -> new DeviceNotFoundException("Cihaz bulunamadı!"));

        device.setStatus("AUTH_FAILURE");
        deviceRepository.save(device);

        String alertMessage = "PAM İHLALİ: " + device.getIpAddress()
                + " IP adresinde ardışık 5 hatalı SSH giriş denemesi (Kaba Kuvvet)!";
        auditLogRepository.save(new AuditLog(alertMessage, LocalDateTime.now(), AuditActor.current()));

        incidentService.create(
                "SSH brute force against " + device.getName(),
                "Repeated failed SSH authentication attempts observed against " + device.getName() + " ["
                        + device.getIpAddress() + "].",
                Severity.HIGH,
                device.getId(),
                "T1110");

        messagingTemplate.convertAndSend(
                "/topic/alerts", "🚨 GÜVENLİK İHLALİ: " + device.getIpAddress() + " SSH Brute Force tespit edildi!");

        sshCounter.increment();
    }

    /**
     * Background network sweep.
     *
     * <p>The lock makes the sweep run once across the whole deployment rather
     * than once per instance: probing the same host from three places at the
     * same time triples the traffic, produces three competing status writes for
     * it and can raise three copies of the same finding. {@code lockAtLeastFor}
     * additionally absorbs the case where a sweep finishes almost instantly —
     * with nothing to scan, say — and would otherwise let a second instance pick
     * the task up moments later.
     */
    @Scheduled(fixedRate = 30000)
    @SchedulerLock(name = "checkAllDevicesStatusAutomatically", lockAtLeastFor = "PT5S", lockAtMostFor = "PT1M")
    public void checkAllDevicesStatusAutomatically() {
        scanAllDevices();
    }

    /**
     * Runs one sweep and times it.
     *
     * <p>Deliberately outside the lock. The lock exists to stop several
     * instances repeating the same <em>timer</em>; an operator who asks for a
     * scan is asking for one now, and refusing them because a background sweep
     * happened to run a moment ago would be a surprising answer to an explicit
     * request.
     */
    public void scanAllDevices() {
        siemMetrics.recordScan(this::probeAllDevices);
    }

    /**
     * Probes every device, all of them at once.
     *
     * <p>Each probe blocks for up to three seconds waiting for a reply that an
     * unreachable host will never send, so a sequential sweep costs that timeout
     * once per unreachable device and an estate of any size cannot finish inside
     * the thirty second interval. Running the probes on virtual threads makes
     * the sweep cost roughly one timeout in total instead of one per device.
     *
     * <p>Identifiers are collected before the fan-out rather than passing the
     * loaded entities across: each probe reloads and saves its own device inside
     * its own thread, so no persistence context is shared between them.
     */
    private void probeAllDevices() {
        System.out.println(">>> [OTOMATİK GÖREV] Arka plan ağ taraması başladı...");

        List<Long> deviceIds =
                deviceRepository.findAll().stream().map(Device::getId).toList();

        int scanned = VirtualThreadFanOut.forEach(deviceIds, this::checkDeviceStatus);

        System.out.println(">>> [OTOMATİK GÖREV] Arka plan ağ taraması tamamlandı. Taranan cihaz: " + scanned + "/"
                + deviceIds.size());
    }

    /**
     * Daily retention purge for {@link LatencySample}.
     *
     * <p>The scan sweep above writes one row per device roughly every thirty
     * seconds with no upper bound, so left alone the table grows without limit.
     * Rows older than {@code siem.latency.retention} (default 30 days) are no
     * longer useful to the latency history endpoint and are removed here.
     *
     * <p>Locked the same way as {@link #checkAllDevicesStatusAutomatically()}
     * and for the same reason: several instances purging at once would run the
     * same bulk delete redundantly. {@code lockAtLeastFor} covers a purge that
     * finishes almost instantly (nothing past the cutoff yet) from being picked
     * up again by another instance moments later; {@code lockAtMostFor} is a
     * generous ceiling for a bulk delete over a table that, by design, never
     * grows past roughly {@code retention / 30s} rows per device.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "purgeExpiredLatencySamples", lockAtLeastFor = "PT10S", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeExpiredLatencySamples() {
        Instant cutoff = Instant.now().minus(latencySampleRetention);
        int deleted = latencySampleRepository.deleteByRecordedAtBefore(cutoff);
        log.info(
                "Purged {} latency samples recorded before {} (retention={}).",
                deleted,
                cutoff,
                latencySampleRetention);
    }
}
