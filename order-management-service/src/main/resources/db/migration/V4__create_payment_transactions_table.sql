-- Create payment_transactions table
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    amount DECIMAL(10,2) NOT NULL,
    status payment_status NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50) NOT NULL,
    external_transaction_id VARCHAR(100),
    failure_reason TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create indexes
CREATE UNIQUE INDEX idx_payment_order ON payment_transactions(order_id);
CREATE INDEX idx_payment_status ON payment_transactions(status);
CREATE INDEX idx_payment_external ON payment_transactions(external_transaction_id);

-- Add comments
COMMENT ON TABLE payment_transactions IS 'Payment processing records for orders';
COMMENT ON COLUMN payment_transactions.order_id IS 'One-to-one relationship with orders table';
COMMENT ON COLUMN payment_transactions.payment_method IS 'Payment method: MOCK, STRIPE, PAYPAL, etc.';
COMMENT ON COLUMN payment_transactions.external_transaction_id IS 'External payment provider transaction ID (null if failed)';
COMMENT ON COLUMN payment_transactions.failure_reason IS 'Failure reason text (null if successful)';
COMMENT ON COLUMN payment_transactions.attempt_count IS 'Number of payment attempts (for retry logic)';

