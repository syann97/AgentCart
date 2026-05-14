'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import type { AxiosError } from 'axios';
import { useProduct } from '@/features/product/hooks/use-product';
import { useUpdateProduct } from '@/features/product/hooks/use-update-product';
import { ProductForm } from '@/features/product/components/ProductForm';
import type { ApiError } from '@/types/api.types';

export default function ProductEditPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const router = useRouter();
  const [serverError, setServerError] = useState<string | undefined>();

  const { data, isLoading } = useProduct(id);

  const { mutate: updateProduct, isPending } = useUpdateProduct(id, {
    onSuccess: () => router.push(`/products/${id}`),
    onError: (err) => {
      const axiosError = err as AxiosError<ApiError>;
      const status = axiosError.response?.status;
      if (status === 409) {
        setServerError('이미 존재하는 상품입니다.');
      } else if (status === 422) {
        setServerError('입력값을 확인해주세요.');
      } else {
        setServerError('상품 수정에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    },
  });

  if (isLoading) {
    return (
      <div className="max-w-lg space-y-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-10 bg-gray-100 rounded animate-pulse" />
        ))}
      </div>
    );
  }

  if (!data) return null;

  const { name, description, price, category, brand, stock } = data.data;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">상품 수정</h1>
      <ProductForm
        defaultValues={{
          name,
          description: description ?? undefined,
          price,
          category,
          brand: brand ?? undefined,
          stock,
        }}
        onSubmit={updateProduct}
        onCancel={() => router.push(`/products/${id}`)}
        isPending={isPending}
        serverError={serverError}
      />
    </div>
  );
}