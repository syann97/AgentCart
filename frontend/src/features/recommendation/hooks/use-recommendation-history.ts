'use client';

import { useQuery } from '@tanstack/react-query';
import { QUERY_KEYS } from '@/constants/api.constants';
import { getRecommendationHistory } from '../api/recommendation.api';

export function useRecommendationHistory() {
  return useQuery({
    queryKey: QUERY_KEYS.recommendations,
    queryFn: getRecommendationHistory,
    staleTime: 0,
  });
}