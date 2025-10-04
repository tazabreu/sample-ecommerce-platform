-- Create order_items table
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_sku VARCHAR(50) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price_snapshot DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_orderitem_order ON order_items(order_id);
CREATE INDEX idx_orderitem_product ON order_items(product_id);

-- Add comments
COMMENT ON TABLE order_items IS 'Immutable order line items with product snapshots';
COMMENT ON COLUMN order_items.product_id IS 'Product UUID (no FK to allow product deletion without affecting orders)';
COMMENT ON COLUMN order_items.product_sku IS 'Product SKU snapshot at order time';
COMMENT ON COLUMN order_items.product_name IS 'Product name snapshot at order time';
COMMENT ON COLUMN order_items.price_snapshot IS 'Product price at order time';

