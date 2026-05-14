'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { productApi } from '../api/product.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { ApiResponse } from '@/types/api.types';

interface UseDeleteProductOptions {
  onSuccess?: (data: ApiResponse<void>) => void;
  onError?: (error: Error) => void;
}

export function useDeleteProduct(options?: UseDeleteProductOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => productApi.delete(id),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.products });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}