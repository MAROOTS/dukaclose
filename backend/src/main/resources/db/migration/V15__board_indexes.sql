CREATE INDEX ix_dc_business_date ON day_closes (business_id, business_date DESC);
CREATE INDEX ix_sd_business_open ON shop_days (business_id) WHERE status = 'OPEN';
CREATE INDEX ix_si_low_stock ON shop_inventory (business_id) WHERE on_hand <= reorder_level;