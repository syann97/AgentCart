'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { productCreateSchema, type ProductCreateInput } from '../types/product.types';
import { FormField } from '@/components/ui/FormField';
import { Button } from '@/components/ui/Button';

interface ProductFormProps {
  defaultValues?: Partial<ProductCreateInput>;
  onSubmit: (data: ProductCreateInput) => void;
  onCancel: () => void;
  isPending: boolean;
  serverError?: string;
}

export function ProductForm({ defaultValues, onSubmit, onCancel, isPending, serverError }: ProductFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ProductCreateInput>({
    resolver: zodResolver(productCreateSchema),
    defaultValues: defaultValues ?? {},
  });

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4 max-w-lg">
      <FormField
        id="name"
        label="이름 *"
        placeholder="상품명"
        error={errors.name}
        {...register('name')}
      />
      <FormField
        id="description"
        label="설명"
        placeholder="상품 설명"
        error={errors.description}
        {...register('description')}
      />
      <FormField
        id="price"
        label="가격 *"
        type="number"
        step="any"
        placeholder="0"
        error={errors.price}
        {...register('price', { valueAsNumber: true })}
      />
      <FormField
        id="category"
        label="카테고리 *"
        placeholder="예: electronics"
        error={errors.category}
        {...register('category')}
      />
      <FormField
        id="brand"
        label="브랜드"
        placeholder="브랜드명"
        error={errors.brand}
        {...register('brand')}
      />
      <FormField
        id="stock"
        label="재고 *"
        type="number"
        step="1"
        placeholder="0"
        error={errors.stock}
        {...register('stock', { valueAsNumber: true })}
      />

      {serverError && <p className="text-xs text-red-500">{serverError}</p>}

      <div className="flex gap-2">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isPending}>
          취소
        </Button>
        <Button type="submit" loading={isPending} loadingText="저장 중...">
          저장
        </Button>
      </div>
    </form>
  );
}