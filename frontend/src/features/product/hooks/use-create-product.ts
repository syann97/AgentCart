'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { productApi } from '../api/product.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { ProductCreateInput, ProductDetail } from '../types/product.types';
import type { ApiResponse } from '@/types/api.types';

interface UseCreateProductOptions {
  onSuccess?: (data: ApiResponse<ProductDetail>) => void;
  onError?: (error: Error) => void;
}

export function useCreateProduct(options?: UseCreateProductOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: ProductCreateInput) => productApi.create(body),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.products });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}