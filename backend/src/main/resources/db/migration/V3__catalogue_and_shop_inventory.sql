CREATE TABLE items (
                       id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       business_id UUID         NOT NULL,
                       name        VARCHAR(120) NOT NULL,
                       unit        VARCHAR(10)  NOT NULL,
                       pack_name   VARCHAR(40),
                       pack_size   NUMERIC(14,3),
                       status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       CONSTRAINT fk_items_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    -- target for composite FKs from shop_inventory (and later tables)
                       CONSTRAINT uq_items_id_business UNIQUE (id, business_id),
                       CONSTRAINT ck_items_unit CHECK (unit IN ('PIECE', 'KG', 'LITRE')),
                       CONSTRAINT ck_items_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    -- a pack needs both a name and a positive size, or neither
                       CONSTRAINT ck_items_pack CHECK (
                           (pack_name IS NULL AND pack_size IS NULL)
                               OR (pack_name IS NOT NULL AND pack_size IS NOT NULL AND pack_size > 0))
);
-- "Sugar" and "sugar" are the same item within one business
CREATE UNIQUE INDEX uq_items_business_name ON items (business_id, lower(name));

CREATE TABLE shop_inventory (
                                id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                shop_id       UUID          NOT NULL,
                                business_id   UUID          NOT NULL,
                                item_id       UUID          NOT NULL,
                                price_minor   BIGINT,
                                on_hand       NUMERIC(14,3) NOT NULL DEFAULT 0,
                                average_cost  NUMERIC(18,4) NOT NULL DEFAULT 0,
                                reorder_level NUMERIC(14,3) NOT NULL DEFAULT 0,
                                created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- both links carry business_id, so a shop and an item from different businesses cannot be joined
                                CONSTRAINT fk_si_shop FOREIGN KEY (shop_id, business_id) REFERENCES shops (id, business_id),
                                CONSTRAINT fk_si_item FOREIGN KEY (item_id, business_id) REFERENCES items (id, business_id),
                                CONSTRAINT uq_si_shop_item UNIQUE (shop_id, item_id),
                                CONSTRAINT ck_si_price CHECK (price_minor IS NULL OR price_minor >= 0),
                                CONSTRAINT ck_si_on_hand CHECK (on_hand >= 0),
                                CONSTRAINT ck_si_average_cost CHECK (average_cost >= 0),
                                CONSTRAINT ck_si_reorder CHECK (reorder_level >= 0)
);
CREATE INDEX ix_si_item ON shop_inventory (item_id);