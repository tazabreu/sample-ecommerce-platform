package com.ecommerce.order.contract;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for Health endpoints.
 * Validates API contract against OpenAPI specification.
 * These tests MUST FAIL until controllers are implemented (TDD approach).
 */
@SpringBootTest(classes = com.ecommerce.order.OrderManagementServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthContractTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void healthCheck_shouldReturn200WithStatus() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/actuator/health")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", in(Arrays.asList("UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN")))
            .body("components", notNullValue());
    }

    @Test
    void healthCheck_shouldIncludeDatabaseComponent() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/actuator/health")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("components.db", notNullValue())
            .body("components.db.status", notNullValue());
    }

    @Test
    void healthCheck_shouldNotIncludeKafkaComponentInTests() {
        // Kafka health indicator is disabled in test profile since Kafka isn't running
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/actuator/health")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("components.kafkaHealthIndicator", nullValue());
    }

    @Test
    void livenessProbe_shouldReturn200() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/actuator/health/liveness")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", in(Arrays.asList("UP", "DOWN")));
    }

    @Test
    void readinessProbe_shouldReturn200WhenReady() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/actuator/health/readiness")
        .then()
            .statusCode(anyOf(equalTo(200), equalTo(503))) // 503 if not ready
            .contentType(ContentType.JSON)
            .body("status", in(Arrays.asList("UP", "DOWN", "OUT_OF_SERVICE")));
    }

    @Test
    void healthCheck_responseStructure_shouldMatchSchema() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/actuator/health")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", notNullValue())
            .body("status", isA(String.class));
    }
}

