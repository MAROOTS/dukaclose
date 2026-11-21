-- keys so refund rows can be pinned to the exact sale, line and day
ALTER TABLE sales      ADD CONSTRAINT uq_sales_id_day UNIQUE (id, shop_day_id);
ALTER TABLE sale_lines ADD CONSTRAINT uq_sl_id_sale   UNIQUE (id, sale_id);
ALTER TABLE sale_lines ADD CONSTRAINT uq_sl_id_item   UNIQUE (id, item_id);

CREATE TABLE sale_refunds (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id       UUID         NOT NULL,
    shop_id           UUID         NOT NULL,
    shop_day_id       UUID         NOT NULL,       -- the OPEN day the refund is recorded on
    sale_id           UUID         NOT NULL,
    sale_day_id       UUID         NOT NULL,       -- the closed day the sale belongs to
    total_minor       BIGINT       NOT NULL,
    debt_credit_minor BIGINT       NOT NULL DEFAULT 0,
    reason            VARCHAR(200) NOT NULL,
    refunded_by       UUID         NOT NULL,
    idempotency_key   UUID         NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_rf_shop     FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
    CONSTRAINT fk_rf_day      FOREIGN KEY (shop_day_id, shop_id) REFERENCES shop_days (id, shop_id),
    CONSTRAINT fk_rf_sale     FOREIGN KEY (sale_id, shop_id)     REFERENCES sales (id, shop_id),
    CONSTRAINT fk_rf_sale_day FOREIGN KEY (sale_id, sale_day_id) REFERENCES sales (id, shop_day_id),
    CONSTRAINT fk_rf_user     FOREIGN KEY (refunded_by)          REFERENCES users (id),
    CONSTRAINT uq_rf_idempotency UNIQUE (shop_id, idempotency_key),
    CONSTRAINT uq_rf_id_shop UNIQUE (id, shop_id),
    CONSTRAINT uq_rf_id_sale UNIQUE (id, sale_id),
    CONSTRAINT ck_rf_total   CHECK (total_minor > 0),
    CONSTRAINT ck_rf_credit  CHECK (debt_credit_minor >= 0 AND debt_credit_minor <= total_minor),
    CONSTRAINT ck_rf_reason  CHECK (char_length(btrim(reason)) >= 3),
    CONSTRAINT ck_rf_other_day CHECK (shop_day_id <> sale_day_id)
);
CREATE INDEX ix_rf_sale ON sale_refunds (sale_id, created_at DESC);
CREATE INDEX ix_rf_day  ON sale_refunds (shop_day_id);

CREATE TABLE sale_refund_lines (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id         UUID          NOT NULL,
    shop_id           UUID          NOT NULL,
    sale_id           UUID          NOT NULL,
    sale_line_id      UUID          NOT NULL,
    item_id           UUID          NOT NULL,
    quantity          NUMERIC(14,3) NOT NULL,
    unit_price_minor  BIGINT        NOT NULL,
    unit_cost         NUMERIC(18,4) NOT NULL,
    line_refund_minor BIGINT        NOT NULL,
    disposition       VARCHAR(10)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_rfl_refund      FOREIGN KEY (refund_id, shop_id)     REFERENCES sale_refunds (id, shop_id),
    CONSTRAINT fk_rfl_refund_sale FOREIGN KEY (refund_id, sale_id)     REFERENCES sale_refunds (id, sale_id),
    CONSTRAINT fk_rfl_line        FOREIGN KEY (sale_line_id, sale_id)  REFERENCES sale_lines (id, sale_id),
    CONSTRAINT fk_rfl_item        FOREIGN KEY (sale_line_id, item_id)  REFERENCES sale_lines (id, item_id),
    CONSTRAINT uq_rfl_refund_line UNIQUE (refund_id, sale_line_id),
    CONSTRAINT ck_rfl_quantity    CHECK (quantity > 0),
    CONSTRAINT ck_rfl_amounts     CHECK (unit_price_minor >= 0 AND unit_cost >= 0 AND line_refund_minor >= 0),
    CONSTRAINT ck_rfl_disposition CHECK (disposition IN ('RESTOCK', 'WRITE_OFF'))
);
CREATE INDEX ix_rfl_sale_line ON sale_refund_lines (sale_line_id);

CREATE TABLE sale_refund_payouts (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id    UUID        NOT NULL,
    shop_id      UUID        NOT NULL,
    method       VARCHAR(10) NOT NULL,
    amount_minor BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_rfp_refund FOREIGN KEY (refund_id, shop_id) REFERENCES sale_refunds (id, shop_id),
    CONSTRAINT uq_rfp_refund_method UNIQUE (refund_id, method),
    CONSTRAINT ck_rfp_method CHECK (method IN ('CASH', 'POCHI', 'TILL')),
    CONSTRAINT ck_rfp_amount CHECK (amount_minor > 0)
);

-- A refund can reduce what a customer still owes. This is the "authorised credit" of the deni formula.
CREATE TABLE debt_credits (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID        NOT NULL,
    debt_id      UUID        NOT NULL,
    customer_id  UUID        NOT NULL,
    refund_id    UUID        NOT NULL,
    amount_minor BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_dcr_debt     FOREIGN KEY (debt_id, customer_id)     REFERENCES customer_debts (id, customer_id),
    CONSTRAINT fk_dcr_customer FOREIGN KEY (customer_id, business_id) REFERENCES customers (id, business_id),
    CONSTRAINT fk_dcr_refund   FOREIGN KEY (refund_id)                REFERENCES sale_refunds (id),
    CONSTRAINT uq_dcr_refund UNIQUE (refund_id),
    CONSTRAINT ck_dcr_amount CHECK (amount_minor > 0)
);
CREATE INDEX ix_dcr_debt ON debt_credits (debt_id);
CREATE INDEX ix_dcr_customer ON debt_credits (customer_id);

CREATE TRIGGER trg_sale_refunds_append_only BEFORE UPDATE OR DELETE ON sale_refunds
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_sale_refund_lines_append_only BEFORE UPDATE OR DELETE ON sale_refund_lines
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_sale_refund_payouts_append_only BEFORE UPDATE OR DELETE ON sale_refund_payouts
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_debt_credits_append_only BEFORE UPDATE OR DELETE ON debt_credits
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- a refund is recorded on an open day only
CREATE TRIGGER trg_zz_sale_refunds_day_open AFTER INSERT ON sale_refunds
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();

-- At commit: lines, payouts and deni credit all add up to the refund total
CREATE FUNCTION check_refund_integrity() RETURNS trigger AS $$
DECLARE
    v_refund_id  UUID;
    v_total      BIGINT;
    v_credit     BIGINT;
    v_sale_id    UUID;
    v_lines      BIGINT;
    v_line_count BIGINT;
    v_payouts    BIGINT;
    v_credits    BIGINT;
    v_sale_stat  VARCHAR;
    v_day_stat   VARCHAR;
BEGIN
    IF TG_TABLE_NAME = 'sale_refunds' THEN
        v_refund_id := NEW.id;
    ELSE
        v_refund_id := NEW.refund_id;
    END IF;

    SELECT total_minor, debt_credit_minor, sale_id INTO v_total, v_credit, v_sale_id
      FROM sale_refunds WHERE id = v_refund_id;
    SELECT COALESCE(SUM(line_refund_minor), 0), COUNT(*) INTO v_lines, v_line_count
      FROM sale_refund_lines WHERE refund_id = v_refund_id;
    SELECT COALESCE(SUM(amount_minor), 0) INTO v_payouts FROM sale_refund_payouts WHERE refund_id = v_refund_id;
    SELECT COALESCE(SUM(amount_minor), 0) INTO v_credits FROM debt_credits WHERE refund_id = v_refund_id;
    SELECT s.status, d.status INTO v_sale_stat, v_day_stat
      FROM sales s JOIN shop_days d ON d.id = s.shop_day_id WHERE s.id = v_sale_id;

    IF v_line_count = 0 THEN
        RAISE EXCEPTION 'A refund needs at least one line';
    END IF;
    IF v_lines <> v_total THEN
        RAISE EXCEPTION 'Refund lines (%) do not add up to the refund total (%)', v_lines, v_total;
    END IF;
    IF v_payouts + v_credit <> v_total THEN
        RAISE EXCEPTION 'Refund payouts (%) and deni credit (%) do not add up to the refund total (%)',
                        v_payouts, v_credit, v_total;
    END IF;
    IF v_credits <> v_credit THEN
        RAISE EXCEPTION 'The deni credit (%) does not match the credit records (%)', v_credit, v_credits;
    END IF;
    IF v_sale_stat <> 'COMPLETED' THEN
        RAISE EXCEPTION 'A reversed sale cannot be refunded';
    END IF;
    IF v_day_stat <> 'CLOSED' THEN
        RAISE EXCEPTION 'A refund is only for a sale from a closed day';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_refunds_integrity AFTER INSERT ON sale_refunds
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_refund_integrity();
CREATE CONSTRAINT TRIGGER trg_refund_lines_integrity AFTER INSERT ON sale_refund_lines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_refund_integrity();
CREATE CONSTRAINT TRIGGER trg_refund_payouts_integrity AFTER INSERT ON sale_refund_payouts
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_refund_integrity();
CREATE CONSTRAINT TRIGGER trg_debt_credits_integrity AFTER INSERT ON debt_credits
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_refund_integrity();

-- At commit: across all refunds, no sale line is refunded beyond what was sold, in quantity or in money
CREATE FUNCTION check_refund_line_caps() RETURNS trigger AS $$
DECLARE
    v_qty     NUMERIC;
    v_total   BIGINT;
    v_ref_qty NUMERIC;
    v_ref_amt BIGINT;
BEGIN
    SELECT quantity, line_total_minor INTO v_qty, v_total FROM sale_lines WHERE id = NEW.sale_line_id;
    SELECT SUM(quantity), SUM(line_refund_minor) INTO v_ref_qty, v_ref_amt
      FROM sale_refund_lines WHERE sale_line_id = NEW.sale_line_id;
    IF v_ref_qty > v_qty THEN
        RAISE EXCEPTION 'More of an item was refunded (%) than was sold (%)', v_ref_qty, v_qty;
    END IF;
    IF v_ref_amt > v_total THEN
        RAISE EXCEPTION 'More money was refunded (%) than the line was worth (%)', v_ref_amt, v_total;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_refund_line_caps AFTER INSERT ON sale_refund_lines
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_refund_line_caps();

-- Deni credits count in the debt balance: paid + written off + credited never exceeds the debt
CREATE OR REPLACE FUNCTION check_debt_balance() RETURNS trigger AS $$
DECLARE
    v_debt_id  UUID;
    v_original BIGINT;
    v_status   VARCHAR;
    v_paid     BIGINT;
    v_off      BIGINT;
    v_credit   BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'customer_debts' THEN
        v_debt_id := NEW.id;
    ELSE
        v_debt_id := NEW.debt_id;
    END IF;
    SELECT original_amount_minor, status INTO v_original, v_status FROM customer_debts WHERE id = v_debt_id;
    SELECT COALESCE(SUM(a.amount_minor), 0) INTO v_paid
      FROM debt_payment_allocations a
     WHERE a.debt_id = v_debt_id
       AND NOT EXISTS (SELECT 1 FROM debt_payment_reversals r WHERE r.payment_id = a.payment_id);
    SELECT COALESCE(SUM(amount_minor), 0) INTO v_off FROM debt_write_offs WHERE debt_id = v_debt_id;
    SELECT COALESCE(SUM(amount_minor), 0) INTO v_credit FROM debt_credits WHERE debt_id = v_debt_id;

    IF v_paid + v_off + v_credit > v_original THEN
        RAISE EXCEPTION 'More was paid, written off or credited (%) than the debt (%)',
                        v_paid + v_off + v_credit, v_original;
    END IF;
    IF v_status = 'VOID' AND v_paid + v_off + v_credit > 0 THEN
        RAISE EXCEPTION 'A debt with payments, write-offs or credits cannot be void';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_debt_credits_balance AFTER INSERT ON debt_credits
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_debt_balance();

-- The stock ledger learns about refunds
ALTER TABLE stock_movements ADD COLUMN refund_id UUID;
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_smv_refund FOREIGN KEY (refund_id) REFERENCES sale_refunds (id);

ALTER TABLE stock_movements DROP CONSTRAINT ck_smv_type;
ALTER TABLE stock_movements DROP CONSTRAINT ck_smv_sale_ref;
ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_type
    CHECK (movement_type IN ('OPENING', 'RECEIPT', 'ADJUSTMENT', 'SALE', 'SALE_REVERSAL', 'SALE_REFUND'));
ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_sale_ref
    CHECK ((movement_type IN ('SALE', 'SALE_REVERSAL', 'SALE_REFUND')) = (sale_id IS NOT NULL));
ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_refund_ref
    CHECK ((movement_type = 'SALE_REFUND') = (refund_id IS NOT NULL));
ALTER TABLE stock_movements ADD CONSTRAINT ck_smv_refund_dir
    CHECK (movement_type <> 'SALE_REFUND' OR quantity_delta > 0);