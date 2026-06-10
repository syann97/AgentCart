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

  it('start 호출 전에는 SSE 연결 옵션이 false로 전달된다', () => {
    renderHook(() => useRecommendationStream());

    expect(vi.mocked(useSse)).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ enabled: false }),
    );
  });

  it('start 호출 시 SSE 연결이 활성화되고 isSearching=true가 된다', () => {
    const { result } = renderHook(() => useRecommendationStream());

    act(() => { result.current.start('돌잔치'); });

    expect(vi.mocked(useSse)).toHaveBeenLastCalledWith(
      expect.stringContaining(encodeURIComponent('돌잔치')),
      expect.objectContaining({ enabled: true }),
    );
    expect(result.current.isSearching).toBe(true);
  });

  it('새 검색 시작 시 results가 초기화된다', () => {
    let capturedOnMessage: ((data: string) => void) | undefined;
    vi.mocked(useSse).mockImplementation((_url, options) => {
      capturedOnMessage = options?.onMessage;
      return { isConnected: true, close: mockClose };
    });

    const { result } = renderHook(() => useRecommendationStream());

    act(() => { result.current.start('돌잔치'); });
    act(() => { capturedOnMessage?.(completeChunk); });
    expect(result.current.results).toHaveLength(1);

    act(() => { result.current.start('생일선물'); });

    expect(result.current.results).toHaveLength(0);
  });

  it('type: complete 메시지 수신 시 results에 항목이 추가된다', () => {
    let capturedOnMessage: ((data: string) => void) | undefined;
    vi.mocked(useSse).mockImplementation((_url, options) => {
      capturedOnMessage = options?.onMessage;
      return { isConnected: true, close: mockClose };
    });

    const { result } = renderHook(() => useRecommendationStream());

    act(() => { result.current.start('돌잔치'); });
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

    const { result } = renderHook(() => useRecommendationStream());

    act(() => { result.current.start('돌잔치'); });
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

    const { result } = renderHook(() => useRecommendationStream());

    act(() => { result.current.start('돌잔치'); });
    act(() => { capturedOnError?.(new Event('error')); });

    expect(result.current.isComplete).toBe(true);
  });

  it('이전 검색 완료 후 재검색 시 isSearching=true, 연결 enabled=true가 유지된다 (#137 회귀)', () => {
    let capturedOnError: ((err: Event) => void) | undefined;
    vi.mocked(useSse).mockImplementation((_url, options) => {
      capturedOnError = options?.onError;
      return { isConnected: false, close: mockClose };
    });

    const { result } = renderHook(() => useRecommendationStream());

    // 1번째 검색 정상 완료 → isComplete=true 상태에서
    act(() => { result.current.start('돌잔치'); });
    act(() => { capturedOnError?.(new Event('error')); });
    expect(result.current.isComplete).toBe(true);
    expect(result.current.isSearching).toBe(false);

    // 2번째 검색 시작 — 리셋이 원자적이라 stale isComplete로 죽지 않아야 함
    act(() => { result.current.start('신발 추천'); });

    expect(result.current.isComplete).toBe(false);
    expect(result.current.isSearching).toBe(true);
    expect(vi.mocked(useSse)).toHaveBeenLastCalledWith(
      expect.stringContaining(encodeURIComponent('신발 추천')),
      expect.objectContaining({ enabled: true }),
    );
  });
});
