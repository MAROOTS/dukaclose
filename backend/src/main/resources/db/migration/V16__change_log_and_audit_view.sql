CREATE TABLE change_events (
                               id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                               seq          BIGINT      GENERATED ALWAYS AS IDENTITY,
                               business_id  UUID        NOT NULL,
                               shop_id      UUID,
                               kind         VARCHAR(30) NOT NULL,
                               entity_type  VARCHAR(20) NOT NULL,
                               entity_id    UUID        NOT NULL,
                               actor_id     UUID        NOT NULL,
                               before_state JSONB,
                               after_state  JSONB,
                               created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                               CONSTRAINT fk_ce_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    -- a shop, when given, must belong to the same business
                               CONSTRAINT fk_ce_shop  FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
                               CONSTRAINT fk_ce_actor FOREIGN KEY (actor_id) REFERENCES users (id),
                               CONSTRAINT ck_ce_kind CHECK (kind IN ('CREDIT_LIMIT_CHANGED', 'ITEM_CREATED', 'ITEM_UPDATED', 'ITEM_ARCHIVED',
                                                                     'ITEM_RESTORED', 'PRICE_CHANGED', 'REORDER_LEVEL_CHANGED', 'SHOP_CREATED', 'CASHIER_CREATED',
                                                                     'CASHIER_ASSIGNED', 'CASHIER_UNASSIGNED')),
                               CONSTRAINT ck_ce_entity CHECK (entity_type IN ('ITEM', 'SHOP_ITEM', 'SHOP', 'USER', 'CUSTOMER')),
                               CONSTRAINT ck_ce_shop_kinds CHECK (kind NOT IN ('PRICE_CHANGED', 'REORDER_LEVEL_CHANGED', 'SHOP_CREATED',
                                                                               'CASHIER_ASSIGNED', 'CASHIER_UNASSIGNED') OR shop_id IS NOT NULL),
                               CONSTRAINT ck_ce_before CHECK (before_state IS NULL OR jsonb_typeof(before_state) = 'object'),
                               CONSTRAINT ck_ce_after  CHECK (after_state IS NULL OR jsonb_typeof(after_state) = 'object')
);
CREATE INDEX ix_ce_business_time ON change_events (business_id, created_at DESC);
CREATE INDEX ix_ce_shop_time ON change_events (shop_id, created_at DESC);

CREATE TRIGGER trg_change_events_append_only BEFORE UPDATE OR DELETE ON change_events
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- One read-only view over every record we already keep. Nothing is copied: a record is in the history because it exists.
CREATE VIEW audit_events AS
SELECT s.created_at AS occurred_at, s.business_id, s.shop_id, 'SALE_RECORDED'::varchar AS kind, s.cashier_id AS actor_id,
       'SALE'::varchar AS entity_type, s.id AS entity_id, s.receipt_no AS receipt_no, s.total_minor AS amount_minor,
       NULL::varchar AS reason, NULL::jsonb AS details
FROM sales s
UNION ALL
SELECT r.created_at, r.business_id, r.shop_id, 'SALE_REVERSED', r.reversed_by, 'SALE', r.sale_id, s.receipt_no,
       s.total_minor, r.reason, NULL::jsonb
FROM sale_reversals r JOIN sales s ON s.id = r.sale_id
UNION ALL
SELECT f.created_at, f.business_id, f.shop_id, 'REFUND_RECORDED', f.refunded_by, 'SALE', f.sale_id, s.receipt_no,
       f.total_minor, f.reason, jsonb_build_object('debtCreditMinor', f.debt_credit_minor)
FROM sale_refunds f JOIN sales s ON s.id = f.sale_id
UNION ALL
SELECT p.created_at, p.business_id, p.shop_id, 'DEBT_PAYMENT_RECORDED', p.received_by, 'DEBT_PAYMENT', p.id,
       NULL::bigint, p.amount_minor, NULL::varchar, jsonb_build_object('method', p.method)
FROM debt_payments p
UNION ALL
SELECT r.created_at, p.business_id, r.shop_id, 'DEBT_PAYMENT_REVERSED', r.reversed_by, 'DEBT_PAYMENT', r.payment_id,
       NULL::bigint, p.amount_minor, r.reason, NULL::jsonb
FROM debt_payment_reversals r JOIN debt_payments p ON p.id = r.payment_id
UNION ALL
SELECT w.created_at, w.business_id, d.shop_id, 'DEBT_WRITTEN_OFF', w.written_off_by, 'CUSTOMER', w.customer_id,
       s.receipt_no, w.amount_minor, w.reason, NULL::jsonb
FROM debt_write_offs w JOIN customer_debts d ON d.id = w.debt_id JOIN sales s ON s.id = d.sale_id
UNION ALL
SELECT m.created_at, m.business_id, m.shop_id,
       CASE WHEN m.direction = 'IN' THEN 'MONEY_IN_RECORDED' ELSE 'MONEY_OUT_RECORDED' END, m.created_by,
       'MONEY_MOVEMENT', m.id, NULL::bigint, m.amount_minor, m.note,
       jsonb_build_object('category', m.category, 'method', m.method)
FROM money_movements m
UNION ALL
SELECT r.created_at, m.business_id, r.shop_id, 'MONEY_REVERSED', r.reversed_by, 'MONEY_MOVEMENT', r.movement_id,
       NULL::bigint, m.amount_minor, r.reason, jsonb_build_object('category', m.category, 'method', m.method)
FROM money_movement_reversals r JOIN money_movements m ON m.id = r.movement_id
UNION ALL
-- only deliberate stock actions: the movements a sale or a refund makes are consequences, not events
SELECT m.created_at, m.business_id, m.shop_id,
       CASE m.movement_type WHEN 'OPENING' THEN 'STOCK_OPENING' WHEN 'RECEIPT' THEN 'STOCK_RECEIVED'
                            ELSE 'STOCK_ADJUSTED' END, m.created_by, 'STOCK_MOVEMENT', m.id, NULL::bigint, m.total_cost_minor,
       m.note, jsonb_build_object('item', i.name, 'quantity', m.quantity_delta, 'reason', m.reason)
FROM stock_movements m JOIN items i ON i.id = m.item_id
WHERE m.movement_type IN ('OPENING', 'RECEIPT', 'ADJUSTMENT')
UNION ALL
SELECT c.created_at, c.business_id, c.shop_id, 'CASH_COUNTED', c.counted_by, 'CASH_COUNT', c.id, NULL::bigint,
       c.counted_minor, NULL::varchar, jsonb_build_object('expectedMinor', c.expected_minor)
FROM cash_counts c
UNION ALL
SELECT c.closed_at, c.business_id, c.shop_id, 'DAY_CLOSED', c.closed_by, 'DAY_CLOSE', c.id, NULL::bigint,
       c.sales_total_minor, c.note,
       jsonb_build_object('businessDate', c.business_date, 'countedMinor', c.counted_minor,
                          'differenceMinor', c.difference_minor)
FROM day_closes c
UNION ALL
SELECT d.created_at, d.business_id, d.shop_id, 'DAY_OPENED', d.opened_by, 'SHOP_DAY', d.id, NULL::bigint,
       d.opening_float_minor, NULL::varchar,
       jsonb_build_object('businessDate', d.business_date, 'carriedOverMinor', d.carried_over_minor)
FROM shop_days d
UNION ALL
SELECT e.created_at, e.business_id, e.shop_id, e.kind, e.actor_id, e.entity_type, e.entity_id, NULL::bigint,
       NULL::bigint, NULL::varchar, jsonb_build_object('before', e.before_state, 'after', e.after_state)
FROM change_events e;