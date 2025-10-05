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

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for Cart endpoints.
 * Validates API contract against OpenAPI specification.
 * These tests MUST FAIL until controllers are implemented (TDD approach).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class CartContractTest {

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

        RestAssured.requestSpecification = given()
            .header("Authorization", "Bearer " + JwtTestUtils.managerToken());

        // Create test data with unique names
        long uniqueSuffix = System.currentTimeMillis();

        categoryId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                  "name": "Test Category %d",
                  "description": "For cart testing"
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
                  "sku": "SKU-CART-TEST-%d",
                  "name": "Cart Test Product",
                  "price": 29.99,
                  "inventoryQuantity": 100,
                  "categoryId": "%s"
                }
                """, uniqueSuffix, categoryId))
            .post("/products")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        sessionId = "test-session-" + UUID.randomUUID();
    }

    @Test
    void getCart_withNewSession_shouldReturnEmptyCart() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/carts/" + sessionId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("sessionId", equalTo(sessionId))
            .body("items", empty())
            .body("subtotal", anyOf(equalTo(0), equalTo(0.0f)))
            .body("expiresAt", notNullValue());
    }

    @Test
    void addItemToCart_withValidData_shouldReturn200() {
        String requestBody = String.format("""
            {
              "productId": "%s",
              "quantity": 2
            }
            """, productId);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/carts/" + sessionId + "/items")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("sessionId", equalTo(sessionId))
            .body("items", hasSize(1))
            .body("items[0].productId", equalTo(productId))
            .body("items[0].quantity", equalTo(2))
            .body("items[0].priceSnapshot", equalTo(29.99f))
            .body("items[0].subtotal", equalTo(59.98f))
            .body("subtotal", equalTo(59.98f));
    }

    @Test
    void addItemToCart_withInvalidProduct_shouldReturn404() {
        String invalidProductId = "00000000-0000-0000-0000-000000000000";
        String requestBody = String.format("""
            {
              "productId": "%s",
              "quantity": 1
            }
            """, invalidProductId);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/carts/" + sessionId + "/items")
        .then()
            .statusCode(404);
    }

    @Test
    void addItemToCart_withInvalidQuantity_shouldReturn400() {
        String requestBody = String.format("""
            {
              "productId": "%s",
              "quantity": 0
            }
            """, productId);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/carts/" + sessionId + "/items")
        .then()
            .statusCode(400);
    }

    @Test
    void addItemToCart_exceedingInventory_shouldReturn409() {
        String requestBody = String.format("""
            {
              "productId": "%s",
              "quantity": 1000
            }
            """, productId);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/carts/" + sessionId + "/items")
        .then()
            .statusCode(409);
    }

    @Test
    void updateCartItem_withValidQuantity_shouldReturn200() {
        // First add item to cart
        String cartItemId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                  "productId": "%s",
                  "quantity": 2
                }
                """, productId))
            .post("/carts/" + sessionId + "/items")
            .then()
            .statusCode(200)
            .extract()
            .path("items[0].id");

        // Then update quantity
        String updateBody = """
            {
              "quantity": 5
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
        .when()
            .put("/carts/" + sessionId + "/items/" + cartItemId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("items[0].quantity", equalTo(5))
            .body("items[0].subtotal", equalTo(149.95f))
            .body("subtotal", equalTo(149.95f));
    }

    @Test
    void removeCartItem_withValidId_shouldReturn200() {
        // First add item to cart
        String cartItemId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                  "productId": "%s",
                  "quantity": 2
                }
                """, productId))
            .post("/carts/" + sessionId + "/items")
            .then()
            .statusCode(200)
            .extract()
            .path("items[0].id");

        // Then remove it
        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/carts/" + sessionId + "/items/" + cartItemId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("items", empty())
            .body("subtotal", equalTo(0));
    }

    @Test
    void clearCart_shouldReturn204() {
        // First add item to cart
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

        // Then clear cart
        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/carts/" + sessionId)
        .then()
            .statusCode(204);

        // Verify cart is empty
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/carts/" + sessionId)
        .then()
            .statusCode(200)
            .body("items", empty())
            .body("subtotal", anyOf(equalTo(0), equalTo(0.0f)));
    }
}

