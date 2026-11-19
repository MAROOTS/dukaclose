CREATE TABLE cash_counts (
                             id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                             business_id     UUID         NOT NULL,
                             shop_id         UUID         NOT NULL,
                             shop_day_id     UUID         NOT NULL,
                             counted_minor   BIGINT       NOT NULL,
                             expected_minor  BIGINT       NOT NULL,
                             activity_token  VARCHAR(64)  NOT NULL,
                             counted_by      UUID         NOT NULL,
                             idempotency_key UUID         NOT NULL,
                             request_hash    VARCHAR(64)  NOT NULL,
                             created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                             CONSTRAINT fk_cc_shop FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
                             CONSTRAINT fk_cc_day  FOREIGN KEY (shop_day_id, shop_id) REFERENCES shop_days (id, shop_id),
                             CONSTRAINT fk_cc_user FOREIGN KEY (counted_by) REFERENCES users (id),
                             CONSTRAINT uq_cc_idempotency UNIQUE (shop_id, idempotency_key),
    -- the close (Step 20) will point at the final count and must be on the same day
                             CONSTRAINT uq_cc_id_day UNIQUE (id, shop_day_id),
                             CONSTRAINT ck_cc_counted CHECK (counted_minor >= 0),
                             CONSTRAINT ck_cc_token CHECK (char_length(activity_token) = 64)
);
CREATE INDEX ix_cc_day ON cash_counts (shop_day_id, created_at);

-- A count is never edited or deleted. A recount is a new row.
CREATE TRIGGER trg_cash_counts_append_only BEFORE UPDATE OR DELETE ON cash_counts
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();