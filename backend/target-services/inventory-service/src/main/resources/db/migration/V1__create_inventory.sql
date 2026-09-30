-- Phase 2B: inventory-service owns this schema. Flyway is the schema authority;
-- Hibernate only validates (ddl-auto=validate). No other service may touch
-- these tables; order-service reads reservation outcomes over HTTP, never here.
--
-- Note: inventory_reservations deliberately has NO foreign key to
-- inventory_items, so rejected attempts (including requests for unknown
-- products) can still be recorded as REJECTED rows.

CREATE TABLE inventory_items (
    product_id         VARCHAR(64) NOT NULL PRIMARY KEY,
    available_quantity INTEGER     NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_inventory_quantity_non_negative CHECK (available_quantity >= 0)
);

CREATE TABLE inventory_reservations (
    id         UUID        NOT NULL PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL,
    quantity   INTEGER     NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_reservation_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_reservation_status CHECK (status IN ('RESERVED', 'REJECTED'))
);

CREATE INDEX idx_reservations_product ON inventory_reservations (product_id);
CREATE INDEX idx_reservations_status ON inventory_reservations (status);
