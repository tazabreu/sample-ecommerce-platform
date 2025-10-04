package com.ecommerce.order.payment;

/**
 * Result of a payment processing attempt.
 * Contains status, transaction details, and error information if failed.
 */
public record PaymentResult(
        boolean success,
        String externalTransactionId,
        String failureReason
) {
    /**
     * Create a successful payment result.
     *
     * @param externalTransactionId the external transaction ID
     * @return payment result
     */
    public static PaymentResult success(String externalTransactionId) {
        return new PaymentResult(true, externalTransactionId, null);
    }

    /**
     * Create a failed payment result.
     *
     * @param failureReason the failure reason
     * @return payment result
     */
    public static PaymentResult failure(String failureReason) {
        return new PaymentResult(false, null, failureReason);
    }
}


