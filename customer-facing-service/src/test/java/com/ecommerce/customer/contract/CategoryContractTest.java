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
 * Contract tests for Category endpoints.
 * Validates API contract against OpenAPI specification.
 * These tests MUST FAIL until controllers are implemented (TDD approach).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class CategoryContractTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";
    }

    @Test
    void listCategories_shouldReturnEmptyArray() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/categories")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", isA(java.util.List.class));
    }

    @Test
    void createCategory_withValidData_shouldReturn201() {
        String requestBody = """
            {
              "name": "Electronics",
              "description": "Electronic devices and accessories"
            }
            """;

        String token = JwtTestUtils.managerToken();

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(requestBody)
        .when()
            .post("/categories")
        .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("name", equalTo("Electronics"))
            .body("description", equalTo("Electronic devices and accessories"))
            .body("createdAt", notNullValue())
            .body("updatedAt", notNullValue());
    }

    @Test
    void createCategory_withMissingName_shouldReturn400() {
        String requestBody = """
            {
              "description": "Missing name"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/categories")
        .then()
            .statusCode(400);
    }

    @Test
    void getCategoryById_withValidId_shouldReturn200() {
        // First create a category
        String token = JwtTestUtils.managerToken();

        String categoryId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body("""
                {
                  "name": "Books",
                  "description": "Books and literature"
                }
                """)
            .post("/categories")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Then get it by ID
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/categories/" + categoryId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(categoryId))
            .body("name", equalTo("Books"))
            .body("description", equalTo("Books and literature"));
    }

    @Test
    void getCategoryById_withInvalidId_shouldReturn404() {
        String invalidId = "00000000-0000-0000-0000-000000000000";

        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/categories/" + invalidId)
        .then()
            .statusCode(404);
    }

    @Test
    void updateCategory_withValidData_shouldReturn200() {
        // First create a category
        String token = JwtTestUtils.managerToken();

        String categoryId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body("""
                {
                  "name": "Clothing",
                  "description": "Original description"
                }
                """)
            .post("/categories")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Then update it
        String updateBody = """
            {
              "name": "Apparel",
              "description": "Updated description"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(updateBody)
        .when()
            .put("/categories/" + categoryId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(categoryId))
            .body("name", equalTo("Apparel"))
            .body("description", equalTo("Updated description"));
    }

    @Test
    void deleteCategory_withValidId_shouldReturn204() {
        // First create a category
        String token = JwtTestUtils.managerToken();

        String categoryId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body("""
                {
                  "name": "Temporary Category",
                  "description": "Will be deleted"
                }
                """)
            .post("/categories")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Then delete it
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
        .when()
            .delete("/categories/" + categoryId)
        .then()
            .statusCode(204);

        // Verify it's deleted
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/categories/" + categoryId)
        .then()
            .statusCode(404);
    }

    @Test
    void deleteCategory_withInvalidId_shouldReturn404() {
        String invalidId = "00000000-0000-0000-0000-000000000000";

        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/categories/" + invalidId)
        .then()
            .statusCode(404);
    }
}

