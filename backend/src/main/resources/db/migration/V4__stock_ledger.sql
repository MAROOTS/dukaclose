CREATE TABLE stock_movements (
                                 id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                 seq              BIGINT        GENERATED ALWAYS AS IDENTITY,   -- total order of the ledger
                                 business_id      UUID          NOT NULL,
                                 shop_id          UUID          NOT NULL,
                                 item_id          UUID          NOT NULL,
                                 request_id       UUID          NOT NULL,
                                 movement_type    VARCHAR(20)   NOT NULL,
                                 quantity_delta   NUMERIC(14,3) NOT NULL,
                                 balance_after    NUMERIC(14,3) NOT NULL,
                                 unit_cost        NUMERIC(18,4),
                                 total_cost_minor BIGINT,
                                 reason           VARCHAR(20),
                                 note             VARCHAR(200),
                                 created_by       UUID          NOT NULL,
                                 created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- shop_day_id arrives in Phase 5, as a nullable column
                                 CONSTRAINT fk_smv_shop      FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
                                 CONSTRAINT fk_smv_item      FOREIGN KEY (item_id, business_id) REFERENCES items (id, business_id),
                                 CONSTRAINT fk_smv_inventory FOREIGN KEY (shop_id, item_id)     REFERENCES shop_inventory (shop_id, item_id),
                                 CONSTRAINT fk_smv_user      FOREIGN KEY (created_by)           REFERENCES users (id),
                                 CONSTRAINT uq_smv_request   UNIQUE (shop_id, request_id),
                                 CONSTRAINT uq_smv_seq       UNIQUE (seq),
                                 CONSTRAINT ck_smv_type      CHECK (movement_type IN ('OPENING', 'RECEIPT', 'ADJUSTMENT')),
                                 CONSTRAINT ck_smv_reason    CHECK (reason IS NULL OR reason IN
                                                                                      ('DAMAGED', 'EXPIRED', 'LOST', 'FOUND', 'COUNT_CORRECTION', 'OTHER')),
                                 CONSTRAINT ck_smv_delta     CHECK (quantity_delta <> 0),
                                 CONSTRAINT ck_smv_balance   CHECK (balance_after >= 0),
    -- stock coming in is positive and always has a cost; adjustments always have a reason
                                 CONSTRAINT ck_smv_in        CHECK (movement_type = 'ADJUSTMENT'
                                     OR (quantity_delta > 0 AND total_cost_minor IS NOT NULL)),
                                 CONSTRAINT ck_smv_reason_ad CHECK ((movement_type = 'ADJUSTMENT') = (reason IS NOT NULL))
);

CREATE INDEX ix_smv_shop_item_seq ON stock_movements (shop_id, item_id, seq DESC);

-- The ledger is append-only: no edits, no deletes. Corrections are new rows.
CREATE FUNCTION forbid_ledger_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'stock_movements is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stock_movements_append_only
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();