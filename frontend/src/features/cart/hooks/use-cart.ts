'use client';

import { useQuery } from '@tanstack/react-query';
import { cartApi } from '../api/cart.api';
import { QUERY_KEYS } from '@/constants/api.constants';
import { useAuthStore } from '@/stores/auth.store';

export function useCart() {
  const member = useAuthStore((s) => s.member);

  return useQuery({
    queryKey: QUERY_KEYS.cart,
    queryFn: () => cartApi.getCart(),
    enabled: !!member,
    staleTime: 0,
  });
}