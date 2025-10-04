-- Create categories table
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on name for faster lookups
CREATE UNIQUE INDEX idx_category_name ON categories(name);

-- Add comment
COMMENT ON TABLE categories IS 'Product categories for organizing catalog';

