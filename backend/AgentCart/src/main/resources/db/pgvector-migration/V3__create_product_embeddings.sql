CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE product_embeddings
(
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT       NOT NULL UNIQUE,
    embedding  vector(1536) NOT NULL,
    model      VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_embeddings_product_id
    ON product_embeddings (product_id);

CREATE INDEX idx_product_embeddings_ivfflat
    ON product_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);