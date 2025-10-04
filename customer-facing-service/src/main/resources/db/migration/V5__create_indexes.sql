-- Additional performance indexes for customer-facing service

-- Composite index for filtering products by category and active status
CREATE INDEX idx_product_category_active ON products(category_id, is_active) WHERE is_active = TRUE;

-- Index for cart cleanup job (find expired carts)
CREATE INDEX idx_cart_expires_cleanup ON carts(expires_at);

-- Partial index for active products with inventory
CREATE INDEX idx_product_in_stock ON products(id) WHERE is_active = TRUE AND inventory_quantity > 0;

-- Add GIN index for product name search (future full-text search)
CREATE INDEX idx_product_name_search ON products USING gin(to_tsvector('english', name));

-- Composite index for cart items with product info (common join)
CREATE INDEX idx_cartitem_cart_product ON cart_items(cart_id, product_id);

