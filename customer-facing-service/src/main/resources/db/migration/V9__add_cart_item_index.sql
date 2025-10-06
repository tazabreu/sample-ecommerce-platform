ALTER TABLE cart_items
    ADD COLUMN item_index INTEGER NOT NULL DEFAULT 0;

-- Ensure existing constraint remains valid
ALTER TABLE cart_items
    ALTER COLUMN item_index DROP DEFAULT;
