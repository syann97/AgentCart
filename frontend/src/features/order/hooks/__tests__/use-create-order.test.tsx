import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCreateOrder } from '../use-create-order';

vi.mock('../../api/order.api', () => ({
  orderApi: { createOrder: vi.fn() },
}));

import { orderApi } from '../../api/order.api';

const mockOrder = {
  id: 1,
  status: 'PENDING' as const,
  totalPrice: 10000,
  items: [],
  recipientName: '홍길동',
  phone: '010-1234-5678',
  address: '서울시',
  addressDetail: null,
  createdAt: '2024-01-01T00:00:00',
};

const mockResponse = { success: true, data: mockOrder, timestamp: '' };

const validBody = {
  productId: 1,
  quantity: 2,
  recipientName: '홍길동',
  phone: '010-1234-5678',
  address: '서울시',
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return { wrapper: Wrapper, queryClient };
}

describe('useCreateOrder', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('orderApi.createOrder를 호출한다', async () => {
    vi.mocked(orderApi.createOrder).mockResolvedValue(mockResponse);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateOrder(), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(orderApi.createOrder).toHaveBeenCalledWith(validBody);
  });

  it('성공 시 orders 및 cart 캐시를 무효화한다', async () => {
    vi.mocked(orderApi.createOrder).mockResolvedValue(mockResponse);

    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCreateOrder(), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['orders'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['cart'] });
  });

  it('성공 시 onSuccess 콜백을 호출한다', async () => {
    vi.mocked(orderApi.createOrder).mockResolvedValue(mockResponse);

    const onSuccess = vi.fn();
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateOrder({ onSuccess }), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(mockResponse));
  });

  it('실패 시 onError 콜백을 호출한다', async () => {
    const error = new Error('서버 오류');
    vi.mocked(orderApi.createOrder).mockRejectedValue(error);

    const onError = vi.fn();
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateOrder({ onError }), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(onError).toHaveBeenCalled());
    expect(onError.mock.calls[0][0]).toEqual(error);
  });
});