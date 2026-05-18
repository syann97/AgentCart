CREATE TABLE orders
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id        BIGINT         NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    total_price      DECIMAL(12, 2) NOT NULL,
    recipient_name   VARCHAR(100)   NOT NULL,
    phone            VARCHAR(20)    NOT NULL,
    address          VARCHAR(255)   NOT NULL,
    address_detail   VARCHAR(255),
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    CONSTRAINT fk_order_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE TABLE order_items
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT         NOT NULL,
    product_id      BIGINT         NOT NULL,
    product_name    VARCHAR(255)   NOT NULL,
    price_at_order  DECIMAL(12, 2) NOT NULL,
    quantity        INT            NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    CONSTRAINT fk_order_item_order   FOREIGN KEY (order_id)   REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products (id)
);
