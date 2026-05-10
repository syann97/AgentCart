'use client';

import { useEffect, useRef, useState } from 'react';
import { tokenUtils } from '@/utils/token.utils';

interface UseSseOptions {
  onMessage?: (data: string) => void;
  onError?: (err: Event) => void;
  enabled?: boolean;
}

/**
 * SSE 스트림을 구독한다.
 * Spring Boot의 /api/recommendations/stream 등 text/event-stream 엔드포인트와 연동.
 * EventSource는 쿠키를 자동으로 전송하지만 Authorization 헤더를 지원하지 않으므로
 * Access Token을 query parameter로 전달하는 방식을 사용한다.
 */
export function useSse(url: string, options: UseSseOptions = {}) {
  const { onMessage, onError, enabled = true } = options;
  const [isConnected, setIsConnected] = useState(false);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled) return;

    const token = tokenUtils.get();
    const fullUrl = token ? `${url}?token=${encodeURIComponent(token)}` : url;

    const es = new EventSource(fullUrl, { withCredentials: true });
    esRef.current = es;

    es.onopen = () => setIsConnected(true);
    es.onmessage = (e) => onMessage?.(e.data);
    es.onerror = (e) => {
      onError?.(e);
      setIsConnected(false);
    };

    return () => {
      es.close();
      setIsConnected(false);
    };
  }, [url, enabled]); // eslint-disable-line react-hooks/exhaustive-deps

  const close = () => esRef.current?.close();

  return { isConnected, close };
}
