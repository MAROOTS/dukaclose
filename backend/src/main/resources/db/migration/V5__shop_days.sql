CREATE TABLE shop_days (
                           id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                           shop_id             UUID        NOT NULL,
                           business_id         UUID        NOT NULL,
                           business_date       DATE        NOT NULL,
                           status              VARCHAR(10) NOT NULL DEFAULT 'OPEN',
                           opening_float_minor BIGINT      NOT NULL,
                           opened_by           UUID        NOT NULL,
                           created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                           CONSTRAINT fk_days_shop FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
                           CONSTRAINT fk_days_user FOREIGN KEY (opened_by) REFERENCES users (id),
                           CONSTRAINT uq_days_shop_date UNIQUE (shop_id, business_date),
    -- target for composite FKs from sales, money movements and stock movements
                           CONSTRAINT uq_days_id_shop UNIQUE (id, shop_id),
                           CONSTRAINT ck_days_status CHECK (status IN ('OPEN', 'CLOSED')),
                           CONSTRAINT ck_days_float CHECK (opening_float_minor >= 0)
);

-- at most one OPEN day per shop
CREATE UNIQUE INDEX uq_days_one_open ON shop_days (shop_id) WHERE status = 'OPEN';

-- stock movements remember which day they happened on (null when no day was open)
ALTER TABLE stock_movements ADD COLUMN shop_day_id UUID;
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_smv_day FOREIGN KEY (shop_day_id, shop_id) REFERENCES shop_days (id, shop_id);
CREATE INDEX ix_smv_day ON stock_movements (shop_day_id) WHERE shop_day_id IS NOT NULL;