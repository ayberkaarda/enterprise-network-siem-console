package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full application context against the isolated in-memory database
 * profile, so the build needs no external service.
 */
@SpringBootTest
@ActiveProfiles("h2")
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
