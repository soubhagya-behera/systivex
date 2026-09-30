-- Phase 2B local-development seed data (NOT production data).
--
-- A single deterministic catalog row so the end-to-end checkout can be
-- demonstrated without hand-inserting rows: SKU-1001 with 10 units on hand.
-- Success path: reserve 1..10 units. Failure path: request more than 10
-- (e.g. 99) and watch the reservation get REJECTED.
--
-- Idempotent by construction (ON CONFLICT DO NOTHING) so re-migration and
-- fresh containers converge on the same row.

INSERT INTO inventory_items (product_id, available_quantity)
VALUES ('SKU-1001', 10)
ON CONFLICT (product_id) DO NOTHING;
