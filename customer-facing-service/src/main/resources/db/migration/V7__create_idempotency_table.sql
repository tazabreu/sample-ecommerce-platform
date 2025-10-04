-- Migration: Create idempotency table for checkout deduplication
-- Purpose: Enable idempotent checkout operations via Idempotency-Key header
-- Pattern: Store key → fingerprint → response for 24h TTL

CREATE TABLE checkout_idempotency (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,  -- SHA-256 hash of request body
    response_status INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

-- Index for cleanup of expired records
CREATE INDEX idx_checkout_idempotency_expires_at ON checkout_idempotency(expires_at);

-- Add comments for documentation
COMMENT ON TABLE checkout_idempotency IS 'Stores idempotency records for checkout operations to prevent duplicate orders';
COMMENT ON COLUMN checkout_idempotency.idempotency_key IS 'Client-provided idempotency key from Idempotency-Key header';
COMMENT ON COLUMN checkout_idempotency.request_fingerprint IS 'SHA-256 hash of request body to detect request changes';
COMMENT ON COLUMN checkout_idempotency.response_status IS 'HTTP status code of cached response';
COMMENT ON COLUMN checkout_idempotency.response_body IS 'Serialized JSON response body';
COMMENT ON COLUMN checkout_idempotency.expires_at IS 'Expiration timestamp (24h from creation)';
