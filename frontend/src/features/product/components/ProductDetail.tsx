'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useAddCartItem } from '@/features/cart/hooks/use-add-cart-item';
import type { ProductDetail as ProductDetailType, ProductStatus } from '../types/product.types';

const MAX_ORDER_QUANTITY = 10;

const STATUS_LABEL: Record<ProductStatus, string> = {
  ACTIVE: '판매중',
  SOLD_OUT: '품절',
  INACTIVE: '비활성',
};

const STATUS_CLASS: Record<ProductStatus, string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  SOLD_OUT: 'bg-red-100 text-red-700',
  INACTIVE: 'bg-gray-100 text-gray-600',
};

interface ProductDetailProps {
  product: ProductDetailType;
}

export function ProductDetail({ product }: ProductDetailProps) {
  const isSoldOut = product.status === 'SOLD_OUT' || product.stock === 0;
  const maxQuantity = Math.min(product.stock, MAX_ORDER_QUANTITY);
  const [quantity, setQuantity] = useState(1);
  const [inputValue, setInputValue] = useState('1');
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const { mutate: addToCart, isPending } = useAddCartItem({
    onSuccess: () => {
      setToast(`장바구니에 ${quantity}개 담았습니다.`);
      setTimeout(() => setToast(null), 3000);
    },
  });

  function applyQuantity(value: number) {
    if (isNaN(value) || value < 1) {
      setError('수량은 1개 이상이어야 합니다.');
      setQuantity(1);
      setInputValue('1');
      return;
    }
    if (value > maxQuantity) {
      setError(`최대 ${maxQuantity}개까지 주문 가능합니다.`);
      setQuantity(maxQuantity);
      setInputValue(String(maxQuantity));
      return;
    }
    setError(null);
    setQuantity(value);
    setInputValue(String(value));
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    setInputValue(e.target.value);
  }

  function handleInputBlur() {
    applyQuantity(parseInt(inputValue, 10));
  }

  function handleDecrease() {
    applyQuantity(quantity - 1);
  }

  function handleIncrease() {
    applyQuantity(quantity + 1);
  }

  function handleAddToCart() {
    addToCart({ productId: product.id, quantity });
  }

  const canAddToCart = product.status === 'ACTIVE' && !isSoldOut;

  return (
    <div className="max-w-2xl">
      <div className="flex justify-between items-start mb-2">
        <h1 className="text-2xl font-bold text-gray-900">{product.name}</h1>
        <span className={`text-sm px-3 py-1 rounded-full ml-4 shrink-0 ${STATUS_CLASS[product.status]}`}>
          {STATUS_LABEL[product.status]}
        </span>
      </div>

      <p className="text-sm text-gray-500 mb-6">
        {[product.brand, product.category].filter(Boolean).join(' · ')}
      </p>

      {product.description && (
        <p className="text-gray-700 mb-6 leading-relaxed">{product.description}</p>
      )}

      <div className="flex items-center justify-between mb-8">
        <p className="text-2xl font-bold text-gray-900">₩{product.price.toLocaleString()}</p>
        <p className={`text-sm ${isSoldOut ? 'text-red-500 font-semibold' : 'text-gray-600'}`}>
          재고: {product.stock}개
        </p>
      </div>

      {canAddToCart && (
        <div className="mb-6">
          <div className="flex items-center gap-2">
            <label htmlFor="quantity" className="text-sm text-gray-700 shrink-0">
              수량
            </label>
            <button
              onClick={handleDecrease}
              disabled={isPending}
              aria-label="수량 감소"
              className="w-8 h-8 rounded border text-gray-700 hover:bg-gray-100 disabled:opacity-40 flex items-center justify-center text-lg font-medium"
            >
              -
            </button>
            <input
              id="quantity"
              type="number"
              min={1}
              value={inputValue}
              onChange={handleInputChange}
              onBlur={handleInputBlur}
              className="w-16 px-2 py-1.5 border rounded-md text-sm text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              onClick={handleIncrease}
              disabled={isPending}
              aria-label="수량 증가"
              className="w-8 h-8 rounded border text-gray-700 hover:bg-gray-100 disabled:opacity-40 flex items-center justify-center text-lg font-medium"
            >
              +
            </button>
          </div>
          {error && <p className="mt-1.5 text-xs text-red-500">{error}</p>}
        </div>
      )}

      <div className="flex gap-2">
        <Link
          href="/products"
          className="inline-block px-4 py-2 rounded-md border text-sm text-gray-700 hover:bg-gray-50 transition-colors"
        >
          목록으로
        </Link>

        {canAddToCart && (
          <button
            onClick={handleAddToCart}
            disabled={isPending}
            className="px-4 py-2 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            {isPending ? '담는 중...' : '장바구니에 담기'}
          </button>
        )}
      </div>

      {toast && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 bg-gray-800 text-white text-sm px-4 py-2 rounded-lg shadow-lg z-50">
          {toast}
        </div>
      )}
    </div>
  );
}