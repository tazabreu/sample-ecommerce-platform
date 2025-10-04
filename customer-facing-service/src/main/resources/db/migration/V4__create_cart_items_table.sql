-- Create cart_items table
CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price_snapshot DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_product UNIQUE (cart_id, product_id)
);

-- Create indexes
CREATE INDEX idx_cartitem_cart ON cart_items(cart_id);
CREATE INDEX idx_cartitem_product ON cart_items(product_id);

-- Add comments
COMMENT ON TABLE cart_items IS 'Individual items within shopping carts';
COMMENT ON COLUMN cart_items.price_snapshot IS 'Product price at time of adding to cart';
COMMENT ON COLUMN cart_items.subtotal IS 'Calculated: quantity * price_snapshot';
COMMENT ON CONSTRAINT uk_cart_product ON cart_items IS 'One product per cart (update quantity if adding same product)';

