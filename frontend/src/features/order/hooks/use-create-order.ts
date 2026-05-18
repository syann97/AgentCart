'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { orderApi } from '../api/order.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { OrderCreateInput, Order } from '../types/order.types';
import type { ApiResponse } from '@/types/api.types';

interface UseCreateOrderOptions {
  onSuccess?: (data: ApiResponse<Order>) => void;
  onError?: (error: Error) => void;
}

export function useCreateOrder(options?: UseCreateOrderOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: OrderCreateInput) => orderApi.createOrder(body),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.orders });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}