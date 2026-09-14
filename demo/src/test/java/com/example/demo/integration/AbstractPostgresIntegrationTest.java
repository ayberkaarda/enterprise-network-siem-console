package com.example.demo.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared fixture for the tests that must run against a real PostgreSQL
 * instead of the isolated {@code h2} profile the rest of the suite uses: the
 * {@code postgres} profile turns on Flyway and {@code ddl-auto=validate},
 * neither of which the H2 fallback ever exercises (see
 * {@code application-h2.properties}).
 *
 * <p><strong>This is a deliberate, narrow exception</strong> to the rest of
 * the suite needing zero external services: these tests need Docker. Naming
 * this class {@code Abstract...Test} is what keeps it out of the run on its
 * own — Surefire's default exclude glob ({@code **&#47;Abstract*Test.java}) skips
 * abstract base classes named that way, so only the concrete subclasses below
 * are ever picked up as test classes.
 *
 * <p>{@code disabledWithoutDocker = true} makes every {@code @Test} in a
 * subclass report as <em>skipped</em>, not failed, when Docker is not
 * reachable — so a Docker-less {@code ./mvnw verify} still exits successfully;
 * the rest of the suite is completely unaffected either way, since none of it
 * shares a container, a context, or a database with these tests.
 *
 * <p><strong>Singleton container, started once, never stopped explicitly —
 * measured, not a style choice.</strong> The first version of this fixture
 * annotated {@code POSTGRES} with {@code @Container}, which ties
 * start()/stop() to each subclass's own JUnit lifecycle even though the field
 * is inherited. With two subclasses that produced this, verbatim: the
 * migration test's class started a container on one mapped port; when the
 * round-trip test's class began, {@code @Container} started a *second*
 * container on a different port (Testcontainers' {@code start()} restarts
 * rather than no-ops on a stopped instance) — but Spring's test context cache
 * had already cached an {@code ApplicationContext} keyed on this fixture's
 * configuration, built against the *first* container's JDBC URL, and reused
 * it for the second subclass. The result was
 * {@code Connection to localhost:<old port> refused} and
 * {@code CannotCreateTransactionException} on every test in the second class.
 * Starting the container exactly once in a static initializer and never
 * calling {@code stop()} keeps the URL stable for every subclass that shares
 * this fixture; Testcontainers' Ryuk resource reaper removes the container
 * when the whole JVM exits, which is what makes skipping an explicit stop
 * safe here.
 *
 * <p>The container is not shared with the {@code docker-compose.yml} database:
 * it runs on whatever free port Docker assigns it and never touches
 * {@code siem-postgres}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres")
abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("siem_console_it")
            .withUsername("siem_it")
            .withPassword("siem_it_password");

    static {
        // Deliberately not @Container-managed; see the class Javadoc above.
        // disabledWithoutDocker on the class annotation is what keeps this
        // from ever running when Docker is unreachable — JUnit's execution
        // condition disables the whole class before any subclass is
        // instantiated, which is what stops this static initializer (and the
        // start() call inside it) from running at all in that case.
        POSTGRES.start();
    }

    /**
     * Points the {@code postgres} profile's placeholder-driven properties
     * (datasource credentials, JWT secret) at the ephemeral container instead
     * of the environment variables that profile normally requires. A
     * dynamic property source has the highest precedence, so it shadows the
     * {@code ${...}} placeholders in {@code application-postgres.properties}
     * outright rather than needing those environment variables to be set.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("siem.jwt.secret", () -> "testcontainers-integration-fixed-signing-secret-at-least-32-bytes");
    }
}
