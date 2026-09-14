package com.example.demo.device;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the wire contract of {@code GET /api/v1/devices/{id}/latency}: a flat,
 * chronologically ascending array capped at {@code limit}, and a 404 problem
 * document for an unknown device.
 *
 * <p>Samples are inserted directly through {@link LatencySampleRepository}
 * rather than by running a real reachability probe through
 * {@code checkDeviceStatus}: that method blocks on real network I/O, which
 * this test has no need to depend on to pin the read side of the contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@WithMockUser(username = "latency-contract-test", roles = "VIEWER")
class DeviceLatencyHistoryApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private LatencySampleRepository latencySampleRepository;

    private Long seedDeviceWithSamples(String name, long... latencies) {
        Device device = new Device();
        device.setName(name);
        device.setIpAddress("10.55.0.1");
        device.setStatus("ACTIVE");
        device.setLatency(0L);
        device.setDeviceType("SERVER");
        Long deviceId = deviceRepository.save(device).getId();

        Instant base = Instant.now().minus(latencies.length, ChronoUnit.MINUTES);
        for (int i = 0; i < latencies.length; i++) {
            LatencySample sample = new LatencySample(deviceId, latencies[i]);
            sample.setRecordedAt(base.plus(i, ChronoUnit.MINUTES));
            latencySampleRepository.save(sample);
        }
        return deviceId;
    }

    @Test
    void historyIsReturnedOldestFirst() throws Exception {
        Long deviceId = seedDeviceWithSamples("latency-history-device", 10L, 20L, 30L);

        mockMvc.perform(get("/api/v1/devices/{id}/latency", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].latency").value(10))
                .andExpect(jsonPath("$[1].latency").value(20))
                .andExpect(jsonPath("$[2].latency").value(30))
                .andExpect(jsonPath("$[0].recordedAt").exists());
    }

    @Test
    void historyIsCappedAtTheRequestedLimitButStaysChronological() throws Exception {
        Long deviceId = seedDeviceWithSamples("latency-history-capped", 1L, 2L, 3L, 4L, 5L);

        mockMvc.perform(get("/api/v1/devices/{id}/latency", deviceId).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Capped to the two most recent, still oldest-of-the-two first.
                .andExpect(jsonPath("$[0].latency").value(4))
                .andExpect(jsonPath("$[1].latency").value(5));
    }

    @Test
    void historyOfADeviceWithNoSamplesIsAnEmptyArray() throws Exception {
        Device device = new Device();
        device.setName("latency-history-no-samples");
        device.setIpAddress("10.55.0.2");
        device.setStatus("UNKNOWN");
        device.setLatency(0L);
        device.setDeviceType("SERVER");
        Long deviceId = deviceRepository.save(device).getId();

        mockMvc.perform(get("/api/v1/devices/{id}/latency", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void historyOfAnUnknownDeviceIsANotFoundProblemDocument() throws Exception {
        mockMvc.perform(get("/api/v1/devices/{id}/latency", 987654321L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_NOT_FOUND"));
    }
}
