package com.example.demo.incident;

import static org.assertj.core.api.Assertions.assertThat;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the wire contract of the incident lifecycle API, including the failure
 * shapes: an unknown incident is a 404 problem document and a transition the
 * lifecycle forbids is a 409, both carrying a stable error code.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
// ANALYST covers every endpoint exercised here, including the transition and
// comment endpoints that are role-gated beyond plain authentication.
@WithMockUser(username = "contract-test", roles = "ANALYST")
class IncidentApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long createIncident(String title, Severity severity) throws Exception {
        String body = mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"created by the contract test",
                                 "severity":"%s","sourceDeviceId":1,"mitreTechniqueId":"T1110"}
                                """.formatted(title, severity.name())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value(severity.name()))
                .andExpect(jsonPath("$.mitreTechniqueId").value("T1110"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.get("id").asLong();
    }

    @Test
    void createdIncidentAlwaysStartsOpen() throws Exception {
        long id = createIncident("contract-open", Severity.HIGH);

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("contract-open"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createRejectsAnIncidentWithoutATitle() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"severity\":\"LOW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"))
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void createRejectsAnIncidentWithoutASeverity() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"no severity\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"))
                .andExpect(jsonPath("$.errors.severity").exists());
    }

    @Test
    void lifecycleCanBeWalkedOneStepAtATime() throws Exception {
        long id = createIncident("contract-lifecycle", Severity.MEDIUM);

        transition(id, IncidentStatus.ACKNOWLEDGED);
        transition(id, IncidentStatus.IN_PROGRESS);
        transition(id, IncidentStatus.RESOLVED);
        transition(id, IncidentStatus.CLOSED);
    }

    private void transition(long id, IncidentStatus target) throws Exception {
        mockMvc.perform(post("/api/v1/incidents/{id}/transition", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"%s\"}".formatted(target.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(target.name()));
    }

    @Test
    void skippingTheLifecycleIsAConflictProblemDocument() throws Exception {
        long id = createIncident("contract-illegal", Severity.CRITICAL);

        mockMvc.perform(post("/api/v1/incidents/{id}/transition", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"CLOSED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("Cannot transition incident from OPEN to CLOSED"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void transitionOfUnknownIncidentIsANotFoundProblemDocument() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/{id}/transition", 987654321L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void lookupOfUnknownIncidentIsANotFoundProblemDocument() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/{id}", 987654322L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_FOUND"));
    }

    @Test
    void commentsAreAppendedAndReadBackInOrder() throws Exception {
        long id = createIncident("contract-comments", Severity.LOW);

        mockMvc.perform(post("/api/v1/incidents/{id}/comments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"analyst-1\",\"body\":\"triage started\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.incidentId").value(id))
                .andExpect(jsonPath("$.author").value("analyst-1"))
                .andExpect(jsonPath("$.body").value("triage started"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(post("/api/v1/incidents/{id}/comments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"analyst-2\",\"body\":\"confirmed on the switch\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/incidents/{id}/comments", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].author").value("analyst-1"))
                .andExpect(jsonPath("$[1].author").value("analyst-2"));
    }

    @Test
    void commentOnUnknownIncidentIsANotFoundProblemDocument() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/{id}/comments", 987654323L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"analyst\",\"body\":\"note\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INCIDENT_NOT_FOUND"));
    }

    @Test
    void listIsPagedAndFilterable() throws Exception {
        createIncident("contract-filter", Severity.CRITICAL);

        String body = mockMvc.perform(get("/api/v1/incidents")
                        .param("status", "OPEN")
                        .param("severity", "CRITICAL")
                        .param("page", "0")
                        .param("size", "50")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode content = objectMapper.readTree(body).get("content");
        assertThat(content).isNotEmpty();
        content.forEach(node -> {
            assertThat(node.get("status").asText()).isEqualTo("OPEN");
            assertThat(node.get("severity").asText()).isEqualTo("CRITICAL");
        });
    }
}
