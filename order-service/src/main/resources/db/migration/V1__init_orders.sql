CREATE TABLE orders (
                        id              UUID PRIMARY KEY,
                        product_id      VARCHAR(64) NOT NULL,
                        quantity        INTEGER NOT NULL CHECK (quantity > 0),
                        status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                        failure_reason  VARCHAR(255),
                        created_at      TIMESTAMP NOT NULL DEFAULT now(),
                        updated_at      TIMESTAMP NOT NULL DEFAULT now()
);
