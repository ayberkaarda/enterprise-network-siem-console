package com.example.demo.ingestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the wire contract of the event ingestion endpoint, including the
 * distinction between the time the producer claims and the time of receipt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
// Ingestion is authenticated but not role-gated; a producer with any role can
// post events, so VIEWER is enough here.
@WithMockUser(username = "contract-test", roles = "VIEWER")
class IngestionApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptedEventIsStoredAndEchoedBack() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"10.44.0.9","category":"AUTH","severity":"MEDIUM",
                                 "rawPayload":"sshd[1234]: Failed password for invalid user",
                                 "occurredAt":"2026-09-14T09:30:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.source").value("10.44.0.9"))
                .andExpect(jsonPath("$.category").value("AUTH"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.rawPayload").value("sshd[1234]: Failed password for invalid user"))
                .andExpect(jsonPath("$.occurredAt").exists())
                .andExpect(jsonPath("$.receivedAt").exists());
    }

    @Test
    void severityAndOccurrenceTimeDefaultWhenTheProducerOmitsThem() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"collector-a\",\"category\":\"NETFLOW\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity").value("INFO"))
                .andExpect(jsonPath("$.occurredAt").exists())
                .andExpect(jsonPath("$.receivedAt").exists());
    }

    @Test
    void eventWithoutSourceOrCategoryIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"\",\"category\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"))
                .andExpect(jsonPath("$.errors.source").exists())
                .andExpect(jsonPath("$.errors.category").exists());
    }

    @Test
    void storedEventsArePaged() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"collector-b\",\"category\":\"SYSLOG\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/events")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }
}
