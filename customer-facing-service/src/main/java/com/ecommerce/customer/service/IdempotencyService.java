package com.ecommerce.customer.service;

import com.ecommerce.customer.model.CheckoutIdempotency;
import com.ecommerce.customer.repository.CheckoutIdempotencyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Service for managing checkout idempotency.
 * Implements idempotency key pattern to prevent duplicate checkouts.
 *
 * <p>Idempotency guarantees:</p>
 * <ul>
 *   <li>Same key + same request → return cached response (200 OK)</li>
 *   <li>Same key + different request → return 422 Unprocessable Entity</li>
 *   <li>New key → process normally and cache response</li>
 *   <li>Expired key (>24h) → process as new request</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);
    private static final int IDEMPOTENCY_TTL_HOURS = 24;

    private final CheckoutIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(CheckoutIdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Check if request is idempotent replay.
     * Returns cached response if key exists with matching fingerprint.
     *
     * @param idempotencyKey the idempotency key from header
     * @param requestBody the request body to fingerprint
     * @return cached response if replay, empty if new request
     * @throws IllegalStateException if key exists with different fingerprint
     */
    @Transactional(readOnly = true)
    public Optional<ResponseEntity<Object>> checkIdempotency(String idempotencyKey, Object requestBody) {
        String fingerprint = generateFingerprint(requestBody);

        Optional<CheckoutIdempotency> existing = idempotencyRepository
                .findByIdempotencyKeyAndNotExpired(idempotencyKey, Instant.now());

        if (existing.isEmpty()) {
            logger.debug("No idempotency record found for key: {}", idempotencyKey);
            return Optional.empty();
        }

        CheckoutIdempotency record = existing.get();

        // Check if fingerprint matches
        if (!record.getRequestFingerprint().equals(fingerprint)) {
            logger.warn("Idempotency key conflict - key: {}, existing fingerprint: {}, new fingerprint: {}",
                    idempotencyKey, record.getRequestFingerprint(), fingerprint);
            throw new IllegalStateException(
                    "Idempotency key already used with different request body. " +
                    "Please use a new idempotency key or retry with the same request.");
        }

        // Fingerprint matches - return cached response
        logger.info("Returning cached response for idempotency key: {}", idempotencyKey);

        try {
            Object responseBody = objectMapper.readValue(record.getResponseBody(), Object.class);
            return Optional.of(ResponseEntity.status(record.getResponseStatus()).body(responseBody));
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize cached response for key: {}", idempotencyKey, e);
            throw new RuntimeException("Failed to deserialize cached response", e);
        }
    }

    /**
     * Store idempotency record for successful checkout.
     *
     * @param idempotencyKey the idempotency key
     * @param requestBody the request body
     * @param responseStatus the HTTP status code
     * @param responseBody the response body
     */
    @Transactional
    public void storeIdempotency(String idempotencyKey, Object requestBody, int responseStatus, Object responseBody) {
        String fingerprint = generateFingerprint(requestBody);

        try {
            String serializedResponse = objectMapper.writeValueAsString(responseBody);

            CheckoutIdempotency record = new CheckoutIdempotency(
                    idempotencyKey,
                    fingerprint,
                    responseStatus,
                    serializedResponse
            );

            // Set createdAt manually since CheckoutIdempotency doesn't implement Auditable
            // This also auto-sets expiresAt via the setter logic
            record.setCreatedAt(java.time.Instant.now());

            idempotencyRepository.save(record);

            logger.info("Stored idempotency record for key: {}, expires in {}h",
                    idempotencyKey, IDEMPOTENCY_TTL_HOURS);

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize response for idempotency key: {}", idempotencyKey, e);
            throw new RuntimeException("Failed to serialize response", e);
        }
    }

    /**
     * Generate SHA-256 fingerprint of request body.
     *
     * @param requestBody the request body to hash
     * @return hex-encoded SHA-256 hash
     */
    private String generateFingerprint(Object requestBody) {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Clean up expired idempotency records.
     * Should be called periodically (e.g., via scheduled task).
     *
     * @return number of deleted records
     */
    @Transactional
    public int cleanupExpiredRecords() {
        int deleted = idempotencyRepository.deleteExpiredRecords(Instant.now());
        if (deleted > 0) {
            logger.info("Cleaned up {} expired idempotency records", deleted);
        }
        return deleted;
    }
}
