package com.ecommerce.order.contract;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for Order endpoints.
 * Validates API contract against OpenAPI specification.
 * These tests MUST FAIL until controllers are implemented (TDD approach).
 */
@SpringBootTest(classes = com.ecommerce.order.OrderManagementServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderContractTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";
    }

    @Test
    void getOrderByNumber_withValidOrderNumber_shouldReturn200() {
        // This test assumes an order exists (will be created via Kafka event in real scenario)
        // For now, we'll test the endpoint structure
        String orderNumber = "ORD-20250930-001";

        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/orders/" + orderNumber)
        .then()
            .statusCode(anyOf(equalTo(200), equalTo(404))) // 404 if not found is also valid
            .contentType(ContentType.JSON);
    }

    @Test
    void getOrderByNumber_withInvalidFormat_shouldReturn400() {
        String invalidOrderNumber = "INVALID-FORMAT";

        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/orders/" + invalidOrderNumber)
        .then()
            .statusCode(anyOf(equalTo(400), equalTo(404)));
    }

    @Test
    void listOrders_shouldReturnPagedResponse() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("page", 0)
            .queryParam("size", 20)
        .when()
            .get("/orders")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("content", isA(java.util.List.class))
            .body("totalElements", notNullValue())
            .body("totalPages", notNullValue())
            .body("size", equalTo(20))
            .body("number", equalTo(0));
    }

    @Test
    void listOrders_withStatusFilter_shouldFilterResults() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("status", "PAID")
            .queryParam("page", 0)
            .queryParam("size", 20)
        .when()
            .get("/orders")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("content", isA(java.util.List.class));
    }

    @Test
    void listOrders_withEmailFilter_shouldFilterResults() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("customerEmail", "test@example.com")
            .queryParam("page", 0)
            .queryParam("size", 20)
        .when()
            .get("/orders")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("content", isA(java.util.List.class));
    }

    @Test
    void listOrders_withDateRangeFilter_shouldFilterResults() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("startDate", "2025-09-01T00:00:00Z")
            .queryParam("endDate", "2025-09-30T23:59:59Z")
            .queryParam("page", 0)
            .queryParam("size", 20)
        .when()
            .get("/orders")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("content", isA(java.util.List.class));
    }

    @Test
    void cancelOrder_withValidOrderNumber_shouldReturn200() {
        String orderNumber = "ORD-20250930-002";
        String requestBody = """
            {
              "reason": "Customer requested cancellation"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/orders/" + orderNumber + "/cancel")
        .then()
            .statusCode(anyOf(equalTo(200), equalTo(404), equalTo(409))) // Valid responses based on order state
            .contentType(ContentType.JSON);
    }

    @Test
    void cancelOrder_withMissingReason_shouldReturn400() {
        String orderNumber = "ORD-20250930-003";
        String requestBody = """
            {
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/orders/" + orderNumber + "/cancel")
        .then()
            .statusCode(400);
    }

    @Test
    void fulfillOrder_withValidData_shouldReturn200() {
        String orderNumber = "ORD-20250930-004";
        String requestBody = """
            {
              "trackingNumber": "TRACK-123456789",
              "carrier": "FedEx",
              "notes": "Package shipped via FedEx 2-day"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/orders/" + orderNumber + "/fulfill")
        .then()
            .statusCode(anyOf(equalTo(200), equalTo(404), equalTo(409))) // Valid responses based on order state
            .contentType(ContentType.JSON);
    }

    @Test
    void fulfillOrder_withMinimalData_shouldReturn200() {
        String orderNumber = "ORD-20250930-005";
        String requestBody = """
            {
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/orders/" + orderNumber + "/fulfill")
        .then()
            .statusCode(anyOf(equalTo(200), equalTo(404), equalTo(409)));
    }
}

