import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useRecommendationStream } from '../use-recommendation-stream';

vi.mock('@/hooks/use-sse', () => ({
  useSse: vi.fn(),
}));

import { useSse } from '@/hooks/use-sse';

const mockClose = vi.fn();

function makeChunk(type: string, data: object) {
  return JSON.stringify({ type, data });
}

const completeChunk = makeChunk('complete', {
  productId: 1,
  productName: '상품A',
  reason: '이유',
  conditions: ['조건1'],
  score: 0.9,
});

describe('useRecommendationStream', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useSse).mockReturnValue({ isConnected: false, close: mockClose });
  });

  it('enabled=false이면 SSE 연결 옵션이 false로 전달된다', () => {
    renderHook(() => useRecommendationStream('돌잔치', false));

    expect(vi.mocked(useSse)).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ enabled: false }),
    );
  });

  it('query 변경 시 results가 초기화된다', () => {
    let capturedOnMessage: ((data: string) => void) | undefined;
    vi.mocked(useSse).mockImplementation((_url, options) => {
      capturedOnMessage = options?.onMessage;
      return { isConnected: true, close: mockClose };
    });

    const { result, rerender } = renderHook(
      ({ query }) => useRecommendationStream(query, true),
      { initialProps: { query: '돌잔치' } },
    );

    act(() => { capturedOnMessage?.(completeChunk); });
    expect(result.current.results).toHaveLength(1);

    rerender({ query: '생일선물' });

    expect(result.current.results).toHaveLength(0);
  });

  it('type: complete 메시지 수신 시 results에 항목이 추가된다', () => {
    let capturedOnMessage: ((data: string) => void) | undefined;
    vi.mocked(useSse).mockImplementation((_url, options) => {
      capturedOnMessage = options?.onMessage;
      return { isConnected: true, close: mockClose };
    });

    const { result } = renderHook(() => useRecommendationStream('돌잔치', true));

    act(() => { capturedOnMessage?.(completeChunk); });

    expect(result.current.results).toHaveLength(1);
    expect(result.current.results[0].productId).toBe(1);
    expect(result.current.results[0].productName).toBe('상품A');
  });

  it('type: error 메시지 수신 시 isComplete=true가 되고 연결이 종료된다', () => {
    let capturedOnMessage: ((data: string) => void) | undefined;
    vi.mocked(useSse).mockImplementation((_url, options) => {
      capturedOnMessage = options?.onMessage;
      return { isConnected: true, close: mockClose };
    });

    const { result } = renderHook(() => useRecommendationStream('돌잔치', true));

    act(() => { capturedOnMessage?.(makeChunk('error', {})); });

    expect(result.current.isComplete).toBe(true);
    expect(mockClose).toHaveBeenCalled();
  });

  it('onError 발생 시 isComplete=true가 된다', () => {
    let capturedOnError: ((err: Event) => void) | undefined;
    vi.mocked(useSse).mockImplementation((_url, options) => {
      capturedOnError = options?.onError;
      return { isConnected: false, close: mockClose };
    });

    const { result } = renderHook(() => useRecommendationStream('돌잔치', true));

    act(() => { capturedOnError?.(new Event('error')); });

    expect(result.current.isComplete).toBe(true);
  });
});
