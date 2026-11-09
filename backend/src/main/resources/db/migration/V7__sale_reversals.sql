CREATE TABLE sale_reversals (
                                id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                sale_id     UUID         NOT NULL,
                                shop_id     UUID         NOT NULL,
                                business_id UUID         NOT NULL,
                                shop_day_id UUID         NOT NULL,
                                reason      VARCHAR(200) NOT NULL,
                                reversed_by UUID         NOT NULL,
                                created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                CONSTRAINT fk_sr_sale FOREIGN KEY (sale_id, shop_id)         REFERENCES sales (id, shop_id),
                                CONSTRAINT fk_sr_day  FOREIGN KEY (shop_day_id, shop_id)     REFERENCES shop_days (id, shop_id),
                                CONSTRAINT fk_sr_shop FOREIGN KEY (shop_id, business_id)     REFERENCES shops (id, business_id),
                                CONSTRAINT fk_sr_user FOREIGN KEY (reversed_by)              REFERENCES users (id),
                                CONSTRAINT uq_sr_sale UNIQUE (sale_id),
                                CONSTRAINT ck_sr_reason CHECK (char_length(btrim(reason)) >= 3)
);

CREATE TRIGGER trg_sale_reversals_append_only BEFORE UPDATE OR DELETE ON sale_reversals
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- A sale is REVERSED if and only if it has a reversal record, checked when the transaction commits
CREATE FUNCTION check_reversal_integrity() RETURNS trigger AS $$
DECLARE
    v_sale_id UUID;
    v_status  VARCHAR;
    v_has     BOOLEAN;
BEGIN
    IF TG_TABLE_NAME = 'sales' THEN
        v_sale_id := NEW.id;
    ELSE
        v_sale_id := NEW.sale_id;
    END IF;

    SELECT status INTO v_status FROM sales WHERE id = v_sale_id;
    SELECT EXISTS (SELECT 1 FROM sale_reversals WHERE sale_id = v_sale_id) INTO v_has;

    IF v_status = 'REVERSED' AND NOT v_has THEN
        RAISE EXCEPTION 'A reversed sale needs a reversal record';
    END IF;
    IF v_status <> 'REVERSED' AND v_has THEN
        RAISE EXCEPTION 'A reversal record exists for a sale that is not reversed';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_sales_reversal_integrity AFTER UPDATE OF status ON sales
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_reversal_integrity();
CREATE CONSTRAINT TRIGGER trg_reversals_integrity AFTER INSERT ON sale_reversals
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_reversal_integrity();