package com.example.ticketero.integration;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all integration tests.
 * Uses TestContainers if Docker is available, otherwise falls back to H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private static boolean dockerAvailable = false;

    // ============================================================
    // TESTCONTAINERS (Optional - only if Docker is available)
    // ============================================================

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ticketero_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        try {
            if (postgres.isRunning()) {
                // PostgreSQL via TestContainers
                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", postgres::getUsername);
                registry.add("spring.datasource.password", postgres::getPassword);
                registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
                dockerAvailable = true;
            }
        } catch (Exception e) {
            // Docker not available, use H2 (configured in application-test.yml)
            dockerAvailable = false;
        }

        // Telegram Mock (WireMock)
        registry.add("telegram.api-url", () -> "http://localhost:8089/bot");
        registry.add("telegram.bot-token", () -> "test-token");
        registry.add("telegram.chat-id", () -> "123456789");
    }

    // ============================================================
    // SETUP
    // ============================================================

    @BeforeAll
    static void setupContainers() {
        try {
            if (postgres != null && !postgres.isRunning()) {
                postgres.start();
                dockerAvailable = true;
            }
        } catch (Exception e) {
            System.out.println("Docker not available, using H2 database for tests");
            dockerAvailable = false;
        }
    }

    @BeforeEach
    void setupRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void cleanDatabase() {
        // Limpiar en orden correcto (FK constraints)
        try {
            jdbcTemplate.execute("DELETE FROM ticket_event");
        } catch (Exception e) {
            // Table might not exist yet
        }
        try {
            jdbcTemplate.execute("DELETE FROM recovery_event");
        } catch (Exception e) {
            // Table might not exist yet
        }
        try {
            jdbcTemplate.execute("DELETE FROM outbox_message");
        } catch (Exception e) {
            // Table might not exist yet
        }
        try {
            jdbcTemplate.execute("DELETE FROM ticket");
        } catch (Exception e) {
            // Table might not exist yet
        }
        try {
            jdbcTemplate.execute("UPDATE advisor SET status = 'AVAILABLE', total_tickets_served = 0");
        } catch (Exception e) {
            // Table might not exist yet
        }
    }

    // ============================================================
    // UTILITIES
    // ============================================================

    protected String createTicketRequest(String nationalId, String telefono, 
                                          String branchOffice, String queueType) {
        return String.format("""
            {
                "nationalId": "%s",
                "telefono": "%s",
                "branchOffice": "%s",
                "queueType": "%s"
            }
            """, nationalId, telefono, branchOffice, queueType);
    }

    protected String createTicketRequest(String nationalId, String queueType) {
        return createTicketRequest(nationalId, "+56912345678", "Sucursal Centro", queueType);
    }

    protected int countTicketsInStatus(String status) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket WHERE status = ?",
                Integer.class, status);
        } catch (Exception e) {
            return 0;
        }
    }

    protected int countOutboxMessages(String status) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_message WHERE status = ?",
                Integer.class, status);
        } catch (Exception e) {
            return 0;
        }
    }

    protected int countAdvisorsInStatus(String status) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM advisor WHERE status = ?",
                Integer.class, status);
        } catch (Exception e) {
            return 0;
        }
    }

    protected boolean isDockerAvailable() {
        return dockerAvailable;
    }

    protected boolean isPostgreSQLRunning() {
        return dockerAvailable && postgres != null && postgres.isRunning();
    }

    protected void waitForTicketProcessing(int expectedCompleted, int timeoutSeconds) {
        org.awaitility.Awaitility.await()
            .atMost(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .pollInterval(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .until(() -> countTicketsInStatus("COMPLETED") >= expectedCompleted);
    }

    protected void setAdvisorStatus(Long advisorId, String status) {
        try {
            jdbcTemplate.update(
                "UPDATE advisor SET status = ? WHERE id = ?",
                status, advisorId);
        } catch (Exception e) {
            // Table might not exist yet
        }
    }
}