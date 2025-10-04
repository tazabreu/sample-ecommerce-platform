-- Additional performance indexes for order management service

-- Composite index for filtering orders by status and date range
CREATE INDEX idx_order_status_created ON orders(status, created_at);

-- Composite index for manager queries (email + status)
CREATE INDEX idx_order_email_status ON orders(customer_email, status);

-- Index for completed orders (analytics)
CREATE INDEX idx_order_completed ON orders(completed_at) WHERE completed_at IS NOT NULL;

-- Index for pending payments (monitoring)
CREATE INDEX idx_payment_pending ON payment_transactions(status, created_at) WHERE status = 'PENDING';

-- Index for failed payments (alerting)
CREATE INDEX idx_payment_failed ON payment_transactions(status, created_at) WHERE status = 'FAILED';

-- Composite index for order items with product analytics
CREATE INDEX idx_orderitem_product_created ON order_items(product_id, created_at);

