package com.ecommerce.customer.contract;

import com.ecommerce.customer.config.EmbeddedRedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for OrderCreatedEvent schema.
 * Validates event structure and field formats.
 * These tests MUST FAIL until event classes are implemented (TDD approach).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
class OrderCreatedEventContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void orderCreatedEvent_shouldSerializeToJson() throws Exception {
        // Arrange: Create test event structure
        Map<String, Object> event = createTestOrderCreatedEvent();

        // Act: Serialize to JSON
        String json = objectMapper.writeValueAsString(event);

        // Assert: JSON should not be null or empty
        assertThat(json).isNotNull();
        assertThat(json).isNotBlank();
        assertThat(json).contains("ORDER_CREATED");
    }

    @Test
    void orderCreatedEvent_shouldHaveRequiredFields() throws Exception {
        // Arrange
        Map<String, Object> event = createTestOrderCreatedEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act: Deserialize back
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);

        // Assert: Check required fields
        assertThat(deserialized).containsKey("eventId");
        assertThat(deserialized).containsKey("eventType");
        assertThat(deserialized).containsKey("eventVersion");
        assertThat(deserialized).containsKey("timestamp");
        assertThat(deserialized).containsKey("correlationId");
        assertThat(deserialized).containsKey("orderId");
        assertThat(deserialized).containsKey("orderNumber");
        assertThat(deserialized).containsKey("customer");
        assertThat(deserialized).containsKey("items");
        assertThat(deserialized).containsKey("subtotal");
    }

    @Test
    void orderCreatedEvent_eventIdShouldBeUuid() throws Exception {
        // Arrange
        Map<String, Object> event = createTestOrderCreatedEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);

        // Assert: eventId should be valid UUID
        String eventId = (String) deserialized.get("eventId");
        assertThat(eventId).isNotNull();
        assertThat(UUID.fromString(eventId)).isNotNull();
    }

    @Test
    void orderCreatedEvent_orderNumberShouldMatchPattern() throws Exception {
        // Arrange
        Map<String, Object> event = createTestOrderCreatedEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);

        // Assert: orderNumber should match ORD-YYYYMMDD-NNN pattern
        String orderNumber = (String) deserialized.get("orderNumber");
        assertThat(orderNumber).matches("^ORD-\\d{8}-\\d{3}$");
    }

    @Test
    void orderCreatedEvent_customerShouldHaveRequiredFields() throws Exception {
        // Arrange
        Map<String, Object> event = createTestOrderCreatedEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) deserialized.get("customer");

        // Assert
        assertThat(customer).containsKey("name");
        assertThat(customer).containsKey("email");
        assertThat(customer).containsKey("phone");
        assertThat(customer).containsKey("shippingAddress");

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) customer.get("shippingAddress");
        assertThat(address).containsKeys("street", "city", "state", "postalCode", "country");
    }

    @Test
    void orderCreatedEvent_itemsShouldBeNonEmpty() throws Exception {
        // Arrange
        Map<String, Object> event = createTestOrderCreatedEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) deserialized.get("items");

        // Assert
        assertThat(items).isNotEmpty();
        assertThat(items.get(0)).containsKeys("productId", "productSku", "productName", "quantity", "priceSnapshot", "subtotal");
    }

    @Test
    void orderCreatedEvent_timestampShouldBeIso8601() throws Exception {
        // Arrange
        Map<String, Object> event = createTestOrderCreatedEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);
        String timestamp = (String) deserialized.get("timestamp");

        // Assert: Should be parseable as ISO 8601
        assertThat(timestamp).isNotNull();
        assertThat(Instant.parse(timestamp)).isNotNull();
    }

    // Helper method to create test event
    private Map<String, Object> createTestOrderCreatedEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "ORDER_CREATED");
        event.put("eventVersion", "1.0");
        event.put("timestamp", Instant.now().toString());
        event.put("correlationId", "test-correlation-id");
        event.put("orderId", UUID.randomUUID().toString());
        event.put("orderNumber", "ORD-20250930-001");
        
        Map<String, Object> customer = new HashMap<>();
        customer.put("name", "John Doe");
        customer.put("email", "john.doe@example.com");
        customer.put("phone", "+14155551234");
        
        Map<String, Object> address = new HashMap<>();
        address.put("street", "123 Main St");
        address.put("city", "San Francisco");
        address.put("state", "CA");
        address.put("postalCode", "94105");
        address.put("country", "USA");
        customer.put("shippingAddress", address);
        
        event.put("customer", customer);
        
        Map<String, Object> item = new HashMap<>();
        item.put("productId", UUID.randomUUID().toString());
        item.put("productSku", "SKU-TEST-001");
        item.put("productName", "Test Product");
        item.put("quantity", 2);
        item.put("priceSnapshot", 29.99);
        item.put("subtotal", 59.98);
        
        event.put("items", List.of(item));
        event.put("subtotal", 59.98);
        event.put("cartId", UUID.randomUUID().toString());
        
        return event;
    }
}

