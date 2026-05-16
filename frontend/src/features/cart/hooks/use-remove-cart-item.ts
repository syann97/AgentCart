'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '../api/cart.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { ApiResponse } from '@/types/api.types';

interface UseRemoveCartItemOptions {
  onSuccess?: (data: ApiResponse<void>) => void;
  onError?: (error: Error) => void;
}

export function useRemoveCartItem(options?: UseRemoveCartItemOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (itemId: number) => cartApi.removeItem(itemId),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}