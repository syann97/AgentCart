#!/usr/bin/env python3
"""
AgentCart Batch Embedding Generator
MySQL products → OpenAI text-embedding-3-small → pgvector product_embeddings

실행:  python scripts/generate_embeddings.py
전제:  MySQL(agentcart-mysql), pgvector(agentcart-pgvector) 컨테이너 실행 중
       .env 파일에 OPENAI_API_KEY 존재
"""

import os
import re
import sys
import time

import psycopg
import pymysql
import requests

# ──────────────────────────────────────────────
# 설정
# ──────────────────────────────────────────────
ENV_FILE = os.path.join(os.path.dirname(__file__), "..", "backend", "AgentCart", ".env")

OPENAI_EMBED_URL = "https://api.openai.com/v1/embeddings"
EMBED_MODEL = "text-embedding-3-small"
BATCH_SIZE = 20          # OpenAI 한 번에 처리할 텍스트 수
RETRY_DELAY = 2          # 재시도 대기(초)
MAX_RETRIES = 3


def load_env(path: str) -> dict:
    env = {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    return env


# ──────────────────────────────────────────────
# 1. MySQL에서 모든 상품 로드
# ──────────────────────────────────────────────
def load_products():
    conn = pymysql.connect(
        host="localhost", port=3307,
        user="root", password="root",
        database="agentcart",
        charset="utf8mb4",
    )
    with conn:
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            cur.execute("SELECT id, name, description, category, brand FROM products ORDER BY id")
            return cur.fetchall()


# ──────────────────────────────────────────────
# 2. 임베딩 텍스트 생성
# ──────────────────────────────────────────────
def build_text(p: dict) -> str:
    parts = [p["name"] or "", p["description"] or "", p["category"] or "", p["brand"] or ""]
    return " ".join(x for x in parts if x).strip()


# ──────────────────────────────────────────────
# 3. OpenAI 배치 임베딩 요청
# ──────────────────────────────────────────────
def embed_batch(texts, api_key):
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            resp = requests.post(
                OPENAI_EMBED_URL,
                headers={"Authorization": f"Bearer {api_key}"},
                json={"input": texts, "model": EMBED_MODEL},
                timeout=30,
            )
            resp.raise_for_status()
            data = resp.json()["data"]
            data.sort(key=lambda x: x["index"])
            return [item["embedding"] for item in data]
        except Exception as e:
            if attempt == MAX_RETRIES:
                raise
            print(f"    [재시도 {attempt}/{MAX_RETRIES}] {e}")
            time.sleep(RETRY_DELAY)


# ──────────────────────────────────────────────
# 4. pgvector upsert
# ──────────────────────────────────────────────
def upsert_embeddings(rows, pg_conn):
    with pg_conn.cursor() as cur:
        for product_id, embedding in rows:
            vec_str = "[" + ",".join(str(x) for x in embedding) + "]"
            cur.execute(
                """
                INSERT INTO product_embeddings (product_id, embedding, model, created_at)
                VALUES (%s, %s::vector, %s, NOW())
                ON CONFLICT (product_id) DO UPDATE
                SET embedding = EXCLUDED.embedding, model = EXCLUDED.model
                """,
                (product_id, vec_str, EMBED_MODEL),
            )
    pg_conn.commit()


# ──────────────────────────────────────────────
# main
# ──────────────────────────────────────────────
def main():
    print("=" * 60)
    print("  AgentCart Batch Embedding Generator")
    print("=" * 60)

    # .env 로드
    env = load_env(ENV_FILE)
    api_key = env.get("OPENAI_API_KEY")
    if not api_key:
        print("[!] OPENAI_API_KEY가 .env에 없습니다.", file=sys.stderr)
        sys.exit(1)

    # MySQL → 상품 목록
    print("\n[1] MySQL에서 상품 로드...")
    products = load_products()
    print(f"  [+] {len(products)}개 상품 로드 완료")

    # pgvector에서 이미 임베딩된 product_id 확인
    print("\n[2] pgvector 기존 임베딩 확인...")
    pg_conn = psycopg.connect(
        host="localhost", port=5432,
        dbname="agentcart_vector",
        user="user", password="password",
    )

    with pg_conn.cursor() as cur:
        cur.execute("SELECT product_id FROM product_embeddings")
        already_done = {row[0] for row in cur.fetchall()}
    print(f"  [+] 기존 임베딩: {len(already_done)}개 (스킵됨)")

    pending = [p for p in products if p["id"] not in already_done]
    print(f"  [+] 신규 임베딩 필요: {len(pending)}개")

    if not pending:
        print("\n모든 상품에 임베딩이 이미 존재합니다.")
        pg_conn.close()
        return

    # 배치 임베딩 + pgvector 저장
    print(f"\n[3] OpenAI {EMBED_MODEL} 배치 임베딩 (배치 크기: {BATCH_SIZE})...")
    total = len(pending)
    success, failed = 0, []

    for batch_start in range(0, total, BATCH_SIZE):
        batch = pending[batch_start: batch_start + BATCH_SIZE]
        texts = [build_text(p) for p in batch]
        end_idx = min(batch_start + BATCH_SIZE, total)

        try:
            embeddings = embed_batch(texts, api_key)
            rows = [(p["id"], emb) for p, emb in zip(batch, embeddings)]
            upsert_embeddings(rows, pg_conn)
            success += len(batch)
            print(f"  [{end_idx:3}/{total}] ✓ 배치 처리 완료 (ID {batch[0]['id']}~{batch[-1]['id']})")
        except Exception as e:
            failed.extend(p["id"] for p in batch)
            print(f"  [{end_idx:3}/{total}] ✗ 배치 실패 → {e}", file=sys.stderr)

    pg_conn.close()

    # 결과
    print(f"\n{'='*60}")
    print(f"  완료: {success}개 성공 / {len(failed)}개 실패 / 전체 {total}개")
    if failed:
        print(f"  실패 product_id: {failed}")

    # 최종 검증
    pg_verify = psycopg.connect(
        host="localhost", port=5432,
        dbname="agentcart_vector",
        user="user", password="password",
    )
    with pg_verify.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM product_embeddings")
        count = cur.fetchone()[0]
    pg_verify.close()
    print(f"\n  pgvector product_embeddings 최종 행 수: {count}")


if __name__ == "__main__":
    main()