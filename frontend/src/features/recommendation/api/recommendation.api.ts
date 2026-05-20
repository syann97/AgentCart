import { apiClient } from '@/lib/axios';
import { API_ENDPOINTS } from '@/constants/api.constants';
import type { ApiResponse } from '@/types/api.types';
import type { RecommendationHistoryItem } from '../types/recommendation.types';

export async function getRecommendationHistory(): Promise<RecommendationHistoryItem[]> {
  const { data } = await apiClient.get<ApiResponse<RecommendationHistoryItem[]>>(
    API_ENDPOINTS.recommendations.history,
  );
  return data.data;
}