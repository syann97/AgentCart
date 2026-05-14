'use client';

import { useQuery } from '@tanstack/react-query';
import { productApi } from '../api/product.api';
import { QUERY_KEYS } from '@/constants/api.constants';

export function useProduct(id: number) {
  return useQuery({
    queryKey: QUERY_KEYS.product(id),
    queryFn: () => productApi.detail(id),
  });
}