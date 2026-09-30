-- Phase 2B: order-service owns this schema. Flyway is the schema authority;
-- Hibernate only validates (ddl-auto=validate). No other service may touch
-- this table; cross-service needs go over HTTP, never over this database.

CREATE TABLE orders (
    id               UUID          NOT NULL PRIMARY KEY,
    customer_id      VARCHAR(64)   NOT NULL,
    product_id       VARCHAR(64)   NOT NULL,
    quantity         INTEGER       NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    status           VARCHAR(16)   NOT NULL,
    reservation_id   UUID,
    authorization_id UUID,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_orders_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED'))
);

CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_customer ON orders (customer_id);
