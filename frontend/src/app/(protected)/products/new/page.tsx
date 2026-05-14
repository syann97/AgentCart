'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type { AxiosError } from 'axios';
import { useCreateProduct } from '@/features/product/hooks/use-create-product';
import { ProductForm } from '@/features/product/components/ProductForm';
import type { ApiError } from '@/types/api.types';

export default function ProductCreatePage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | undefined>();

  const { mutate: createProduct, isPending } = useCreateProduct({
    onSuccess: (res) => router.push(`/products/${res.data.id}`),
    onError: (err) => {
      const axiosError = err as AxiosError<ApiError>;
      const status = axiosError.response?.status;
      if (status === 409) {
        setServerError('이미 존재하는 상품입니다.');
      } else if (status === 422) {
        setServerError('입력값을 확인해주세요.');
      } else {
        setServerError('상품 등록에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    },
  });

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">상품 등록</h1>
      <ProductForm
        onSubmit={createProduct}
        onCancel={() => router.push('/products')}
        isPending={isPending}
        serverError={serverError}
      />
    </div>
  );
}