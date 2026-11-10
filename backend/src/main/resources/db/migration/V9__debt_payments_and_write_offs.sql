CREATE TABLE debt_payments (
                               id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                               business_id     UUID        NOT NULL,
                               shop_id         UUID        NOT NULL,
                               shop_day_id     UUID        NOT NULL,
                               customer_id     UUID        NOT NULL,
                               method          VARCHAR(10) NOT NULL,
                               amount_minor    BIGINT      NOT NULL,
                               received_by     UUID        NOT NULL,
                               idempotency_key UUID        NOT NULL,
                               request_hash    VARCHAR(64) NOT NULL,
                               created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                               CONSTRAINT fk_dp_shop     FOREIGN KEY (shop_id, business_id)     REFERENCES shops (id, business_id),
                               CONSTRAINT fk_dp_day      FOREIGN KEY (shop_day_id, shop_id)     REFERENCES shop_days (id, shop_id),
                               CONSTRAINT fk_dp_customer FOREIGN KEY (customer_id, business_id) REFERENCES customers (id, business_id),
                               CONSTRAINT fk_dp_user     FOREIGN KEY (received_by)              REFERENCES users (id),
                               CONSTRAINT uq_dp_idempotency UNIQUE (shop_id, idempotency_key),
                               CONSTRAINT uq_dp_id_customer UNIQUE (id, customer_id),
                               CONSTRAINT ck_dp_method CHECK (method IN ('CASH', 'POCHI', 'TILL')),
                               CONSTRAINT ck_dp_amount CHECK (amount_minor > 0)
);
CREATE INDEX ix_dp_day ON debt_payments (shop_day_id);
CREATE INDEX ix_dp_customer ON debt_payments (customer_id, created_at DESC);

CREATE TABLE debt_payment_allocations (
                                          id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                          payment_id   UUID        NOT NULL,
                                          debt_id      UUID        NOT NULL,
                                          customer_id  UUID        NOT NULL,
                                          amount_minor BIGINT      NOT NULL,
                                          created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- a payment can only touch debts of the customer who paid
                                          CONSTRAINT fk_dpa_payment FOREIGN KEY (payment_id, customer_id) REFERENCES debt_payments (id, customer_id),
                                          CONSTRAINT fk_dpa_debt    FOREIGN KEY (debt_id, customer_id)    REFERENCES customer_debts (id, customer_id),
                                          CONSTRAINT uq_dpa_payment_debt UNIQUE (payment_id, debt_id),
                                          CONSTRAINT ck_dpa_amount CHECK (amount_minor > 0)
);
CREATE INDEX ix_dpa_debt ON debt_payment_allocations (debt_id);
CREATE INDEX ix_dpa_customer ON debt_payment_allocations (customer_id);

CREATE TABLE debt_write_offs (
                                 id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                 business_id    UUID         NOT NULL,
                                 debt_id        UUID         NOT NULL,
                                 customer_id    UUID         NOT NULL,
                                 amount_minor   BIGINT       NOT NULL,
                                 reason         VARCHAR(200) NOT NULL,
                                 written_off_by UUID         NOT NULL,
                                 business_date  DATE         NOT NULL,
                                 created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 CONSTRAINT fk_dw_debt     FOREIGN KEY (debt_id, customer_id)     REFERENCES customer_debts (id, customer_id),
                                 CONSTRAINT fk_dw_customer FOREIGN KEY (customer_id, business_id) REFERENCES customers (id, business_id),
                                 CONSTRAINT fk_dw_user     FOREIGN KEY (written_off_by)           REFERENCES users (id),
                                 CONSTRAINT ck_dw_amount CHECK (amount_minor > 0),
                                 CONSTRAINT ck_dw_reason CHECK (char_length(btrim(reason)) >= 3)
);
CREATE INDEX ix_dw_debt ON debt_write_offs (debt_id);
CREATE INDEX ix_dw_customer ON debt_write_offs (customer_id, created_at DESC);

CREATE TRIGGER trg_debt_payments_append_only BEFORE UPDATE OR DELETE ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_debt_payment_allocations_append_only BEFORE UPDATE OR DELETE ON debt_payment_allocations
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_debt_write_offs_append_only BEFORE UPDATE OR DELETE ON debt_write_offs
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();

-- At commit, a payment is exactly the sum of its parts
CREATE FUNCTION check_payment_integrity() RETURNS trigger AS $$
DECLARE
    v_payment_id UUID;
    v_amount     BIGINT;
    v_allocated  BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'debt_payments' THEN
        v_payment_id := NEW.id;
    ELSE
        v_payment_id := NEW.payment_id;
    END IF;
    SELECT amount_minor INTO v_amount FROM debt_payments WHERE id = v_payment_id;
    SELECT COALESCE(SUM(amount_minor), 0) INTO v_allocated
    FROM debt_payment_allocations WHERE payment_id = v_payment_id;
    IF v_allocated <> v_amount THEN
        RAISE EXCEPTION 'Payment allocations (%) do not add up to the payment (%)', v_allocated, v_amount;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_payments_integrity AFTER INSERT ON debt_payments
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payment_integrity();
CREATE CONSTRAINT TRIGGER trg_payment_allocations_integrity AFTER INSERT ON debt_payment_allocations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payment_integrity();

-- At commit, nothing is paid or written off beyond the debt, and a debt with activity is never void
CREATE FUNCTION check_debt_balance() RETURNS trigger AS $$
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
    SELECT COALESCE(SUM(amount_minor), 0) INTO v_paid FROM debt_payment_allocations WHERE debt_id = v_debt_id;
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

CREATE CONSTRAINT TRIGGER trg_allocations_balance AFTER INSERT ON debt_payment_allocations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_debt_balance();
CREATE CONSTRAINT TRIGGER trg_write_offs_balance AFTER INSERT ON debt_write_offs
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_debt_balance();
CREATE CONSTRAINT TRIGGER trg_debts_balance AFTER UPDATE OF status ON customer_debts
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_debt_balance();