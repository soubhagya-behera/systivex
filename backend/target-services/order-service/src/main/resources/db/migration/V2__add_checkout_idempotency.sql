-- Phase 2B follow-up (checkout idempotency): order-service owns this schema.
-- Flyway is the schema authority; Hibernate only validates (ddl-auto=validate).
-- No other service may touch this table; cross-service needs go over HTTP,
-- never over this database.
--
-- An idempotency key identifies ONE checkout attempt, and one checkout attempt
-- IS one order row — so the key lives on `orders` instead of in a side table.
-- That keeps the whole idempotency record in a single row that is written by
-- the same statements that write the order outcome.
--
-- `idempotency_key_hash` is the SHA-256 hex digest of the client-supplied key:
-- the raw key is never stored, never logged, and never becomes a metric label.
-- `idempotency_request_hash` is the SHA-256 hex digest of the canonical request
-- payload, so the same key with a different payload can be detected.
--
-- The unique index below is the concurrency arbiter: two concurrent checkouts
-- carrying the same key can never both insert an order row — the loser gets a
-- unique violation and replays the winner's outcome. NULL (no key supplied)
-- stays distinct per row, so requests without an Idempotency-Key keep their
-- pre-existing behaviour.

ALTER TABLE orders
    ADD COLUMN idempotency_key_hash    VARCHAR(64),
    ADD COLUMN idempotency_request_hash VARCHAR(64),
    ADD COLUMN failure_reason           VARCHAR(64),
    ADD COLUMN failure_message          VARCHAR(512);

CREATE UNIQUE INDEX uq_orders_idempotency_key ON orders (idempotency_key_hash);
