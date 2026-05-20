'use client';

import { useEffect, useState } from 'react';
import { useSse } from '@/hooks/use-sse';
import { API_BASE_URL, API_ENDPOINTS } from '@/constants/api.constants';
import type { RecommendationResult, RecommendationStreamChunk } from '../types/recommendation.types';

export function useRecommendationStream(query: string, enabled: boolean) {
  const [results, setResults] = useState<RecommendationResult[]>([]);
  const [isComplete, setIsComplete] = useState(false);

  const url = query
    ? `${API_BASE_URL}${API_ENDPOINTS.recommendations.stream}?query=${encodeURIComponent(query)}`
    : '';

  useEffect(() => {
    setResults([]);
    setIsComplete(false);
  }, [url]);

  const { isConnected, close } = useSse(url, {
    enabled: enabled && !!query,
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