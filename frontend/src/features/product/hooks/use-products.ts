'use client';

import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { productApi } from '../api/product.api';
import { QUERY_KEYS } from '@/constants/api.constants';

interface UseProductsParams {
  search?: string;
  page?: number;
  size?: number;
}

export function useProducts(params?: UseProductsParams) {
  return useQuery({
    queryKey: [...QUERY_KEYS.products, params],
    queryFn: () => productApi.list(params),
    placeholderData: keepPreviousData,
  });
}