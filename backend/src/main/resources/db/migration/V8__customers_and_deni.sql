CREATE TABLE customers (
                           id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                           business_id        UUID         NOT NULL,
                           name               VARCHAR(120) NOT NULL,
                           phone              VARCHAR(20)  NOT NULL,
                           credit_limit_minor BIGINT,
                           created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                           CONSTRAINT fk_customers_business FOREIGN KEY (business_id) REFERENCES businesses (id),
                           CONSTRAINT uq_customers_business_phone UNIQUE (business_id, phone),
                           CONSTRAINT uq_customers_id_business UNIQUE (id, business_id),
                           CONSTRAINT ck_customers_phone CHECK (phone ~ '^\+254[17][0-9]{8}$'),
                           CONSTRAINT ck_customers_name CHECK (char_length(btrim(name)) > 0),
                           CONSTRAINT ck_customers_limit CHECK (credit_limit_minor IS NULL OR credit_limit_minor >= 0)
);

CREATE TABLE customer_debts (
                                id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                business_id           UUID        NOT NULL,
                                shop_id               UUID        NOT NULL,
                                sale_id               UUID        NOT NULL,
                                customer_id           UUID        NOT NULL,
                                original_amount_minor BIGINT      NOT NULL,
                                due_date              DATE        NOT NULL,
                                status                VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
                                created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
                                CONSTRAINT fk_debts_sale     FOREIGN KEY (sale_id, shop_id)         REFERENCES sales (id, shop_id),
                                CONSTRAINT fk_debts_customer FOREIGN KEY (customer_id, business_id) REFERENCES customers (id, business_id),
                                CONSTRAINT fk_debts_shop     FOREIGN KEY (shop_id, business_id)     REFERENCES shops (id, business_id),
                                CONSTRAINT uq_debts_sale UNIQUE (sale_id),
    -- target for repayment allocations in Step 16: a payment can only touch its own customer's debts
                                CONSTRAINT uq_debts_id_customer UNIQUE (id, customer_id),
                                CONSTRAINT ck_debts_amount CHECK (original_amount_minor > 0),
                                CONSTRAINT ck_debts_status CHECK (status IN ('ACTIVE', 'VOID'))
);
CREATE INDEX ix_debts_customer ON customer_debts (customer_id, status);
CREATE INDEX ix_debts_due ON customer_debts (business_id, due_date) WHERE status = 'ACTIVE';

-- Debts are never deleted or rewritten. The only change allowed is ACTIVE -> VOID.
CREATE FUNCTION protect_debts() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'customer_debts cannot be deleted';
    END IF;
    IF (NEW.id, NEW.business_id, NEW.shop_id, NEW.sale_id, NEW.customer_id,
        NEW.original_amount_minor, NEW.due_date, NEW.created_at)
        IS DISTINCT FROM
        (OLD.id, OLD.business_id, OLD.shop_id, OLD.sale_id, OLD.customer_id,
         OLD.original_amount_minor, OLD.due_date, OLD.created_at) THEN
        RAISE EXCEPTION 'customer_debts are append-only: only the status may change';
    END IF;
    IF OLD.status = 'VOID' AND NEW.status <> 'VOID' THEN
        RAISE EXCEPTION 'a void debt cannot be restored';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_debts_protect BEFORE UPDATE OR DELETE ON customer_debts
    FOR EACH ROW EXECUTE FUNCTION protect_debts();

-- At commit: the deni payment equals the debt, and a debt is void exactly when its sale is reversed
CREATE FUNCTION check_debt_integrity() RETURNS trigger AS $$
DECLARE
    v_sale_id     UUID;
    v_sale_status VARCHAR;
    v_deni        BIGINT;
    v_debt        BIGINT;
    v_debts       BIGINT;
    v_void        BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'sales' THEN
        v_sale_id := NEW.id;
    ELSE
        v_sale_id := NEW.sale_id;
    END IF;

    SELECT status INTO v_sale_status FROM sales WHERE id = v_sale_id;
    SELECT COALESCE(SUM(amount_minor), 0) INTO v_deni
    FROM sale_allocations WHERE sale_id = v_sale_id AND method = 'DENI';
    SELECT COALESCE(SUM(original_amount_minor), 0), COUNT(*), COUNT(*) FILTER (WHERE status = 'VOID')
    INTO v_debt, v_debts, v_void
    FROM customer_debts WHERE sale_id = v_sale_id;

    IF v_deni <> v_debt THEN
        RAISE EXCEPTION 'The deni payment (%) does not match the customer debt (%)', v_deni, v_debt;
    END IF;
    IF v_debts > 0 AND ((v_sale_status = 'REVERSED') <> (v_void = v_debts)) THEN
        RAISE EXCEPTION 'A debt must be void exactly when its sale is reversed';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_sales_debt_integrity AFTER INSERT OR UPDATE OF status ON sales
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_debt_integrity();
CREATE CONSTRAINT TRIGGER trg_allocations_debt_integrity AFTER INSERT ON sale_allocations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_debt_integrity();
CREATE CONSTRAINT TRIGGER trg_debts_integrity AFTER INSERT OR UPDATE OF status ON customer_debts
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_debt_integrity();