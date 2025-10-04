-- Create processed_events table for Kafka event idempotency

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL
);

-- Create indexes
CREATE INDEX idx_processed_event_type ON processed_events(event_type);
CREATE INDEX idx_processed_event_order ON processed_events(order_id);
CREATE INDEX idx_processed_event_timestamp ON processed_events(processed_at);

-- Add comments
COMMENT ON TABLE processed_events IS 'Kafka event tracking for idempotency (at-least-once delivery)';
COMMENT ON COLUMN processed_events.event_id IS 'UUID from Kafka event (dedupe key)';
COMMENT ON COLUMN processed_events.event_type IS 'Event type: ORDER_CREATED, PAYMENT_COMPLETED, etc.';
COMMENT ON COLUMN processed_events.order_id IS 'Associated order ID (nullable for non-order events)';

-- Create cleanup function for old processed events (retention 30 days)
CREATE OR REPLACE FUNCTION cleanup_old_processed_events()
RETURNS void AS $$
BEGIN
    DELETE FROM processed_events WHERE processed_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_processed_events() IS 'Cleanup processed events older than 30 days (scheduled job)';

