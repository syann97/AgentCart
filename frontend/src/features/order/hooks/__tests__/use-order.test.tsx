import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useOrder } from '../use-order';

vi.mock('../../api/order.api', () => ({
  orderApi: { getOrder: vi.fn() },
}));

vi.mock('@/stores/auth.store', () => ({
  useAuthStore: vi.fn(),
}));

import { orderApi } from '../../api/order.api';
import { useAuthStore } from '@/stores/auth.store';

const mockMember = { id: 1, email: 'test@test.com', name: '테스트', role: 'USER' as const };

const mockResponse = {
  success: true,
  data: {
    id: 1,
    status: 'PENDING' as const,
    totalPrice: 10000,
    items: [],
    recipientName: '홍길동',
    phone: '010-1234-5678',
    address: '서울시',
    addressDetail: null,
    createdAt: '2024-01-01T00:00:00',
  },
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

describe('useOrder', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('id로 주문 상세를 조회한다', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector) =>
      selector({ member: mockMember } as any),
    );
    vi.mocked(orderApi.getOrder).mockResolvedValue(mockResponse);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useOrder(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(orderApi.getOrder).toHaveBeenCalledWith(1);
    expect(result.current.data).toEqual(mockResponse);
  });

  it('미인증 상태에서는 조회하지 않는다', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector) =>
      selector({ member: null } as any),
    );

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useOrder(1), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(orderApi.getOrder).not.toHaveBeenCalled();
  });
});