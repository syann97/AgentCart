'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '../api/cart.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { ApiResponse } from '@/types/api.types';

interface UseClearCartOptions {
  onSuccess?: (data: ApiResponse<void>) => void;
  onError?: (error: Error) => void;
}

export function useClearCart(options?: UseClearCartOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => cartApi.clearCart(),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}