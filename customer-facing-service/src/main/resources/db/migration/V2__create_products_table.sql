-- Create products table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL CHECK (price > 0),
    inventory_quantity INTEGER NOT NULL CHECK (inventory_quantity >= 0),
    category_id UUID NOT NULL REFERENCES categories(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Create indexes
CREATE UNIQUE INDEX idx_product_sku ON products(sku);
CREATE INDEX idx_product_category ON products(category_id);
CREATE INDEX idx_product_active ON products(is_active);

-- Add comments
COMMENT ON TABLE products IS 'Product catalog with inventory tracking';
COMMENT ON COLUMN products.version IS 'Optimistic locking version';
COMMENT ON COLUMN products.price IS 'Price in USD with 2 decimal places';
COMMENT ON COLUMN products.inventory_quantity IS 'Available inventory count';

