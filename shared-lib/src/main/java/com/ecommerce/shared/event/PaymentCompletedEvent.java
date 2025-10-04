package com.ecommerce.shared.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when payment processing completes (success or failure).
 * Published to Kafka topic: payments.completed
 *
 * <p>This event updates order and payment transaction status.</p>
 */
public record PaymentCompletedEvent(
        @NotNull @JsonProperty("eventId") UUID eventId,
        @NotBlank @JsonProperty("eventType") String eventType,
        @NotBlank @JsonProperty("eventVersion") String eventVersion,
        @NotNull @JsonProperty("timestamp") Instant timestamp,
        @NotBlank @JsonProperty("correlationId") String correlationId,
        @NotNull @JsonProperty("orderId") UUID orderId,
        @NotBlank @JsonProperty("orderNumber") String orderNumber,
        @NotNull @JsonProperty("paymentTransactionId") UUID paymentTransactionId,
        @NotBlank @JsonProperty("status") String status,
        @NotNull @DecimalMin("0.01") @JsonProperty("amount") BigDecimal amount,
        @NotBlank @JsonProperty("paymentMethod") String paymentMethod,
        @JsonProperty("externalTransactionId") String externalTransactionId,
        @JsonProperty("failureReason") String failureReason
) {
    /**
     * Create a new PaymentCompletedEvent with default event type and version.
     *
     * @param correlationId request correlation ID
     * @param orderId order UUID
     * @param orderNumber human-readable order number
     * @param paymentTransactionId payment transaction UUID
     * @param status payment status (SUCCESS or FAILED)
     * @param amount payment amount
     * @param paymentMethod payment method
     * @param externalTransactionId external transaction ID (null if failed)
     * @param failureReason failure reason (null if successful)
     */
    public PaymentCompletedEvent(
            String correlationId,
            UUID orderId,
            String orderNumber,
            UUID paymentTransactionId,
            String status,
            BigDecimal amount,
            String paymentMethod,
            String externalTransactionId,
            String failureReason
    ) {
        this(
                UUID.randomUUID(),
                "PAYMENT_COMPLETED",
                "1.0",
                Instant.now(),
                correlationId,
                orderId,
                orderNumber,
                paymentTransactionId,
                status,
                amount,
                paymentMethod,
                externalTransactionId,
                failureReason
        );
    }
}
