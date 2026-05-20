-- bge-m3 모델 전환: 1536차원(text-embedding-3-small) → 1024차원
-- 기존 임베딩은 모델 불일치로 무의미하므로 함께 제거
DROP INDEX IF EXISTS idx_product_embeddings_ivfflat;
TRUNCATE TABLE product_embeddings;
ALTER TABLE product_embeddings ALTER COLUMN embedding TYPE vector(1024);
CREATE INDEX idx_product_embeddings_ivfflat
    ON product_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);