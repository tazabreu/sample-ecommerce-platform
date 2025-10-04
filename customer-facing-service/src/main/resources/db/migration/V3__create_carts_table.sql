-- Create carts table
CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(100) NOT NULL UNIQUE,
    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

-- Create indexes
CREATE UNIQUE INDEX idx_cart_session ON carts(session_id);
CREATE INDEX idx_cart_expires ON carts(expires_at);

-- Add comments
COMMENT ON TABLE carts IS 'Shopping carts for guest checkout';
COMMENT ON COLUMN carts.session_id IS 'Session identifier for guest users';
COMMENT ON COLUMN carts.expires_at IS 'TTL for cart expiration (30 minutes default)';

