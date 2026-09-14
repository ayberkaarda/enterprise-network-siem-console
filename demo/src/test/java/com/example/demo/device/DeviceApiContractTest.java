package com.example.demo.device;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the wire contract of both API generations.
 *
 * <p>The unversioned /api/devices endpoints are consumed by the deployed
 * frontend and must keep returning flat, unpaged JSON with exactly the entity
 * field names. The /api/v1 endpoints are the paged, DTO based replacement and
 * report failures as RFC 7807 problem documents.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
// None of the endpoints exercised here are role-gated beyond "authenticated",
// so one mock principal for the whole class is enough; VIEWER is the lowest
// role that satisfies that.
@WithMockUser(username = "contract-test", roles = "VIEWER")
class DeviceApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void legacyCreateReturnsFlatDeviceJson() throws Exception {
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"legacy-router\",\"ipAddress\":\"10.20.30.40\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("legacy-router"))
                .andExpect(jsonPath("$.ipAddress").value("10.20.30.40"))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.latency").value(0))
                .andExpect(jsonPath("$.deviceType").value("SERVER"))
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(6)));
    }

    @Test
    void legacyListReturnsPlainArray() throws Exception {
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"legacy-list-probe\",\"ipAddress\":\"10.20.30.41\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].ipAddress").exists());
    }

    @Test
    void legacyLogsReturnPlainArray() throws Exception {
        mockMvc.perform(get("/api/devices/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void legacyCreateRejectsInvalidIpAsProblemDetail() throws Exception {
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad\",\"ipAddress\":\"999.1.1.1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IP_VALIDATION_FAILURE"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void versionedCreateReturnsCreatedWithResponseDto() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"v1-firewall\",\"ipAddress\":\"192.168.77.10\",\"deviceType\":\"FIREWALL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("v1-firewall"))
                .andExpect(jsonPath("$.ipAddress").value("192.168.77.10"))
                .andExpect(jsonPath("$.deviceType").value("FIREWALL"))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.latency").value(0));
    }

    @Test
    void versionedCreateRejectsInvalidIpThroughBeanValidation() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"ipAddress\":\"not-an-ip\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"))
                .andExpect(jsonPath("$.errors.ipAddress").exists())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void versionedListIsPagedAndFilterable() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"v1-switch\",\"ipAddress\":\"172.16.5.9\",\"deviceType\":\"SWITCH\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/devices")
                        .param("type", "SWITCH")
                        .param("ipPrefix", "172.16.")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].deviceType").value("SWITCH"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("172.16.5.9"))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void versionedLookupOfUnknownIdReturnsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/devices/{id}", 987654321L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Cihaz bulunamadı!"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void versionedLogsArePaged() throws Exception {
        mockMvc.perform(get("/api/v1/logs")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "timestamp,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }
}
