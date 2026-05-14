'use client';

import { useSearchParams, useRouter } from 'next/navigation';
import { useProducts } from '@/features/product/hooks/use-products';
import { ProductList } from '@/features/product/components/ProductList';
import { CategoryFilter } from '@/features/product/components/CategoryFilter';

export default function ProductsPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const category = searchParams.get('category') ?? undefined;
  const page = Number(searchParams.get('page') ?? 0);

  const { data, isLoading, isError } = useProducts({ category, page, size: 20 });

  const goToPage = (next: number) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('page', String(next));
    router.push(`?${params.toString()}`);
  };

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">상품 목록</h1>

      <div className="mb-4">
        <CategoryFilter />
      </div>

      {isLoading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="border rounded-lg p-4 h-32 animate-pulse bg-gray-100" />
          ))}
        </div>
      )}

      {isError && (
        <p className="text-red-500 py-8 text-center">상품 목록을 불러오지 못했습니다.</p>
      )}

      {data && (
        <>
          <ProductList products={data.data.content} />

          {data.data.totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-8">
              <button
                disabled={data.data.number === 0}
                onClick={() => goToPage(data.data.number - 1)}
                className="px-4 py-2 rounded border text-sm disabled:opacity-40 hover:bg-gray-50"
              >
                이전
              </button>
              <span className="px-4 py-2 text-sm text-gray-600">
                {data.data.number + 1} / {data.data.totalPages}
              </span>
              <button
                disabled={data.data.last}
                onClick={() => goToPage(data.data.number + 1)}
                className="px-4 py-2 rounded border text-sm disabled:opacity-40 hover:bg-gray-50"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}