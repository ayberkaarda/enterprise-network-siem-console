package com.example.demo.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the wire contract of the authentication endpoints and of the security
 * filter chain wired around every other one: no token is a 401 with
 * {@code AUTH_REQUIRED}, a valid token of the wrong role is a 403 with
 * {@code INSUFFICIENT_ROLE}, and a valid token of a sufficient role reaches the
 * controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AuthApiContractTest {

    /**
     * {@code AdminUserBootstrap} creates this account on a database with no
     * users yet. The h2 profile sets no {@code siem.bootstrap.admin-password},
     * so its built-in development fallback applies.
     */
    private static final String BOOTSTRAPPED_ADMIN_USERNAME = "admin";
    private static final String BOOTSTRAPPED_ADMIN_PASSWORD = "changeme-on-first-login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginWithValidCredentialsReturnsATokenPair() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                .formatted(BOOTSTRAPPED_ADMIN_USERNAME, BOOTSTRAPPED_ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    void loginWithAWrongPasswordIsRejectedAsAProblemDocument() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"not-the-password\"}"
                                .formatted(BOOTSTRAPPED_ADMIN_USERNAME)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginOfAnUnknownUserFailsTheSameWayAsAWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody-by-this-name\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshWithAValidTokenReturnsAFreshPair() throws Exception {
        String refreshToken =
                loginAndExtract(BOOTSTRAPPED_ADMIN_USERNAME, BOOTSTRAPPED_ADMIN_PASSWORD, "refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void refreshWithAnUnusableTokenIsRejectedAsAProblemDocument() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void anAuthenticatedGetReachesTheController() throws Exception {
        String accessToken =
                loginAndExtract(BOOTSTRAPPED_ADMIN_USERNAME, BOOTSTRAPPED_ADMIN_PASSWORD, "accessToken");

        mockMvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void aRequestWithNoTokenIsRejectedWithAuthRequired() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void aGarbledBearerTokenIsAlsoRejectedWithAuthRequired() throws Exception {
        mockMvc.perform(get("/api/v1/devices").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void aViewerTokenIsForbiddenFromAnAnalystGatedEndpoint() throws Exception {
        String username = "viewer-contract-test";
        userRepository.save(new User(username, passwordEncoder.encode("viewer-password"), Role.VIEWER, true));

        String accessToken = loginAndExtract(username, "viewer-password", "accessToken");

        mockMvc.perform(post("/api/devices/scan").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_ROLE"));
    }

    private String loginAndExtract(String username, String password, String field) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get(field).asText();
    }
}
