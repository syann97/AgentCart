import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useOrders } from '../use-orders';

vi.mock('../../api/order.api', () => ({
  orderApi: { getOrders: vi.fn() },
}));

vi.mock('@/stores/auth.store', () => ({
  useAuthStore: vi.fn(),
}));

import { orderApi } from '../../api/order.api';
import { useAuthStore } from '@/stores/auth.store';

const mockMember = { id: 1, email: 'test@test.com', name: '테스트', role: 'USER' as const };

const mockResponse = {
  success: true,
  data: { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, last: true },
  timestamp: '',
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return { wrapper: Wrapper };
}

describe('useOrders', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('인증된 사용자의 주문 목록을 조회한다', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector: (s: unknown) => unknown) =>
      selector({ member: mockMember }),
    );
    vi.mocked(orderApi.getOrders).mockResolvedValue(mockResponse);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useOrders(0), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(orderApi.getOrders).toHaveBeenCalledWith(0);
    expect(result.current.data).toEqual(mockResponse);
  });

  it('미인증 상태에서는 조회하지 않는다', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector: (s: unknown) => unknown) =>
      selector({ member: null }),
    );

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useOrders(), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(orderApi.getOrders).not.toHaveBeenCalled();
  });
});