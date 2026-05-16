'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '../api/cart.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { CartItemUpdateInput, CartItem } from '../types/cart.types';
import type { ApiResponse } from '@/types/api.types';

interface UseUpdateCartItemOptions {
  onSuccess?: (data: ApiResponse<CartItem>) => void;
  onError?: (error: Error) => void;
}

export function useUpdateCartItem(itemId: number, options?: UseUpdateCartItemOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: CartItemUpdateInput) => cartApi.updateItem(itemId, body),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}