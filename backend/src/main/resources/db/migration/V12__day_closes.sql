-- ---------- shop days: carry-over, and protection against rewriting history ----------
ALTER TABLE shop_days ADD COLUMN carried_over_minor BIGINT;
ALTER TABLE shop_days ADD CONSTRAINT ck_days_carried CHECK (carried_over_minor IS NULL OR carried_over_minor >= 0);
ALTER TABLE shop_days ADD CONSTRAINT uq_days_id_date UNIQUE (id, business_date);

-- Only the status may change, and a closed day never opens again
CREATE FUNCTION protect_shop_days() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'shop_days cannot be deleted';
    END IF;
    IF (NEW.id, NEW.shop_id, NEW.business_id, NEW.business_date, NEW.opening_float_minor,
        NEW.opened_by, NEW.carried_over_minor, NEW.created_at)
       IS DISTINCT FROM
       (OLD.id, OLD.shop_id, OLD.business_id, OLD.business_date, OLD.opening_float_minor,
        OLD.opened_by, OLD.carried_over_minor, OLD.created_at) THEN
        RAISE EXCEPTION 'shop_days are append-only: only the status may change';
    END IF;
    IF OLD.status = 'CLOSED' AND NEW.status <> 'CLOSED' THEN
        RAISE EXCEPTION 'a closed day cannot be reopened';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_shop_days_protect BEFORE UPDATE OR DELETE ON shop_days
    FOR EACH ROW EXECUTE FUNCTION protect_shop_days();

-- ---------- the close ----------
CREATE TABLE day_closes (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id       UUID         NOT NULL,
    shop_id           UUID         NOT NULL,
    shop_day_id       UUID         NOT NULL,
    business_date     DATE         NOT NULL,
    cash_count_id     UUID         NOT NULL,
    closed_by         UUID         NOT NULL,
    counted_minor     BIGINT       NOT NULL,
    expected_minor    BIGINT       NOT NULL,
    difference_minor  BIGINT       NOT NULL,
    sales_total_minor BIGINT       NOT NULL,
    note              VARCHAR(200),
    activity_token    VARCHAR(64)  NOT NULL,
    snapshot          JSONB        NOT NULL,
    idempotency_key   UUID         NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    closed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_dc_shop      FOREIGN KEY (shop_id, business_id)     REFERENCES shops (id, business_id),
    CONSTRAINT fk_dc_day       FOREIGN KEY (shop_day_id, shop_id)     REFERENCES shop_days (id, shop_id),
    -- the stored date must be the day's own date
    CONSTRAINT fk_dc_day_date  FOREIGN KEY (shop_day_id, business_date) REFERENCES shop_days (id, business_date),
    -- the count the close is based on must be a count of this same day
    CONSTRAINT fk_dc_count     FOREIGN KEY (cash_count_id, shop_day_id) REFERENCES cash_counts (id, shop_day_id),
    CONSTRAINT fk_dc_user      FOREIGN KEY (closed_by) REFERENCES users (id),
    CONSTRAINT uq_dc_day UNIQUE (shop_day_id),
    CONSTRAINT uq_dc_idempotency UNIQUE (shop_id, idempotency_key),
    CONSTRAINT ck_dc_counted CHECK (counted_minor >= 0),
    CONSTRAINT ck_dc_difference CHECK (difference_minor = counted_minor - expected_minor),
    -- a drawer that is short or over must be explained
    CONSTRAINT ck_dc_note CHECK (difference_minor = 0 OR char_length(btrim(COALESCE(note, ''))) >= 3),
    CONSTRAINT ck_dc_token CHECK (char_length(activity_token) = 64),
    CONSTRAINT ck_dc_snapshot CHECK (jsonb_typeof(snapshot) = 'object')
);
CREATE INDEX ix_dc_shop_date ON day_closes (shop_id, business_date DESC);

CREATE TRIGGER trg_day_closes_append_only BEFORE UPDATE OR DELETE ON day_closes
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- At commit, a close record only exists for a day that really is closed
CREATE FUNCTION check_close_integrity() RETURNS trigger AS $$
DECLARE
    v_status VARCHAR;
BEGIN
    SELECT status INTO v_status FROM shop_days WHERE id = NEW.shop_day_id;
    IF v_status IS DISTINCT FROM 'CLOSED' THEN
        RAISE EXCEPTION 'A close record exists for a day that is not closed';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_day_closes_integrity AFTER INSERT ON day_closes
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_close_integrity();

-- ---------- a closed day takes no new records ----------
-- These are AFTER triggers named trg_zz_*, so a foreign-key error (RI_ConstraintTrigger_*) is always reported first.
CREATE FUNCTION forbid_write_to_closed_day() RETURNS trigger AS $$
DECLARE
    v_status VARCHAR;
BEGIN
    IF NEW.shop_day_id IS NULL THEN
        RETURN NULL;                         -- e.g. a stock delivery when no day was open
    END IF;
    SELECT status INTO v_status FROM shop_days WHERE id = NEW.shop_day_id;
    IF v_status IS DISTINCT FROM 'OPEN' THEN
        RAISE EXCEPTION 'The shop day is closed: no new records can be added to it';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_zz_sales_day_open AFTER INSERT ON sales
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();
CREATE TRIGGER trg_zz_sale_reversals_day_open AFTER INSERT ON sale_reversals
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();
CREATE TRIGGER trg_zz_debt_payments_day_open AFTER INSERT ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();
CREATE TRIGGER trg_zz_debt_payment_reversals_day_open AFTER INSERT ON debt_payment_reversals
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();
CREATE TRIGGER trg_zz_money_movements_day_open AFTER INSERT ON money_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();
CREATE TRIGGER trg_zz_money_reversals_day_open AFTER INSERT ON money_movement_reversals
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();
CREATE TRIGGER trg_zz_cash_counts_day_open AFTER INSERT ON cash_counts
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();
CREATE TRIGGER trg_zz_stock_movements_day_open AFTER INSERT ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_write_to_closed_day();