'use client';

import { useState } from 'react';
import { useSse } from '@/hooks/use-sse';
import { API_BASE_URL, API_ENDPOINTS } from '@/constants/api.constants';
import type { RecommendationResult, RecommendationStreamChunk } from '../types/recommendation.types';

export function useRecommendationStream() {
  const [query, setQuery] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [results, setResults] = useState<RecommendationResult[]>([]);
  const [isComplete, setIsComplete] = useState(false);

  const url = query
    ? `${API_BASE_URL}${API_ENDPOINTS.recommendations.stream}?query=${encodeURIComponent(query)}`
    : '';

  // 검색 시작 — 리셋과 연결 시작을 한 핸들러에서 원자적으로 처리
  const start = (q: string) => {
    setQuery(q);
    setResults([]);
    setIsComplete(false);
    setIsStreaming(true);
  };

  const finish = () => {
    setIsComplete(true);
    setIsStreaming(false);
  };

  const { close } = useSse(url, {
    enabled: isStreaming && !!query,
    onMessage: (raw) => {
      try {
        const chunk: RecommendationStreamChunk = JSON.parse(raw);
        if (chunk.type === 'complete' && chunk.data as RecommendationResult) {
          setResults((prev) => [...prev, chunk.data as RecommendationResult]);
        }
        if (chunk.type === 'error') {
          finish();
          close();
        }
      } catch {
        // non-JSON keep-alive 무시
      }
    },
    onError: () => finish(),
  });

  return { results, isComplete, isSearching: isStreaming && !isComplete, start };
}
