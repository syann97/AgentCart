'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useAddCartItem } from '@/features/cart/hooks/use-add-cart-item';
import type { ProductDetail as ProductDetailType, ProductStatus } from '../types/product.types';

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
  const [toast, setToast] = useState<string | null>(null);

  const { mutate: addToCart, isPending } = useAddCartItem({
    onSuccess: () => {
      setToast('장바구니에 담았습니다.');
      setTimeout(() => setToast(null), 3000);
    },
  });

  function handleAddToCart() {
    addToCart({ productId: product.id, quantity: 1 });
  }

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

      <div className="flex gap-2">
        <Link
          href="/products"
          className="inline-block px-4 py-2 rounded-md border text-sm text-gray-700 hover:bg-gray-50 transition-colors"
        >
          목록으로
        </Link>

        {product.status === 'ACTIVE' && !isSoldOut && (
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