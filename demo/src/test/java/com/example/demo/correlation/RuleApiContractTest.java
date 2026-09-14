package com.example.demo.correlation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the wire contract of the correlation rule CRUD API: listing is open to
 * any authenticated role, writes require {@code ADMIN}, an unknown id is a
 * 404 problem document and a syntactically broken {@code conditionJson} is a
 * 400, all with stable error codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class RuleApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String CREATE_BODY = """
            {"name":"contract-flap-rule","enabled":true,"conditionJson":"{\\"type\\":\\"flap\\"}",
             "thresholdCount":3,"windowSeconds":300,"severity":"HIGH"}
            """;

    @Test
    @WithMockUser(username = "rule-admin", roles = "ADMIN")
    void createdRuleRoundTripsThroughListAndUpdate() throws Exception {
        String body = mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("contract-flap-rule"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.thresholdCount").value(3))
                .andExpect(jsonPath("$.windowSeconds").value(300))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get("/api/v1/rules").param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());

        mockMvc.perform(put("/api/v1/rules/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"contract-flap-rule-renamed","enabled":false,
                                 "conditionJson":"{\\"type\\":\\"flap\\"}",
                                 "thresholdCount":5,"windowSeconds":600,"severity":"MEDIUM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("contract-flap-rule-renamed"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.thresholdCount").value(5))
                .andExpect(jsonPath("$.windowSeconds").value(600))
                .andExpect(jsonPath("$.severity").value("MEDIUM"));

        mockMvc.perform(delete("/api/v1/rules/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/rules/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RULE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "rule-viewer", roles = "VIEWER")
    void listIsOpenToAnyAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/api/v1/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "rule-analyst", roles = "ANALYST")
    void createRejectsANonAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_ROLE"));
    }

    @Test
    @WithMockUser(username = "rule-admin-2", roles = "ADMIN")
    void createRejectsSyntacticallyInvalidConditionJson() throws Exception {
        mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"broken-json-rule","enabled":true,
                                 "conditionJson":"{not valid json",
                                 "thresholdCount":1,"windowSeconds":60,"severity":"LOW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"));
    }

    @Test
    @WithMockUser(username = "rule-admin-2b", roles = "ADMIN")
    void createRejectsAConditionWithoutATypeField() throws Exception {
        mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"no-type-rule","enabled":true,
                                 "conditionJson":"{\\"prefixOctets\\":3}",
                                 "thresholdCount":1,"windowSeconds":60,"severity":"LOW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"));
    }

    @Test
    @WithMockUser(username = "rule-admin-2c", roles = "ADMIN")
    void createRejectsAConditionWithAnUnknownType() throws Exception {
        mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"unknown-type-rule","enabled":true,
                                 "conditionJson":"{\\"type\\":\\"totaly_not_a_real_type\\"}",
                                 "thresholdCount":1,"windowSeconds":60,"severity":"LOW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"));
    }

    @Test
    @WithMockUser(username = "rule-admin-2d", roles = "ADMIN")
    void updateRejectsAConditionWithAnUnknownType() throws Exception {
        String body = mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(put("/api/v1/rules/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"contract-flap-rule","enabled":true,
                                 "conditionJson":"{\\"type\\":\\"totaly_not_a_real_type\\"}",
                                 "thresholdCount":3,"windowSeconds":300,"severity":"HIGH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"));
    }

    @Test
    @WithMockUser(username = "rule-admin-3", roles = "ADMIN")
    void createRejectsARuleWithoutAName() throws Exception {
        mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","enabled":true,"conditionJson":"{\\"type\\":\\"flap\\"}",
                                 "thresholdCount":1,"windowSeconds":60,"severity":"LOW"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILURE"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @WithMockUser(username = "rule-admin-4", roles = "ADMIN")
    void updateOfUnknownRuleIsANotFoundProblemDocument() throws Exception {
        mockMvc.perform(put("/api/v1/rules/{id}", 987654321L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RULE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "rule-admin-5", roles = "ADMIN")
    void deleteOfUnknownRuleIsANotFoundProblemDocument() throws Exception {
        mockMvc.perform(delete("/api/v1/rules/{id}", 987654322L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RULE_NOT_FOUND"));
    }
}
