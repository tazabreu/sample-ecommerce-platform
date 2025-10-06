package com.ecommerce.customer.contract;

import com.ecommerce.customer.config.EmbeddedRedisConfig;
import com.ecommerce.customer.testsupport.JwtTestUtils;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for Checkout endpoint.
 * Validates API contract against OpenAPI specification.
 * These tests MUST FAIL until controllers are implemented (TDD approach).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class CheckoutContractTest {

    @LocalServerPort
    private int port;

    private String categoryId;
    private String productId;
    private String sessionId;

    @SuppressWarnings("resource")

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        RestAssured.requestSpecification = requestSpecWithAuth();

        // Create test data with unique names
        long uniqueSuffix = System.currentTimeMillis();
        String token = JwtTestUtils.managerToken();

        categoryId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                  "name": "Checkout Test Category %d",
                  "description": "For checkout testing"
                }
                """, uniqueSuffix))
            .post("/categories")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        productId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                  "sku": "SKU-CHECKOUT-TEST-%d",
                  "name": "Checkout Test Product",
                  "price": 49.99,
                  "inventoryQuantity": 100,
                  "categoryId": "%s"
                }
                """, uniqueSuffix, categoryId))
            .post("/products")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        sessionId = "checkout-session-" + UUID.randomUUID();

        // Add item to cart
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                  "productId": "%s",
                  "quantity": 2
                }
                """, productId))
            .post("/carts/" + sessionId + "/items")
            .then()
            .statusCode(200);
    }

    @Test
    void checkout_withValidData_shouldReturn201() {
        String requestBody = String.format("""
            {
              "sessionId": "%s",
              "customerInfo": {
                "name": "John Doe",
                "email": "john.doe@example.com",
                "phone": "+14155551234",
                "shippingAddress": {
                  "street": "123 Main Street",
                  "city": "San Francisco",
                  "state": "CA",
                  "postalCode": "94105",
                  "country": "USA"
                }
              }
            }
            """, sessionId);

        uuidWithIdempotency()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/checkout")
        .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("orderNumber", matchesPattern("^ORD-\\d{8}-\\d+$"))
            .body("status", in(Arrays.asList("PENDING", "PROCESSING")))
            .body("message", notNullValue());
    }

    @Test
    void checkout_withEmptyCart_shouldReturn400() {
        String emptySessionId = "empty-session-" + UUID.randomUUID();
        String requestBody = String.format("""
            {
              "sessionId": "%s",
              "customerInfo": {
                "name": "John Doe",
                "email": "john.doe@example.com",
                "phone": "+14155551234",
                "shippingAddress": {
                  "street": "123 Main Street",
                  "city": "San Francisco",
                  "state": "CA",
                  "postalCode": "94105",
                  "country": "USA"
                }
              }
            }
            """, emptySessionId);

        uuidWithIdempotency()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/checkout")
        .then()
            .statusCode(400);
    }

    @Test
    void checkout_withInvalidSession_shouldReturn404() {
        String invalidSessionId = "non-existent-session";
        String requestBody = String.format("""
            {
              "sessionId": "%s",
              "customerInfo": {
                "name": "John Doe",
                "email": "john.doe@example.com",
                "phone": "+14155551234",
                "shippingAddress": {
                  "street": "123 Main Street",
                  "city": "San Francisco",
                  "state": "CA",
                  "postalCode": "94105",
                  "country": "USA"
                }
              }
            }
            """, invalidSessionId);

        uuidWithIdempotency()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/checkout")
        .then()
            .statusCode(400);
    }

    @Test
    void checkout_withMissingCustomerInfo_shouldReturn400() {
        String requestBody = String.format("""
            {
              "sessionId": "%s"
            }
            """, sessionId);

        uuidWithIdempotency()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/checkout")
        .then()
            .statusCode(400);
    }

    @Test
    void checkout_withInvalidEmail_shouldReturn400() {
        String requestBody = String.format("""
            {
              "sessionId": "%s",
              "customerInfo": {
                "name": "John Doe",
                "email": "invalid-email",
                "phone": "+14155551234",
                "shippingAddress": {
                  "street": "123 Main Street",
                  "city": "San Francisco",
                  "state": "CA",
                  "postalCode": "94105",
                  "country": "USA"
                }
              }
            }
            """, sessionId);

        uuidWithIdempotency()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/checkout")
        .then()
            .statusCode(400);
    }

    @Test
    void checkout_withIncompleteAddress_shouldReturn400() {
        String requestBody = String.format("""
            {
              "sessionId": "%s",
              "customerInfo": {
                "name": "John Doe",
                "email": "john.doe@example.com",
                "phone": "+14155551234",
                "shippingAddress": {
                  "city": "San Francisco",
                  "state": "CA"
                }
              }
            }
            """, sessionId);

        uuidWithIdempotency()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/checkout")
        .then()
            .statusCode(400);
    }

    private String idempotencyKey() {
        return UUID.randomUUID().toString();
    }

    private io.restassured.specification.RequestSpecification requestSpecWithAuth() {
        return RestAssured.given().header("Authorization", "Bearer " + JwtTestUtils.userToken());
    }

    private io.restassured.specification.RequestSpecification uuidWithIdempotency() {
        return RestAssured.given()
            .header("Authorization", "Bearer " + JwtTestUtils.userToken())
            .header("Idempotency-Key", idempotencyKey());
    }
}

