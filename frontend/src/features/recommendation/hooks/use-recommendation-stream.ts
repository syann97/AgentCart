'use client';

import { useRef, useState } from 'react';
import { useSse } from '@/hooks/use-sse';
import { API_BASE_URL, API_ENDPOINTS } from '@/constants/api.constants';
import type {
  RecommendationResult,
  RecommendationStreamError,
  RecommendationStreamEvent,
  RecommendationStreamOutcome,
} from '../types/recommendation.types';

export function useRecommendationStream() {
  const [query, setQuery] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [results, setResults] = useState<RecommendationResult[]>([]);
  const [isComplete, setIsComplete] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<RecommendationStreamOutcome | null>(null);
  const [completionMessage, setCompletionMessage] = useState<string | null>(null);
  const [error, setError] = useState<RecommendationStreamError | null>(null);
  const terminalRef = useRef(false);

  const url = query
    ? `${API_BASE_URL}${API_ENDPOINTS.recommendations.stream}?query=${encodeURIComponent(query)}`
    : '';

  // 검색 시작 — 리셋과 연결 시작을 한 핸들러에서 원자적으로 처리
  const start = (q: string) => {
    setQuery(q);
    setResults([]);
    setIsComplete(false);
    setStatusMessage(null);
    setOutcome(null);
    setCompletionMessage(null);
    setError(null);
    terminalRef.current = false;
    setIsStreaming(true);
  };

  const finish = () => {
    terminalRef.current = true;
    setIsComplete(true);
    setIsStreaming(false);
    setStatusMessage(null);
  };

  const { close } = useSse(url, {
    enabled: isStreaming && !!query,
    onMessage: (raw) => {
      try {
        const event = parseStreamEvent(raw);
        if (!event || terminalRef.current) return;
        switch (event.type) {
          case 'status':
            setStatusMessage(event.data.message);
            break;
          case 'result':
            setResults((prev) => [...prev, event.data]);
            break;
          case 'done':
            setOutcome(event.data.outcome);
            setCompletionMessage(event.data.message);
            finish();
            close();
            break;
          case 'error':
            setError({ ...event.data, transport: false });
            finish();
            close();
            break;
        }
      } catch {
        // malformed 또는 keep-alive 이벤트는 상태를 변경하지 않는다.
      }
    },
    onError: () => {
      if (terminalRef.current) return;
      setError({
        code: 'TRANSPORT_ERROR',
        message: '추천 연결이 끊어졌습니다. 다시 시도해 주세요.',
        retryable: true,
        transport: true,
      });
      finish();
      close();
    },
  });

  return {
    results,
    isComplete,
    isSearching: isStreaming && !isComplete,
    statusMessage,
    outcome,
    completionMessage,
    error,
    start,
  };
}

function parseStreamEvent(raw: string): RecommendationStreamEvent | null {
  const value: unknown = JSON.parse(raw);
  if (!isObject(value) || typeof value.type !== 'string' || !isObject(value.data)) return null;

  switch (value.type) {
    case 'status':
      return typeof value.data.message === 'string' && typeof value.data.searchAttempt === 'number'
        ? (value as RecommendationStreamEvent)
        : null;
    case 'result':
      return isRecommendationResult(value.data) ? (value as RecommendationStreamEvent) : null;
    case 'done':
      return isOutcome(value.data.outcome) && typeof value.data.message === 'string'
        ? (value as RecommendationStreamEvent)
        : null;
    case 'error':
      return typeof value.data.code === 'string'
        && typeof value.data.message === 'string'
        && typeof value.data.retryable === 'boolean'
        ? (value as RecommendationStreamEvent)
        : null;
    default:
      return null;
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isRecommendationResult(value: Record<string, unknown>): boolean {
  return typeof value.productId === 'number'
    && typeof value.productName === 'string'
    && typeof value.price === 'number'
    && typeof value.reason === 'string'
    && Array.isArray(value.conditions)
    && typeof value.score === 'number';
}

function isOutcome(value: unknown): value is RecommendationStreamOutcome {
  return value === 'SUCCESS'
    || value === 'NO_RESULTS'
    || value === 'OUT_OF_SCOPE'
    || value === 'CLARIFICATION_REQUIRED'
    || value === 'FALLBACK';
}
