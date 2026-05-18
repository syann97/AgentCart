'use client';

import { useQuery } from '@tanstack/react-query';
import { orderApi } from '../api/order.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import { useAuthStore } from '@/stores/auth.store';

export function useOrder(id: number) {
  const member = useAuthStore((s) => s.member);

  return useQuery({
    queryKey: QUERY_KEYS.order(id),
    queryFn: () => orderApi.getOrder(id),
    enabled: !!member && !!id,
    staleTime: 0,
  });
}