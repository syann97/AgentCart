#!/usr/bin/env python3
"""
AgentCart Product Seed Script (JSON 파일 기반)
LLM 합성 한국어 상품 JSON → POST /api/products (ADMIN 인증 포함)

실행:  python scripts/seed_products_from_json.py scripts/data/products_pilot.json
전제:  백엔드(localhost:8080) + MySQL 컨테이너(agentcart-mysql) 실행 중
"""

import json
import sys

import requests

BASE_URL = "http://localhost:8080/api"

ADMIN_EMAIL = "admin@agentcart.com"
ADMIN_PASSWORD = "Admin1234!"


# ──────────────────────────────────────────────
# 1. 백엔드 헬스 체크
# ──────────────────────────────────────────────
def check_backend():
    try:
        r = requests.get(f"{BASE_URL}/products", timeout=5)
        return r.status_code in (200, 401, 403)
    except Exception:
        return False


# ──────────────────────────────────────────────
# 2. 로그인 → access token
# ──────────────────────────────────────────────
def login() -> str:
    r = requests.post(
        f"{BASE_URL}/auth/login",
        json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD},
        timeout=10,
    )
    r.raise_for_status()
    token = r.json()["data"]["accessToken"]
    print(f"  [+] 로그인 성공 → token 발급")
    return token


# ──────────────────────────────────────────────
# 3. /api/products POST
# ──────────────────────────────────────────────
def seed_products(products: list, token: str):
    headers = {"Authorization": f"Bearer {token}"}
    total = len(products)
    success, failed = 0, []

    for i, payload in enumerate(products, 1):
        try:
            r = requests.post(
                f"{BASE_URL}/products",
                json=payload,
                headers=headers,
                timeout=30,
            )
            if r.status_code in (200, 201):
                success += 1
                print(f"  [{i:3}/{total}] ✓  {payload['name'][:55]}")
            else:
                failed.append((payload["name"], r.status_code, r.text[:120]))
                print(f"  [{i:3}/{total}] ✗  {payload['name'][:55]} → HTTP {r.status_code}")
        except Exception as e:
            failed.append((payload["name"], "ERR", str(e)))
            print(f"  [{i:3}/{total}] ✗  {payload['name'][:55]} → {e}")

    print(f"\n{'='*60}")
    print(f"  완료: {success}개 성공 / {len(failed)}개 실패 / 전체 {total}개")
    if failed:
        print("\n  실패 목록:")
        for name, code, msg in failed:
            print(f"    - [{code}] {name}: {msg}")


# ──────────────────────────────────────────────
# main
# ──────────────────────────────────────────────
def main():
    if len(sys.argv) != 2:
        print("사용법: python scripts/seed_products_from_json.py <json 파일 경로>", file=sys.stderr)
        sys.exit(1)

    json_path = sys.argv[1]

    print("=" * 60)
    print("  AgentCart Product Seed (JSON → /api/products)")
    print("=" * 60)

    print("\n[1] 백엔드 연결 확인...")
    if not check_backend():
        print("  [!] 백엔드(localhost:8080)에 연결할 수 없습니다.", file=sys.stderr)
        print("  [!] 서버를 먼저 실행하세요.", file=sys.stderr)
        sys.exit(1)
    print("  [+] 백엔드 정상 응답")

    print("\n[2] 로그인...")
    token = login()

    print(f"\n[3] {json_path} 로드...")
    with open(json_path, encoding="utf-8") as f:
        products = json.load(f)
    print(f"  [+] {len(products)}개 상품 로드 완료")

    print("\n[4] /api/products 주입...")
    seed_products(products, token)


if __name__ == "__main__":
    main()
