'use client';

import { useState } from 'react';
import { useSse } from '@/hooks/use-sse';
import { API_BASE_URL, API_ENDPOINTS } from '@/constants/api.constants';
import type { RecommendationResult, RecommendationStreamChunk } from '../types/recommendation.types';

export function useRecommendationStream(enabled: boolean) {
  const [results, setResults] = useState<RecommendationResult[]>([]);
  const [isComplete, setIsComplete] = useState(false);

  const url = `${API_BASE_URL}${API_ENDPOINTS.recommendations.stream}`;

  const { isConnected, close } = useSse(url, {
    enabled,
    onMessage: (raw) => {
      try {
        const chunk: RecommendationStreamChunk = JSON.parse(raw);
        if (chunk.type === 'complete' && chunk.data as RecommendationResult) {
          setResults((prev) => [...prev, chunk.data as RecommendationResult]);
        }
        if (chunk.type === 'error') {
          setIsComplete(true);
          close();
        }
      } catch {
        // non-JSON keep-alive 무시
      }
    },
    onError: () => setIsComplete(true),
  });

  return { results, isConnected, isComplete };
}
