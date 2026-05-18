'use client';

import { useQuery } from '@tanstack/react-query';
import { orderApi } from '../api/order.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import { useAuthStore } from '@/stores/auth.store';

export function useOrders(page = 0) {
  const member = useAuthStore((s) => s.member);

  return useQuery({
    queryKey: [...QUERY_KEYS.orders, page],
    queryFn: () => orderApi.getOrders(page),
    enabled: !!member,
    staleTime: 0,
  });
}