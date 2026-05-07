CREATE TABLE IF NOT EXISTS sale_payments (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL,
    method VARCHAR(20) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_sale_payments_sale
        FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sale_payments_sale_id
    ON sale_payments(sale_id);
