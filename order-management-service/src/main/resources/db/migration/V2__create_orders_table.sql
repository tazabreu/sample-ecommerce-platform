-- Create orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(20) NOT NULL UNIQUE,
    customer_name VARCHAR(200) NOT NULL,
    customer_email VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    shipping_address JSONB NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    status order_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create indexes
CREATE UNIQUE INDEX idx_order_number ON orders(order_number);
CREATE INDEX idx_order_status ON orders(status);
CREATE INDEX idx_order_created ON orders(created_at);
CREATE INDEX idx_order_email ON orders(customer_email);

-- Create GIN index for JSONB shipping address queries
CREATE INDEX idx_order_shipping_address ON orders USING gin(shipping_address);

-- Add comments
COMMENT ON TABLE orders IS 'Customer orders with denormalized customer info';
COMMENT ON COLUMN orders.order_number IS 'Human-readable order number (format: ORD-YYYYMMDD-NNN)';
COMMENT ON COLUMN orders.shipping_address IS 'JSONB containing: street, city, state, postalCode, country';
COMMENT ON COLUMN orders.completed_at IS 'Timestamp when order was fulfilled or cancelled';

