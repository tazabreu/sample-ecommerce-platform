-- Migration: Create transactional outbox table for OrderCreated events
-- Purpose: Replace synchronous Kafka publishing with transactional outbox pattern
-- Pattern: Write event to DB in same transaction, publish asynchronously via background job

CREATE TABLE order_created_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,  -- Order ID
    aggregate_type VARCHAR(50) NOT NULL DEFAULT 'ORDER',
    event_type VARCHAR(100) NOT NULL DEFAULT 'OrderCreatedEvent',
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Index for polling pending events
CREATE INDEX idx_outbox_status_created ON order_created_outbox(status, created_at)
    WHERE status = 'PENDING';

-- Index for aggregate lookup
CREATE INDEX idx_outbox_aggregate ON order_created_outbox(aggregate_id, aggregate_type);

-- Add comments for documentation
COMMENT ON TABLE order_created_outbox IS 'Transactional outbox for OrderCreated events';
COMMENT ON COLUMN order_created_outbox.aggregate_id IS 'Order UUID';
COMMENT ON COLUMN order_created_outbox.payload IS 'Serialized OrderCreatedEvent as JSON';
COMMENT ON COLUMN order_created_outbox.status IS 'PENDING=not published, PUBLISHED=sent to Kafka, FAILED=max retries exceeded';
COMMENT ON COLUMN order_created_outbox.retry_count IS 'Number of publish attempts';
