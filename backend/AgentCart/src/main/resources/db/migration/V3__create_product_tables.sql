CREATE TABLE products
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       DECIMAL(12, 2) NOT NULL,
    category    VARCHAR(100)   NOT NULL,
    brand       VARCHAR(100),
    stock       INT            NOT NULL DEFAULT 0,
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6),
    INDEX idx_products_category (category),
    INDEX idx_products_status (status),
    INDEX idx_products_brand (brand)
);