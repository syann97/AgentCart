'use client';

import { useState } from 'react';
import { useUpdateCartItem } from '../hooks/use-update-cart-item';
import { useRemoveCartItem } from '../hooks/use-remove-cart-item';
import type { CartItem } from '../types/cart.types';

const STATUS_LABEL: Record<CartItem['productStatus'], string> = {
  ACTIVE: '판매중',
  SOLD_OUT: '품절',
  INACTIVE: '비활성',
};

const STATUS_CLASS: Record<CartItem['productStatus'], string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  SOLD_OUT: 'bg-red-100 text-red-700',
  INACTIVE: 'bg-gray-100 text-gray-600',
};

interface CartItemRowProps {
  item: CartItem;
}

export function CartItemRow({ item }: CartItemRowProps) {
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const isSoldOut = item.productStatus === 'SOLD_OUT';

  const { mutate: updateItem, isPending: isUpdating } = useUpdateCartItem(item.id);
  const { mutate: removeItem, isPending: isRemoving } = useRemoveCartItem();

  const isPending = isUpdating || isRemoving;

  function handleDecrease() {
    if (item.quantity === 1) {
      setShowDeleteConfirm(true);
    } else {
      updateItem({ quantity: item.quantity - 1 });
    }
  }

  function handleIncrease() {
    updateItem({ quantity: item.quantity + 1 });
  }

  function handleDelete() {
    removeItem(item.id);
    setShowDeleteConfirm(false);
  }

  return (
    <>
      <div className={`border rounded-lg p-4 ${isSoldOut ? 'opacity-60' : ''}`}>
        <div className="flex justify-between items-start mb-1">
          <h3 className="font-semibold text-gray-900">{item.productName}</h3>
          <span className={`text-xs px-2 py-0.5 rounded-full ml-2 shrink-0 ${STATUS_CLASS[item.productStatus]}`}>
            {STATUS_LABEL[item.productStatus]}
          </span>
        </div>

        <p className="text-sm text-gray-500 mb-4">₩{item.productPrice.toLocaleString()}</p>

        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <button
              onClick={handleDecrease}
              disabled={isPending || isSoldOut}
              aria-label="수량 감소"
              className="w-8 h-8 rounded border text-gray-700 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center text-lg font-medium"
            >
              -
            </button>
            <span className="w-8 text-center font-medium">{item.quantity}</span>
            <button
              onClick={handleIncrease}
              disabled={isPending || isSoldOut}
              aria-label="수량 증가"
              className="w-8 h-8 rounded border text-gray-700 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center text-lg font-medium"
            >
              +
            </button>
          </div>

          <div className="flex items-center gap-4">
            <p className="text-sm font-semibold text-gray-900">
              소계: ₩{item.subtotal.toLocaleString()}
            </p>
            <button
              onClick={() => setShowDeleteConfirm(true)}
              disabled={isPending}
              className="text-sm text-red-500 hover:text-red-700 disabled:opacity-40"
            >
              삭제
            </button>
          </div>
        </div>
      </div>

      {showDeleteConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-sm w-full mx-4">
            <h2 className="text-lg font-semibold mb-2">상품 삭제</h2>
            <p className="text-gray-600 mb-6">
              <span className="font-medium">{item.productName}</span>을(를) 장바구니에서 삭제하시겠습니까?
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowDeleteConfirm(false)}
                disabled={isRemoving}
                className="rounded-md py-2 px-4 text-sm font-medium bg-gray-100 text-gray-800 hover:bg-gray-200 disabled:opacity-50"
              >
                취소
              </button>
              <button
                onClick={handleDelete}
                disabled={isRemoving}
                className="rounded-md py-2 px-4 text-sm font-medium bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
              >
                {isRemoving ? '삭제 중...' : '삭제'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}