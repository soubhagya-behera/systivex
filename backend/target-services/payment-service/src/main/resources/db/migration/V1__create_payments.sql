-- Phase 2B: payment-service owns this schema. Flyway is the schema authority;
-- Hibernate only validates (ddl-auto=validate). No other service may touch
-- this table; order-service reads payment outcomes over HTTP, never here.

CREATE TABLE payments (
    id              UUID           NOT NULL PRIMARY KEY,
    customer_id     VARCHAR(64)    NOT NULL,
    order_reference VARCHAR(64),
    amount          NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payments_status CHECK (status IN ('AUTHORIZED', 'DECLINED'))
);

CREATE INDEX idx_payments_customer ON payments (customer_id);
CREATE INDEX idx_payments_order_reference ON payments (order_reference);
