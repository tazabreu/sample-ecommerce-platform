-- Create ENUM types for order and payment status

-- Order status enum
CREATE TYPE order_status AS ENUM (
    'PENDING',      -- Order created, awaiting payment processing
    'PROCESSING',   -- Payment in progress
    'PAID',         -- Payment successful, ready for fulfillment
    'FULFILLED',    -- Order shipped/delivered
    'CANCELLED',    -- Order cancelled
    'FAILED'        -- Payment failed
);

-- Payment status enum
CREATE TYPE payment_status AS ENUM (
    'PENDING',      -- Payment initiated, awaiting result
    'SUCCESS',      -- Payment successful
    'FAILED'        -- Payment failed
);

-- Add comments
COMMENT ON TYPE order_status IS 'Order lifecycle status';
COMMENT ON TYPE payment_status IS 'Payment transaction status';

