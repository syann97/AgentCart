'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { paymentApi } from '../api/payment.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import type { Payment, PaymentCreateInput } from '../types/payment.types';
import type { ApiResponse } from '@/types/api.types';

interface UsePayOptions {
  onSuccess?: (data: ApiResponse<Payment>) => void;
  onError?: (error: Error) => void;
}

export function usePay(options?: UsePayOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: PaymentCreateInput) => paymentApi.pay(body),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.orders });
      options?.onSuccess?.(data);
    },
    onError: options?.onError,
  });
}