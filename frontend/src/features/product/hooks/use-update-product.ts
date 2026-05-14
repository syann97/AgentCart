'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { productApi } from '../api/product.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { ProductUpdateInput, ProductDetail } from '../types/product.types';
import type { ApiResponse } from '@/types/api.types';

interface UseUpdateProductOptions {
  onSuccess?: (data: ApiResponse<ProductDetail>) => void;
  onError?: (error: Error) => void;
}

export function useUpdateProduct(id: number, options?: UseUpdateProductOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: ProductUpdateInput) => productApi.update(id, body),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.products });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.product(id) });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}