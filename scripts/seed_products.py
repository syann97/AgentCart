#!/usr/bin/env python3
"""
AgentCart Product Seed Script
DummyJSON(194개) → POST /api/products (ADMIN 인증 포함)

실행:  python scripts/seed_products.py
전제:  백엔드(localhost:8080) + MySQL 컨테이너(agentcart-mysql) 실행 중
"""

import json
import subprocess
import sys
import time

import requests

BASE_URL = "http://localhost:8080/api"
DUMMYJSON_URL = "https://dummyjson.com/products"

ADMIN_EMAIL = "admin@agentcart.com"
ADMIN_PASSWORD = "Admin1234!"
ADMIN_NAME = "Admin"
ADMIN_NICKNAME = "agentcart_admin"


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
# 2. ADMIN 계정 준비
# ──────────────────────────────────────────────
def register_admin():
    payload = {
        "email": ADMIN_EMAIL,
        "password": ADMIN_PASSWORD,
        "name": ADMIN_NAME,
        "nickname": ADMIN_NICKNAME,
    }
    r = requests.post(f"{BASE_URL}/auth/register", json=payload, timeout=10)
    if r.status_code in (200, 201):
        print(f"  [+] 회원가입 완료: {ADMIN_EMAIL}")
    else:
        # 이미 존재하거나 닉네임 충돌 → 계속 진행
        print(f"  [~] 이미 존재하는 계정 (또는 닉네임 충돌), 계속 진행...")


def promote_to_admin():
    """docker exec로 MySQL에 직접 ADMIN 승격"""
    sql = f"UPDATE agentcart.members SET role = 'ADMIN' WHERE email = '{ADMIN_EMAIL}';"
    cmd = ["docker", "exec", "agentcart-mysql", "mysql", "-uroot", "-proot", "-e", sql]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"  [!] role 승격 실패: {result.stderr}", file=sys.stderr)
        sys.exit(1)
    print(f"  [+] role → ADMIN 승격 완료")


# ──────────────────────────────────────────────
# 3. 로그인 → access token
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
# 4. DummyJSON 전체 상품 수집
# ──────────────────────────────────────────────
def fetch_all_products() -> list:
    products, skip, limit = [], 0, 100
    while True:
        r = requests.get(DUMMYJSON_URL, params={"limit": limit, "skip": skip}, timeout=10)
        r.raise_for_status()
        data = r.json()
        products.extend(data["products"])
        skip += limit
        if skip >= data["total"]:
            break
    print(f"  [+] {len(products)}개 상품 수집 완료 (24개 카테고리)")
    return products


# ──────────────────────────────────────────────
# 5. 필드 매핑
# ──────────────────────────────────────────────
def map_product(p: dict) -> dict:
    return {
        "name": p["title"],
        "description": p.get("description") or "",
        "price": round(float(p["price"]), 2),
        "category": p["category"],
        "brand": p.get("brand") or "",
        "stock": int(p.get("stock", 0)),
    }


# ──────────────────────────────────────────────
# 6. /api/products POST
# ──────────────────────────────────────────────
def seed_products(products: list, token: str):
    headers = {"Authorization": f"Bearer {token}"}
    total = len(products)
    success, failed = 0, []

    for i, raw in enumerate(products, 1):
        payload = map_product(raw)
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
    print("=" * 60)
    print("  AgentCart Product Seed  (DummyJSON → /api/products)")
    print("=" * 60)

    print("\n[1] 백엔드 연결 확인...")
    if not check_backend():
        print("  [!] 백엔드(localhost:8080)에 연결할 수 없습니다.", file=sys.stderr)
        print("  [!] 서버를 먼저 실행하세요.", file=sys.stderr)
        sys.exit(1)
    print("  [+] 백엔드 정상 응답")

    print("\n[2] ADMIN 계정 준비...")
    register_admin()
    promote_to_admin()

    print("\n[3] 로그인...")
    token = login()

    print("\n[4] DummyJSON 상품 수집...")
    products = fetch_all_products()

    print("\n[5] /api/products 주입...")
    seed_products(products, token)


if __name__ == "__main__":
    main()
