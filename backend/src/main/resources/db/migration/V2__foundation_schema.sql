CREATE TABLE businesses (
                            id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                            name       VARCHAR(150) NOT NULL,
                            status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                            created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
                            CONSTRAINT ck_businesses_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE users (
                       id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       display_name     VARCHAR(120) NOT NULL,
                       login_identifier VARCHAR(40)  NOT NULL,
                       password_hash    VARCHAR(255) NOT NULL,
                       status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       CONSTRAINT uq_users_login_identifier UNIQUE (login_identifier),
                       CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE shops (
                       id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       business_id UUID         NOT NULL,
                       name        VARCHAR(120) NOT NULL,
                       status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       CONSTRAINT fk_shops_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    -- also serves lookups by business_id (leading column)
                       CONSTRAINT uq_shops_business_name UNIQUE (business_id, name),
    -- target for composite FKs (shop_id, business_id) in later tables
                       CONSTRAINT uq_shops_id_business UNIQUE (id, business_id),
                       CONSTRAINT ck_shops_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE business_memberships (
                                      id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                      business_id UUID        NOT NULL,
                                      user_id     UUID        NOT NULL,
                                      role        VARCHAR(20) NOT NULL,
                                      status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                      created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                                      CONSTRAINT fk_bm_business FOREIGN KEY (business_id) REFERENCES businesses (id),
                                      CONSTRAINT fk_bm_user FOREIGN KEY (user_id) REFERENCES users (id),
                                      CONSTRAINT uq_bm_business_user UNIQUE (business_id, user_id),
                                      CONSTRAINT ck_bm_role CHECK (role IN ('OWNER')),
                                      CONSTRAINT ck_bm_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_bm_user ON business_memberships (user_id);

CREATE TABLE shop_memberships (
                                  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                  shop_id    UUID        NOT NULL,
                                  user_id    UUID        NOT NULL,
                                  role       VARCHAR(20) NOT NULL DEFAULT 'CASHIER',
                                  status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  CONSTRAINT fk_sm_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
                                  CONSTRAINT fk_sm_user FOREIGN KEY (user_id) REFERENCES users (id),
                                  CONSTRAINT uq_sm_shop_user UNIQUE (shop_id, user_id),
                                  CONSTRAINT ck_sm_role CHECK (role IN ('CASHIER')),
                                  CONSTRAINT ck_sm_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_sm_user ON shop_memberships (user_id);