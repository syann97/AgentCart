'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useCart } from '@/features/cart/hooks/use-cart';
import { CartItemRow } from '@/features/cart/components/CartItemRow';
import { CartSummary } from '@/features/cart/components/CartSummary';
import type { CartItem } from '@/features/cart/types/cart.types';

export default function CartPage() {
  const { data, isLoading, isError } = useCart();
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  const items: CartItem[] = data?.data.items ?? [];
  const selectableItems = items.filter((item) => item.productStatus !== 'SOLD_OUT');
  const allSelected =
    selectableItems.length > 0 && selectableItems.every((item) => selectedIds.has(item.id));
  const selectedItems = items.filter((item) => selectedIds.has(item.id));

  function handleSelectAll(checked: boolean) {
    if (checked) {
      setSelectedIds(new Set(selectableItems.map((item) => item.id)));
    } else {
      setSelectedIds(new Set());
    }
  }

  function handleItemChecked(id: number, checked: boolean) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">장바구니</h1>

      {isLoading && (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="border rounded-lg p-4 space-y-3 animate-pulse">
              <div className="h-5 bg-gray-100 rounded w-1/2" />
              <div className="h-4 bg-gray-100 rounded w-1/4" />
              <div className="h-8 bg-gray-100 rounded w-1/3" />
            </div>
          ))}
        </div>
      )}

      {isError && (
        <p className="text-red-500 py-8 text-center">장바구니 정보를 불러오지 못했습니다.</p>
      )}

      {data && data.data.items.length === 0 && (
        <div className="py-16 text-center">
          <p className="text-gray-500 mb-4">장바구니가 비어 있습니다.</p>
          <Link
            href="/products"
            className="inline-block px-4 py-2 rounded-md border text-sm text-gray-700 hover:bg-gray-50 transition-colors"
          >
            쇼핑 계속하기
          </Link>
        </div>
      )}

      {data && data.data.items.length > 0 && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 px-1">
            <input
              type="checkbox"
              id="select-all"
              checked={allSelected}
              onChange={(e) => handleSelectAll(e.target.checked)}
              disabled={selectableItems.length === 0}
              aria-label="전체 선택"
              className="h-4 w-4 rounded border-gray-300 accent-blue-600 disabled:cursor-not-allowed"
            />
            <label htmlFor="select-all" className="text-sm text-gray-700 cursor-pointer select-none">
              전체 선택 ({selectedIds.size}/{selectableItems.length})
            </label>
          </div>

          {data.data.items.map((item) => (
            <CartItemRow
              key={item.id}
              item={item}
              checked={selectedIds.has(item.id)}
              onCheckedChange={(checked) => handleItemChecked(item.id, checked)}
            />
          ))}
          <CartSummary selectedItems={selectedItems} />
        </div>
      )}
    </div>
  );
}