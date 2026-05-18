'use client';

import Link from 'next/link';
import { useAuthStore } from '@/stores/auth.store';
import { authApi } from '@/features/auth/api/auth.api';

export function Header() {
  const { isAuthenticated, member, clearAuth } = useAuthStore();

  const handleLogout = async () => {
    await authApi.logout().catch(() => {});
    clearAuth();
  };

  return (
    <header className="border-b bg-white sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 h-14 flex items-center justify-between">
        <Link href="/" className="font-bold text-lg text-blue-600">
          AgentCart
        </Link>

        <nav className="flex items-center gap-4 text-sm">
          <Link href="/products" className="hover:text-blue-600">
            상품
          </Link>
          <Link href="/recommendations" className="hover:text-blue-600">
            AI 추천
          </Link>
          {isAuthenticated ? (
            <>
              <Link href="/cart" className="hover:text-blue-600">
                장바구니
              </Link>
              <Link href="/orders" className="hover:text-blue-600">
                주문 내역
              </Link>
              <span className="text-gray-500">{member?.name}</span>
              <button onClick={handleLogout} className="text-gray-500 hover:text-red-500">
                로그아웃
              </button>
            </>
          ) : (
            <Link
              href="/login"
              className="bg-blue-600 text-white px-3 py-1.5 rounded-md hover:bg-blue-700"
            >
              로그인
            </Link>
          )}
        </nav>
      </div>
    </header>
  );
}
