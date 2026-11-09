-- ---------- receipt counters: one row per shop, created automatically ----------
CREATE TABLE shop_counters (
                               shop_id         UUID   PRIMARY KEY REFERENCES shops (id),
                               last_receipt_no BIGINT NOT NULL DEFAULT 0
);
INSERT INTO shop_counters (shop_id) SELECT id FROM shops;

CREATE FUNCTION create_shop_counter() RETURNS trigger AS $$
BEGIN
    INSERT INTO shop_counters (shop_id) VALUES (NEW.id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_shops_counter AFTER INSERT ON shops
    FOR EACH ROW EXECUTE FUNCTION create_shop_counter();

-- ---------- sales ----------
CREATE TABLE sales (
                       id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                       business_id     UUID        NOT NULL,
                       shop_id         UUID        NOT NULL,
                       shop_day_id     UUID        NOT NULL,
                       receipt_no      BIGINT      NOT NULL,
                       cashier_id      UUID        NOT NULL,
                       total_minor     BIGINT      NOT NULL,
                       status          VARCHAR(10) NOT NULL DEFAULT 'COMPLETED',
                       idempotency_key UUID        NOT NULL,
                       request_hash    VARCHAR(64) NOT NULL,
                       created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                       CONSTRAINT fk_sales_shop FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
                       CONSTRAINT fk_sales_day  FOREIGN KEY (shop_day_id, shop_id) REFERENCES shop_days (id, shop_id),
                       CONSTRAINT fk_sales_user FOREIGN KEY (cashier_id) REFERENCES users (id),
                       CONSTRAINT uq_sales_receipt     UNIQUE (shop_id, receipt_no),
                       CONSTRAINT uq_sales_idempotency UNIQUE (shop_id, idempotency_key),
                       CONSTRAINT uq_sales_id_shop     UNIQUE (id, shop_id),
                       CONSTRAINT ck_sales_status CHECK (status IN ('COMPLETED', 'REVERSED')),
                       CONSTRAINT ck_sales_total  CHECK (total_minor > 0)
);
CREATE INDEX ix_sales_day ON sales (shop_day_id);
CREATE INDEX ix_sales_shop_created ON sales (shop_id, created_at DESC);

CREATE TABLE sale_lines (
                            id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                            sale_id          UUID          NOT NULL,
                            shop_id          UUID          NOT NULL,
                            business_id      UUID          NOT NULL,
                            item_id          UUID          NOT NULL,
                            line_no          INTEGER       NOT NULL,
                            quantity         NUMERIC(14,3) NOT NULL,
                            unit_price_minor BIGINT        NOT NULL,
                            unit_cost        NUMERIC(18,4) NOT NULL,
                            line_total_minor BIGINT        NOT NULL,
                            line_cost_minor  BIGINT        NOT NULL,
                            created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
                            CONSTRAINT fk_sl_sale FOREIGN KEY (sale_id, shop_id) REFERENCES sales (id, shop_id),
                            CONSTRAINT fk_sl_item FOREIGN KEY (item_id, business_id) REFERENCES items (id, business_id),
                            CONSTRAINT fk_sl_shop FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
                            CONSTRAINT uq_sl_sale_item UNIQUE (sale_id, item_id),
                            CONSTRAINT uq_sl_sale_line UNIQUE (sale_id, line_no),
                            CONSTRAINT ck_sl_quantity CHECK (quantity > 0),
                            CONSTRAINT ck_sl_amounts  CHECK (unit_price_minor >= 0 AND unit_cost >= 0
                                AND line_total_minor >= 0 AND line_cost_minor >= 0)
);

CREATE TABLE sale_allocations (
                                  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                  sale_id      UUID        NOT NULL,
                                  shop_id      UUID        NOT NULL,
                                  method       VARCHAR(10) NOT NULL,
                                  amount_minor BIGINT      NOT NULL,
                                  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  CONSTRAINT fk_sa_sale FOREIGN KEY (sale_id, shop_id) REFERENCES sales (id, shop_id),
                                  CONSTRAINT uq_sa_sale_method UNIQUE (sale_id, method),
                                  CONSTRAINT ck_sa_method CHECK (method IN ('CASH', 'POCHI', 'TILL', 'DENI')),
                                  CONSTRAINT ck_sa_amount CHECK (amount_minor > 0)
);

-- ---------- sales cannot be rewritten ----------
CREATE FUNCTION forbid_row_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sale_lines_append_only BEFORE UPDATE OR DELETE ON sale_lines
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_sale_allocations_append_only BEFORE UPDATE OR DELETE ON sale_allocations
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- A sale is never deleted. Only its status may change, and a reversed sale never comes back.
CREATE FUNCTION protect_sales() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'sales cannot be deleted';
    END IF;
    IF (NEW.id, NEW.business_id, NEW.shop_id, NEW.shop_day_id, NEW.receipt_no, NEW.cashier_id,
        NEW.total_minor, NEW.idempotency_key, NEW.request_hash, NEW.created_at)
        IS DISTINCT FROM
        (OLD.id, OLD.business_id, OLD.shop_id, OLD.shop_day_id, OLD.receipt_no, OLD.cashier_id,
         OLD.total_minor, OLD.idempotency_key, OLD.request_hash, OLD.created_at) THEN
        RAISE EXCEPTION 'sales are append-only: only the status may change';
    END IF;
    IF OLD.status = 'REVERSED' AND NEW.status <> 'REVERSED' THEN
        RAISE EXCEPTION 'a reversed sale cannot be restored';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sales_protect BEFORE UPDATE OR DELETE ON sales
    FOR EACH ROW EXECUTE FUNCTION protect_sales();

-- ---------- at commit, lines and payments must both add up to the sale total ----------
CREATE FUNCTION check_sale_integrity() RETURNS trigger AS $$
DECLARE
    v_sale_id    UUID;
    v_total      BIGINT;
    v_lines      BIGINT;
    v_line_count BIGINT;
    v_allocated  BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'sales' THEN
        v_sale_id := NEW.id;
    ELSE
        v_sale_id := NEW.sale_id;
    END IF;

    SELECT total_minor INTO v_total FROM sales WHERE id = v_sale_id;
    SELECT COALESCE(SUM(line_total_minor), 0), COUNT(*) INTO v_lines, v_line_count
    FROM sale_lines WHERE sale_id = v_sale_id;
    SELECT COALESCE(SUM(amount_minor), 0) INTO v_allocated
    FROM sale_allocations WHERE sale_id = v_sale_id;

    IF v_line_count = 0 THEN
        RAISE EXCEPTION 'A sale needs at least one line';
    END IF;
    IF v_lines <> v_total THEN
        RAISE EXCEPTION 'Sale lines (%) do not add up to the sale total (%)', v_lines, v_total;
    END IF;
    IF v_allocated <> v_total THEN
        RAISE EXCEPTION 'Payment allocations (%) do not add up to the sale total (%)', v_allocated, v_total;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_sales_integrity AFTER INSERT ON sales
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_sale_integrity();
CREATE CONSTRAINT TRIGGER trg_sale_lines_integrity AFTER INSERT ON sale_lines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_sale_integrity();
CREATE CONSTRAINT TRIGGER trg_sale_allocations_integrity AFTER INSERT ON sale_allocations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_sale_integrity();

-- ---------- the stock ledger learns about sales ----------
ALTER TABLE stock_movements ADD COLUMN sale_id UUID;
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_smv_sale FOREIGN KEY (sale_id, shop_id) REFERENCES sales (id, shop_id);

ALTER TABLE stock_movements DROP CONSTRAINT ck_smv_type;
ALTER TABLE stock_movements DROP CONSTRAINT ck_smv_in;

ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_type
    CHECK (movement_type IN ('OPENING', 'RECEIPT', 'ADJUSTMENT', 'SALE', 'SALE_REVERSAL'));
ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_in
    CHECK (movement_type NOT IN ('OPENING', 'RECEIPT')
        OR (quantity_delta > 0 AND total_cost_minor IS NOT NULL));
ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_sale_ref
    CHECK ((movement_type IN ('SALE', 'SALE_REVERSAL')) = (sale_id IS NOT NULL));
ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_sale_dir
    CHECK (movement_type <> 'SALE' OR quantity_delta < 0);
ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_reversal_dir
    CHECK (movement_type <> 'SALE_REVERSAL' OR quantity_delta > 0);

CREATE INDEX ix_smv_sale ON stock_movements (sale_id) WHERE sale_id IS NOT NULL;