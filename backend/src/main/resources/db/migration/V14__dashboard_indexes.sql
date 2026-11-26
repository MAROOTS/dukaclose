-- Write-offs are looked up by the day the owner recorded them. Every other column the dashboard filters on
-- (shop_days.shop_id + business_date, shop_day_id on sales, payments, refunds and money entries) is already indexed.
CREATE INDEX ix_dw_date ON debt_write_offs (business_date);