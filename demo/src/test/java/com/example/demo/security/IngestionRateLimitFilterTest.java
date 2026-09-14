package com.example.demo.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Covers the token bucket in front of {@code POST /api/v1/events}
 * ({@code siem.ratelimit.events.*} in application.properties: capacity 20,
 * refilled over 1 second).
 *
 * <p>Requests are pinned to one synthetic remote address so this test's bucket
 * cannot be starved or topped up by any other test hitting the same endpoint
 * from MockMvc's default loopback address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@WithMockUser(username = "rate-limit-test", roles = "VIEWER")
class IngestionRateLimitFilterTest {

    private static final String CLIENT_ADDRESS = "203.0.113.77";
    private static final int BURST_SIZE = 30;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aClientExceedingTheBucketCapacityIsRejectedWithRateLimitExceeded() throws Exception {
        RequestPostProcessor fromFixedAddress = request -> {
            request.setRemoteAddr(CLIENT_ADDRESS);
            return request;
        };

        List<Integer> statuses = new ArrayList<>();
        MvcResult rejected = null;
        for (int i = 0; i < BURST_SIZE && rejected == null; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/events")
                            .with(fromFixedAddress)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"source\":\"rate-limit-test\",\"category\":\"TEST\"}"))
                    .andReturn();
            int status = result.getResponse().getStatus();
            statuses.add(status);
            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                rejected = result;
            }
        }

        assertThat(rejected)
                .as("burst of %d requests must eventually trip the %d/s bucket, got statuses %s",
                        BURST_SIZE, 20, statuses)
                .isNotNull();
        assertThat(rejected.getResponse().getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        assertThat(rejected.getResponse().getContentType())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }
}
