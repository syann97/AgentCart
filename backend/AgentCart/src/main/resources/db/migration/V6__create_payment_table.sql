CREATE TABLE payments
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT         NOT NULL UNIQUE,
    status      VARCHAR(20)    NOT NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    payment_key VARCHAR(100),
    paid_at     DATETIME(6),
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id)
);