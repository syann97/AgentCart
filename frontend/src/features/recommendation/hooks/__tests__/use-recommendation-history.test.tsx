import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useRecommendationHistory } from '../use-recommendation-history';

vi.mock('../../api/recommendation.api', () => ({
  getRecommendationHistory: vi.fn(),
}));

import { getRecommendationHistory } from '../../api/recommendation.api';

const mockHistory = [
  { productId: 1, productName: '상품A', reason: '이유A', score: 0.9, recommendedAt: '2024-01-01T00:00:00' },
  { productId: 2, productName: '상품B', reason: '이유B', score: 0.8, recommendedAt: '2024-01-02T00:00:00' },
];

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return { wrapper: Wrapper };
}

describe('useRecommendationHistory', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('정상 응답 시 히스토리 데이터를 반환한다', async () => {
    vi.mocked(getRecommendationHistory).mockResolvedValue(mockHistory);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useRecommendationHistory(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockHistory);
  });

  it('API 에러 시 에러 상태가 된다', async () => {
    vi.mocked(getRecommendationHistory).mockRejectedValue(new Error('Unauthorized'));

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useRecommendationHistory(), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});