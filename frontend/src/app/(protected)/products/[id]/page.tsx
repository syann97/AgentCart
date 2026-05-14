'use client';

import { useParams } from 'next/navigation';
import { useProduct } from '@/features/product/hooks/use-product';
import { ProductDetail } from '@/features/product/components/ProductDetail';

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);

  const { data, isLoading, isError, error } = useProduct(id);

  const is404 = (error as { response?: { status?: number } })?.response?.status === 404;

  return (
    <div>
      {isLoading && (
        <div className="max-w-2xl space-y-4">
          <div className="h-8 bg-gray-100 rounded animate-pulse w-2/3" />
          <div className="h-4 bg-gray-100 rounded animate-pulse w-1/3" />
          <div className="h-24 bg-gray-100 rounded animate-pulse" />
          <div className="h-8 bg-gray-100 rounded animate-pulse w-1/4" />
        </div>
      )}

      {isError && (
        <p className="text-red-500 py-8 text-center">
          {is404 ? '존재하지 않는 상품입니다.' : '상품 정보를 불러오지 못했습니다.'}
        </p>
      )}

      {data && <ProductDetail product={data.data} />}
    </div>
  );
}