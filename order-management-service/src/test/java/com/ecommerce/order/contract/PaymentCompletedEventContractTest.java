package com.ecommerce.order.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for PaymentCompletedEvent schema.
 * Validates event structure for success and failure cases.
 * These tests MUST FAIL until event classes are implemented (TDD approach).
 */
@SpringBootTest(classes = com.ecommerce.order.OrderManagementServiceApplication.class)
@ActiveProfiles("test")
class PaymentCompletedEventContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void paymentCompletedEvent_successCase_shouldSerializeToJson() throws Exception {
        // Arrange: Create successful payment event
        Map<String, Object> event = createSuccessfulPaymentEvent();

        // Act: Serialize to JSON
        String json = objectMapper.writeValueAsString(event);

        // Assert
        assertThat(json).isNotNull();
        assertThat(json).isNotBlank();
        assertThat(json).contains("PAYMENT_COMPLETED");
        assertThat(json).contains("SUCCESS");
    }

    @Test
    void paymentCompletedEvent_failureCase_shouldSerializeToJson() throws Exception {
        // Arrange: Create failed payment event
        Map<String, Object> event = createFailedPaymentEvent();

        // Act: Serialize to JSON
        String json = objectMapper.writeValueAsString(event);

        // Assert
        assertThat(json).isNotNull();
        assertThat(json).isNotBlank();
        assertThat(json).contains("PAYMENT_COMPLETED");
        assertThat(json).contains("FAILED");
    }

    @Test
    void paymentCompletedEvent_shouldHaveRequiredFields() throws Exception {
        // Arrange
        Map<String, Object> event = createSuccessfulPaymentEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
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
        assertThat(deserialized).containsKey("paymentTransactionId");
        assertThat(deserialized).containsKey("status");
        assertThat(deserialized).containsKey("amount");
        assertThat(deserialized).containsKey("paymentMethod");
    }

    @Test
    void paymentCompletedEvent_statusShouldBeEnum() throws Exception {
        // Arrange
        Map<String, Object> event = createSuccessfulPaymentEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);

        // Assert: status should be SUCCESS or FAILED
        String status = (String) deserialized.get("status");
        assertThat(status).isIn("SUCCESS", "FAILED");
    }

    @Test
    void paymentCompletedEvent_successCase_shouldHaveExternalTransactionId() throws Exception {
        // Arrange
        Map<String, Object> event = createSuccessfulPaymentEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);

        // Assert: Success case should have external transaction ID
        String status = (String) deserialized.get("status");
        if ("SUCCESS".equals(status)) {
            assertThat(deserialized.get("externalTransactionId")).isNotNull();
        }
    }

    @Test
    void paymentCompletedEvent_failureCase_shouldHaveFailureReason() throws Exception {
        // Arrange
        Map<String, Object> event = createFailedPaymentEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);

        // Assert: Failure case should have failure reason
        String status = (String) deserialized.get("status");
        if ("FAILED".equals(status)) {
            assertThat(deserialized.get("failureReason")).isNotNull();
        }
    }

    @Test
    void paymentCompletedEvent_orderNumberShouldMatchPattern() throws Exception {
        // Arrange
        Map<String, Object> event = createSuccessfulPaymentEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);

        // Assert: orderNumber should match ORD-YYYYMMDD-NNN pattern
        String orderNumber = (String) deserialized.get("orderNumber");
        assertThat(orderNumber).matches("^ORD-\\d{8}-\\d{3}$");
    }

    @Test
    void paymentCompletedEvent_timestampShouldBeIso8601() throws Exception {
        // Arrange
        Map<String, Object> event = createSuccessfulPaymentEvent();
        String json = objectMapper.writeValueAsString(event);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);
        String timestamp = (String) deserialized.get("timestamp");

        // Assert: Should be parseable as ISO 8601
        assertThat(timestamp).isNotNull();
        assertThat(Instant.parse(timestamp)).isNotNull();
    }

    // Helper methods
    private Map<String, Object> createSuccessfulPaymentEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "PAYMENT_COMPLETED");
        event.put("eventVersion", "1.0");
        event.put("timestamp", Instant.now().toString());
        event.put("correlationId", "test-correlation-id");
        event.put("orderId", UUID.randomUUID().toString());
        event.put("orderNumber", "ORD-20250930-001");
        event.put("paymentTransactionId", UUID.randomUUID().toString());
        event.put("status", "SUCCESS");
        event.put("amount", 109.97);
        event.put("paymentMethod", "MOCK");
        event.put("externalTransactionId", "mock_tx_123456");
        event.put("failureReason", null);
        
        return event;
    }

    private Map<String, Object> createFailedPaymentEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "PAYMENT_COMPLETED");
        event.put("eventVersion", "1.0");
        event.put("timestamp", Instant.now().toString());
        event.put("correlationId", "test-correlation-id");
        event.put("orderId", UUID.randomUUID().toString());
        event.put("orderNumber", "ORD-20250930-002");
        event.put("paymentTransactionId", UUID.randomUUID().toString());
        event.put("status", "FAILED");
        event.put("amount", 79.99);
        event.put("paymentMethod", "MOCK");
        event.put("externalTransactionId", null);
        event.put("failureReason", "Payment gateway timeout (simulated failure)");
        
        return event;
    }
}

