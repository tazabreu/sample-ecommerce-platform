-- Migration: Create order number sequence table
-- Purpose: Replace in-memory AtomicInteger with DB-backed sequence for order number generation
-- Format: ORD-YYYYMMDD-### (e.g., ORD-20250104-001)

CREATE TABLE order_number_sequence (
    date_key VARCHAR(8) PRIMARY KEY,  -- Format: YYYYMMDD
    last_sequence INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast lookups by date
CREATE INDEX idx_order_number_sequence_date_key ON order_number_sequence(date_key);

-- Add comment for documentation
COMMENT ON TABLE order_number_sequence IS 'Stores daily order number sequences for generating unique order numbers';
COMMENT ON COLUMN order_number_sequence.date_key IS 'Date in YYYYMMDD format';
COMMENT ON COLUMN order_number_sequence.last_sequence IS 'Last used sequence number for this date (resets daily)';
