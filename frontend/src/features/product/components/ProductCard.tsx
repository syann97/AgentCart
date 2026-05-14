'use client';

import { useRouter } from 'next/navigation';
import type { ProductSummary, ProductStatus } from '../types/product.types';

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

interface ProductCardProps {
  product: ProductSummary;
}

export function ProductCard({ product }: ProductCardProps) {
  const router = useRouter();

  return (
    <div
      role="article"
      className="border rounded-lg p-4 cursor-pointer hover:shadow-md transition-shadow"
      onClick={() => router.push(`/products/${product.id}`)}
    >
      <div className="flex justify-between items-start mb-1">
        <h3 className="font-semibold text-gray-900 truncate">{product.name}</h3>
        <span className={`text-xs px-2 py-0.5 rounded-full ml-2 shrink-0 ${STATUS_CLASS[product.status]}`}>
          {STATUS_LABEL[product.status]}
        </span>
      </div>
      {product.brand && <p className="text-sm text-gray-500 mb-1">{product.brand}</p>}
      <p className="text-base font-bold text-gray-900">₩{product.price.toLocaleString()}</p>
      <p className="text-xs text-gray-400 mt-1">{product.category}</p>
    </div>
  );
}
