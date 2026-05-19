CREATE TABLE recommendation_history
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id      BIGINT       NOT NULL,
    query          VARCHAR(500),
    product_id     BIGINT       NOT NULL,
    product_name   VARCHAR(255) NOT NULL,
    reason         TEXT,
    score          DOUBLE       NOT NULL,
    recommended_at DATETIME     NOT NULL,
    INDEX idx_member_at (member_id, recommended_at)
);
