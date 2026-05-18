'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useClearCart } from '../hooks/use-clear-cart';
import { Button } from '@/components/ui/Button';
import type { CartItem } from '../types/cart.types';

interface CartSummaryProps {
  selectedItems: CartItem[];
}

export function CartSummary({ selectedItems }: CartSummaryProps) {
  const router = useRouter();
  const selectedTotal = selectedItems.reduce((sum, item) => sum + item.subtotal, 0);
  const hasSelection = selectedItems.length > 0;
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const { mutate: clearCart, isPending } = useClearCart();

  function handleClear() {
    clearCart();
    setShowClearConfirm(false);
  }

  return (
    <>
      <div className="border rounded-lg p-4">
        <div className="flex justify-between items-center mb-4">
          <span className="text-gray-700">
            선택 상품 금액 ({selectedItems.length}개)
          </span>
          <span className="text-xl font-bold text-gray-900">₩{selectedTotal.toLocaleString()}</span>
        </div>

        <div className="flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={() => setShowClearConfirm(true)}
            disabled={isPending}
          >
            전체 비우기
          </Button>
          <Button
            disabled={!hasSelection}
            onClick={() => {
              const ids = selectedItems.map((item) => item.id).join(',');
              router.push(`/orders/new?cartItemIds=${ids}`);
            }}
          >
            주문하기
          </Button>
        </div>
      </div>

      {showClearConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-sm w-full mx-4">
            <h2 className="text-lg font-semibold mb-2">장바구니 비우기</h2>
            <p className="text-gray-600 mb-6">장바구니의 모든 상품을 삭제하시겠습니까?</p>
            <div className="flex justify-end gap-2">
              <Button
                variant="secondary"
                onClick={() => setShowClearConfirm(false)}
                disabled={isPending}
              >
                취소
              </Button>
              <button
                onClick={handleClear}
                disabled={isPending}
                className="rounded-md py-2 px-4 text-sm font-medium bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
              >
                {isPending ? '비우는 중...' : '비우기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}