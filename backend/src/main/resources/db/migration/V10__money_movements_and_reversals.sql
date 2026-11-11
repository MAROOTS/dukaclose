-- ---------- money in and out ----------
CREATE TABLE money_movements (
                                 id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                 seq             BIGINT       GENERATED ALWAYS AS IDENTITY,
                                 business_id     UUID         NOT NULL,
                                 shop_id         UUID         NOT NULL,
                                 shop_day_id     UUID         NOT NULL,
                                 direction       VARCHAR(3)   NOT NULL,
                                 category        VARCHAR(20)  NOT NULL,
                                 method          VARCHAR(10)  NOT NULL,
                                 amount_minor    BIGINT       NOT NULL,
                                 note            VARCHAR(200) NOT NULL,
                                 created_by      UUID         NOT NULL,
                                 idempotency_key UUID         NOT NULL,
                                 request_hash    VARCHAR(64)  NOT NULL,
                                 created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 CONSTRAINT fk_mm_shop FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
                                 CONSTRAINT fk_mm_day  FOREIGN KEY (shop_day_id, shop_id) REFERENCES shop_days (id, shop_id),
                                 CONSTRAINT fk_mm_user FOREIGN KEY (created_by) REFERENCES users (id),
                                 CONSTRAINT uq_mm_idempotency UNIQUE (shop_id, idempotency_key),
                                 CONSTRAINT uq_mm_id_day UNIQUE (id, shop_day_id),
                                 CONSTRAINT ck_mm_direction CHECK (direction IN ('IN', 'OUT')),
                                 CONSTRAINT ck_mm_method CHECK (method IN ('CASH', 'POCHI', 'TILL')),
                                 CONSTRAINT ck_mm_amount CHECK (amount_minor > 0),
                                 CONSTRAINT ck_mm_note CHECK (char_length(btrim(note)) >= 3),
                                 CONSTRAINT ck_mm_category CHECK (
                                     (direction = 'OUT' AND category IN ('STOCK_PURCHASE', 'TRANSPORT', 'LUNCH', 'FLOAT_MOVEMENT', 'OTHER_EXPENSE'))
                                         OR (direction = 'IN' AND category = 'OTHER_CASH_RECEIVED')),
    -- money placed into the drawer is cash, and a float movement is cash leaving the drawer
                                 CONSTRAINT ck_mm_in_is_cash CHECK (direction = 'OUT' OR method = 'CASH'),
                                 CONSTRAINT ck_mm_float_is_cash CHECK (category <> 'FLOAT_MOVEMENT' OR method = 'CASH')
);
CREATE INDEX ix_mm_day ON money_movements (shop_day_id, seq DESC);

CREATE TABLE money_movement_reversals (
                                          id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                          movement_id UUID         NOT NULL,
                                          shop_day_id UUID         NOT NULL,
                                          shop_id     UUID         NOT NULL,
                                          reason      VARCHAR(200) NOT NULL,
                                          reversed_by UUID         NOT NULL,
                                          created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- the reversal must be on the same day as the entry it cancels
                                          CONSTRAINT fk_mmr_movement FOREIGN KEY (movement_id, shop_day_id) REFERENCES money_movements (id, shop_day_id),
                                          CONSTRAINT fk_mmr_day  FOREIGN KEY (shop_day_id, shop_id) REFERENCES shop_days (id, shop_id),
                                          CONSTRAINT fk_mmr_user FOREIGN KEY (reversed_by) REFERENCES users (id),
                                          CONSTRAINT uq_mmr_movement UNIQUE (movement_id),
                                          CONSTRAINT ck_mmr_reason CHECK (char_length(btrim(reason)) >= 3)
);

-- ---------- reversing a repayment ----------
ALTER TABLE debt_payments ADD CONSTRAINT uq_dp_id_day UNIQUE (id, shop_day_id);

CREATE TABLE debt_payment_reversals (
                                        id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                        payment_id  UUID         NOT NULL,
                                        shop_day_id UUID         NOT NULL,
                                        shop_id     UUID         NOT NULL,
                                        reason      VARCHAR(200) NOT NULL,
                                        reversed_by UUID         NOT NULL,
                                        created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                        CONSTRAINT fk_dpr_payment FOREIGN KEY (payment_id, shop_day_id) REFERENCES debt_payments (id, shop_day_id),
                                        CONSTRAINT fk_dpr_day  FOREIGN KEY (shop_day_id, shop_id) REFERENCES shop_days (id, shop_id),
                                        CONSTRAINT fk_dpr_user FOREIGN KEY (reversed_by) REFERENCES users (id),
                                        CONSTRAINT uq_dpr_payment UNIQUE (payment_id),
                                        CONSTRAINT ck_dpr_reason CHECK (char_length(btrim(reason)) >= 3)
);

CREATE TRIGGER trg_money_movements_append_only BEFORE UPDATE OR DELETE ON money_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_money_reversals_append_only BEFORE UPDATE OR DELETE ON money_movement_reversals
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_payment_reversals_append_only BEFORE UPDATE OR DELETE ON debt_payment_reversals
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- A reversed repayment never happened, so it no longer counts towards what was paid
CREATE OR REPLACE FUNCTION check_debt_balance() RETURNS trigger AS $$
DECLARE
    v_debt_id  UUID;
    v_original BIGINT;
    v_status   VARCHAR;
    v_paid     BIGINT;
    v_off      BIGINT;
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

    IF v_paid + v_off > v_original THEN
        RAISE EXCEPTION 'More was paid or written off (%) than the debt (%)', v_paid + v_off, v_original;
    END IF;
    IF v_status = 'VOID' AND v_paid + v_off > 0 THEN
        RAISE EXCEPTION 'A debt with payments or write-offs cannot be void';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;