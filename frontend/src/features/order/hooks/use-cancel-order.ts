'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { orderApi } from '../api/order.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { ApiResponse } from '@/types/api.types';

interface UseCancelOrderOptions {
  onSuccess?: (data: ApiResponse<void>) => void;
  onError?: (error: Error) => void;
}

export function useCancelOrder(options?: UseCancelOrderOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => orderApi.cancelOrder(id),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.orders });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}