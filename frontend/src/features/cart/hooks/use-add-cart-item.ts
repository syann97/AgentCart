'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '../api/cart.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { CartItemAddInput, CartItem } from '../types/cart.types';
import type { ApiResponse } from '@/types/api.types';

interface UseAddCartItemOptions {
  onSuccess?: (data: ApiResponse<CartItem>) => void;
  onError?: (error: Error) => void;
}

export function useAddCartItem(options?: UseAddCartItemOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: CartItemAddInput) => cartApi.addItem(body),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}