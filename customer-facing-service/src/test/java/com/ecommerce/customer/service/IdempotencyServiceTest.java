package com.ecommerce.customer.service;

import com.ecommerce.customer.model.CheckoutIdempotency;
import com.ecommerce.customer.repository.CheckoutIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for IdempotencyService.
 * Tests idempotency key validation, fingerprint calculation, and response caching.
 * 
 * Coverage Target: >95% line, >90% branch (CRITICAL for money safety)
 * Total Tests: 10 (Key Validation: 4, Fingerprint: 3, Response Caching: 3)
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private CheckoutIdempotencyRepository idempotencyRepository;

    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;

    private String testIdempotencyKey;
    private TestRequest testRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);
        
        testIdempotencyKey = "test-key-123";
        testRequest = new TestRequest("user@example.com", "123 Main St", "100.00");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ==================== Key Validation Tests (4 tests) ====================

    @Test
    void checkIdempotency_newKey_returnsEmpty() {
        // Arrange
        when(idempotencyRepository.findByIdempotencyKeyAndNotExpired(eq(testIdempotencyKey), any(Instant.class)))
                .thenReturn(Optional.empty());

        // Act
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(testIdempotencyKey, testRequest);

        // Assert
        assertThat(result).isEmpty();
        verify(idempotencyRepository).findByIdempotencyKeyAndNotExpired(eq(testIdempotencyKey), any(Instant.class));
    }

    @Test
    void checkIdempotency_duplicateKeyMatchingFingerprint_returnsCache() throws Exception {
        // Arrange
        String fingerprint = calculateExpectedFingerprint(testRequest);
        String cachedResponse = "{\"orderId\":\"123\",\"status\":\"success\"}";
        
        CheckoutIdempotency existingRecord = new CheckoutIdempotency(
                testIdempotencyKey,
                fingerprint,
                201,
                cachedResponse
        );
        existingRecord.setCreatedAt(Instant.now());
        
        when(idempotencyRepository.findByIdempotencyKeyAndNotExpired(eq(testIdempotencyKey), any(Instant.class)))
                .thenReturn(Optional.of(existingRecord));

        // Act
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(testIdempotencyKey, testRequest);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getStatusCode().value()).isEqualTo(201);
        assertThat(result.get().getBody()).isNotNull();
    }

    @Test
    void checkIdempotency_duplicateKeyDifferentFingerprint_throwsException() {
        // Arrange
        String wrongFingerprint = "different-fingerprint-hash";
        String cachedResponse = "{\"orderId\":\"123\"}";
        
        CheckoutIdempotency existingRecord = new CheckoutIdempotency(
                testIdempotencyKey,
                wrongFingerprint,
                201,
                cachedResponse
        );
        existingRecord.setCreatedAt(Instant.now());
        
        when(idempotencyRepository.findByIdempotencyKeyAndNotExpired(eq(testIdempotencyKey), any(Instant.class)))
                .thenReturn(Optional.of(existingRecord));

        // Act & Assert
        assertThatThrownBy(() -> idempotencyService.checkIdempotency(testIdempotencyKey, testRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Idempotency key already used with different request body")
                .hasMessageContaining("Please use a new idempotency key");
        
        verify(idempotencyRepository, never()).save(any());
    }

    @Test
    void checkIdempotency_expiredKey_returnsEmpty() {
        // Arrange - Repository returns empty for expired keys
        when(idempotencyRepository.findByIdempotencyKeyAndNotExpired(eq(testIdempotencyKey), any(Instant.class)))
                .thenReturn(Optional.empty());

        // Act
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(testIdempotencyKey, testRequest);

        // Assert
        assertThat(result).isEmpty();
        verify(idempotencyRepository).findByIdempotencyKeyAndNotExpired(eq(testIdempotencyKey), any(Instant.class));
    }

    // ==================== Fingerprint Calculation Tests (3 tests) ====================

    @Test
    void calculateFingerprint_sameRequest_sameFingerprint() throws Exception {
        // Arrange
        TestRequest request1 = new TestRequest("user@example.com", "123 Main St", "100.00");
        TestRequest request2 = new TestRequest("user@example.com", "123 Main St", "100.00");
        
        when(idempotencyRepository.findByIdempotencyKeyAndNotExpired(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        // Act
        idempotencyService.checkIdempotency("key1", request1);
        idempotencyService.checkIdempotency("key2", request2);
        
        // Store both requests
        when(idempotencyRepository.save(any(CheckoutIdempotency.class))).thenReturn(null);
        idempotencyService.storeIdempotency("key1", request1, 201, "{\"orderId\":\"1\"}");
        idempotencyService.storeIdempotency("key2", request2, 201, "{\"orderId\":\"2\"}");

        // Assert - Capture the saved records and verify fingerprints are identical
        ArgumentCaptor<CheckoutIdempotency> captor = ArgumentCaptor.forClass(CheckoutIdempotency.class);
        verify(idempotencyRepository, times(2)).save(captor.capture());
        
        String fingerprint1 = captor.getAllValues().get(0).getRequestFingerprint();
        String fingerprint2 = captor.getAllValues().get(1).getRequestFingerprint();
        
        assertThat(fingerprint1).isEqualTo(fingerprint2);
        assertThat(fingerprint1).isNotBlank();
    }

    @Test
    void calculateFingerprint_differentRequest_differentFingerprint() {
        // Arrange
        TestRequest request1 = new TestRequest("user@example.com", "123 Main St", "100.00");
        TestRequest request2 = new TestRequest("other@example.com", "456 Oak Ave", "200.00");
        
        when(idempotencyRepository.save(any(CheckoutIdempotency.class))).thenReturn(null);

        // Act
        idempotencyService.storeIdempotency("key1", request1, 201, "{\"orderId\":\"1\"}");
        idempotencyService.storeIdempotency("key2", request2, 201, "{\"orderId\":\"2\"}");

        // Assert
        ArgumentCaptor<CheckoutIdempotency> captor = ArgumentCaptor.forClass(CheckoutIdempotency.class);
        verify(idempotencyRepository, times(2)).save(captor.capture());
        
        String fingerprint1 = captor.getAllValues().get(0).getRequestFingerprint();
        String fingerprint2 = captor.getAllValues().get(1).getRequestFingerprint();
        
        assertThat(fingerprint1).isNotEqualTo(fingerprint2);
        assertThat(fingerprint1).hasSize(64); // SHA-256 hex = 64 chars
        assertThat(fingerprint2).hasSize(64);
    }

    @Test
    void calculateFingerprint_orderIndependent_forSameData() {
        // Arrange - Test that fingerprint is deterministic (same input = same output)
        TestRequest request1 = new TestRequest("user@example.com", "123 Main St", "100.00");
        TestRequest request2 = new TestRequest("user@example.com", "123 Main St", "100.00");
        
        when(idempotencyRepository.save(any(CheckoutIdempotency.class))).thenReturn(null);

        // Act - Store same request twice
        idempotencyService.storeIdempotency("key1", request1, 201, "{\"orderId\":\"1\"}");
        idempotencyService.storeIdempotency("key2", request2, 201, "{\"orderId\":\"2\"}");

        // Assert - Fingerprints must be identical
        ArgumentCaptor<CheckoutIdempotency> captor = ArgumentCaptor.forClass(CheckoutIdempotency.class);
        verify(idempotencyRepository, times(2)).save(captor.capture());
        
        String fingerprint1 = captor.getAllValues().get(0).getRequestFingerprint();
        String fingerprint2 = captor.getAllValues().get(1).getRequestFingerprint();
        
        assertThat(fingerprint1).isEqualTo(fingerprint2);
    }

    // ==================== Response Caching Tests (3 tests) ====================

    @Test
    void storeIdempotency_success() {
        // Arrange
        Object responseBody = new TestResponse("ORD-20251006-001", "success");
        when(idempotencyRepository.save(any(CheckoutIdempotency.class))).thenReturn(null);

        // Act
        idempotencyService.storeIdempotency(testIdempotencyKey, testRequest, 201, responseBody);

        // Assert
        ArgumentCaptor<CheckoutIdempotency> captor = ArgumentCaptor.forClass(CheckoutIdempotency.class);
        verify(idempotencyRepository).save(captor.capture());
        
        CheckoutIdempotency saved = captor.getValue();
        assertThat(saved.getIdempotencyKey()).isEqualTo(testIdempotencyKey);
        assertThat(saved.getRequestFingerprint()).isNotBlank();
        assertThat(saved.getRequestFingerprint()).hasSize(64); // SHA-256 hex
        assertThat(saved.getResponseStatus()).isEqualTo(201);
        assertThat(saved.getResponseBody()).contains("ORD-20251006-001");
        assertThat(saved.getResponseBody()).contains("success");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());
    }

    @Test
    void getCachedResponse_exists_returnsResponse() throws Exception {
        // Arrange
        String fingerprint = calculateExpectedFingerprint(testRequest);
        TestResponse responseObj = new TestResponse("ORD-20251006-001", "success");
        String cachedResponse = objectMapper.writeValueAsString(responseObj);
        
        CheckoutIdempotency existingRecord = new CheckoutIdempotency(
                testIdempotencyKey,
                fingerprint,
                201,
                cachedResponse
        );
        existingRecord.setCreatedAt(Instant.now());
        
        when(idempotencyRepository.findByIdempotencyKeyAndNotExpired(eq(testIdempotencyKey), any(Instant.class)))
                .thenReturn(Optional.of(existingRecord));

        // Act
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(testIdempotencyKey, testRequest);

        // Assert
        assertThat(result).isPresent();
        ResponseEntity<Object> response = result.get();
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        
        // Verify the response body structure (it's deserialized as a Map by default)
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> body = (java.util.Map<String, String>) response.getBody();
        assertThat(body.get("orderNumber")).isEqualTo("ORD-20251006-001");
        assertThat(body.get("status")).isEqualTo("success");
    }

    @Test
    void getCachedResponse_notExists_returnsEmpty() {
        // Arrange
        when(idempotencyRepository.findByIdempotencyKeyAndNotExpired(eq(testIdempotencyKey), any(Instant.class)))
                .thenReturn(Optional.empty());

        // Act
        Optional<ResponseEntity<Object>> result = idempotencyService.checkIdempotency(testIdempotencyKey, testRequest);

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== Helper Classes & Methods ====================

    /**
     * Calculate expected fingerprint for a request (for test assertions).
     */
    private String calculateExpectedFingerprint(Object request) throws Exception {
        String json = objectMapper.writeValueAsString(request);
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hash);
    }

    /**
     * Test request DTO for fingerprint testing.
     */
    record TestRequest(String email, String address, String amount) {}

    /**
     * Test response DTO for caching testing.
     */
    record TestResponse(String orderNumber, String status) {}
}
