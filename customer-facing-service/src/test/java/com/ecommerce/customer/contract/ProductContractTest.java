package com.ecommerce.customer.contract;

import com.ecommerce.customer.config.EmbeddedRedisConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import com.ecommerce.customer.testsupport.JwtTestUtils;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for Product endpoints.
 * Validates API contract against OpenAPI specification.
 * These tests MUST FAIL until controllers are implemented (TDD approach).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class ProductContractTest {

    @LocalServerPort
    private int port;

    private String categoryId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        // Create a test category for products with unique name
        String uniqueName = "Test Category " + System.currentTimeMillis();
        categoryId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                  "name": "%s",
                  "description": "Category for testing products"
                }
                """, uniqueName))
            .post("/categories")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    }

    @Test
    void listProducts_shouldReturnPagedResponse() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("page", 0)
            .queryParam("size", 20)
        .when()
            .get("/products")
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
    void listProducts_withCategoryFilter_shouldFilterResults() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("categoryId", categoryId)
            .queryParam("page", 0)
            .queryParam("size", 20)
        .when()
            .get("/products")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("content", isA(java.util.List.class));
    }

    @Test
    void createProduct_withValidData_shouldReturn201() {
        String requestBody = String.format("""
            {
              "sku": "SKU-TEST-001",
              "name": "Test Product",
              "description": "A test product",
              "price": 29.99,
              "inventoryQuantity": 100,
              "categoryId": "%s"
            }
            """, categoryId);

        String token = JwtTestUtils.managerToken();

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(requestBody)
        .when()
            .post("/products")
        .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("sku", equalTo("SKU-TEST-001"))
            .body("name", equalTo("Test Product"))
            .body("price", equalTo(29.99f))
            .body("inventoryQuantity", equalTo(100))
            .body("categoryId", equalTo(categoryId))
            .body("isActive", equalTo(true))
            .body("createdAt", notNullValue())
            .body("updatedAt", notNullValue());
    }

    @Test
    void createProduct_withMissingRequiredFields_shouldReturn400() {
        String requestBody = """
            {
              "name": "Incomplete Product"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/products")
        .then()
            .statusCode(400);
    }

    @Test
    void createProduct_withInvalidSku_shouldReturn400() {
        String requestBody = String.format("""
            {
              "sku": "invalid sku with spaces",
              "name": "Test Product",
              "price": 29.99,
              "inventoryQuantity": 100,
              "categoryId": "%s"
            }
            """, categoryId);

        String token = JwtTestUtils.managerToken();

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(requestBody)
        .when()
            .post("/products")
        .then()
            .statusCode(400);
    }

    @Test
    void createProduct_withDuplicateSku_shouldReturn409() {
        String requestBody = String.format("""
            {
              "sku": "SKU-DUPLICATE",
              "name": "First Product",
              "price": 19.99,
              "inventoryQuantity": 50,
              "categoryId": "%s"
            }
            """, categoryId);

        // Create first product
        String token = JwtTestUtils.managerToken();

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(requestBody)
            .post("/products")
            .then()
            .statusCode(201);

        // Try to create with same SKU
        String duplicateBody = String.format("""
            {
              "sku": "SKU-DUPLICATE",
              "name": "Second Product",
              "price": 29.99,
              "inventoryQuantity": 25,
              "categoryId": "%s"
            }
            """, categoryId);

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(duplicateBody)
        .when()
            .post("/products")
        .then()
            .statusCode(409);
    }

    @Test
    void getProductById_withValidId_shouldReturn200() {
        // First create a product
        String token = JwtTestUtils.managerToken();

        String productId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(String.format("""
                {
                  "sku": "SKU-GET-TEST",
                  "name": "Get Test Product",
                  "price": 39.99,
                  "inventoryQuantity": 75,
                  "categoryId": "%s"
                }
                """, categoryId))
            .post("/products")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Then get it by ID
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/products/" + productId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(productId))
            .body("sku", equalTo("SKU-GET-TEST"))
            .body("name", equalTo("Get Test Product"));
    }

    @Test
    void updateProduct_withValidData_shouldReturn200() {
        // First create a product
        String token = JwtTestUtils.managerToken();

        String productId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(String.format("""
                {
                  "sku": "SKU-UPDATE-TEST",
                  "name": "Original Name",
                  "price": 49.99,
                  "inventoryQuantity": 50,
                  "categoryId": "%s"
                }
                """, categoryId))
            .post("/products")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Then update it
        String updateBody = """
            {
              "name": "Updated Name",
              "price": 59.99,
              "inventoryQuantity": 75
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(updateBody)
        .when()
            .put("/products/" + productId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(productId))
            .body("name", equalTo("Updated Name"))
            .body("price", equalTo(59.99f))
            .body("inventoryQuantity", equalTo(75));
    }

    @Test
    void deleteProduct_withValidId_shouldReturn204() {
        // First create a product
        String token = JwtTestUtils.managerToken();

        String productId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(String.format("""
                {
                  "sku": "SKU-DELETE-TEST",
                  "name": "Delete Test Product",
                  "price": 19.99,
                  "inventoryQuantity": 10,
                  "categoryId": "%s"
                }
                """, categoryId))
            .post("/products")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Then delete it (soft delete via isActive=false)
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
        .when()
            .delete("/products/" + productId)
        .then()
            .statusCode(204);

        // Verify it's marked as inactive
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/products/" + productId)
        .then()
            .statusCode(200)
            .body("isActive", equalTo(false));
    }
}

