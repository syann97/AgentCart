import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { usePay } from '../use-pay';

vi.mock('../../api/payment.api', () => ({
  paymentApi: { pay: vi.fn() },
}));

import { paymentApi } from '../../api/payment.api';

const mockResponse = {
  success: true,
  data: {
    id: 1,
    orderId: 100,
    status: 'COMPLETED' as const,
    amount: 10000,
    paymentKey: 'mock-key',
    paidAt: '2024-01-01T00:00:00',
    createdAt: '2024-01-01T00:00:00',
  },
  timestamp: '',
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

describe('usePay', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('paymentApi.pay를 호출한다', async () => {
    vi.mocked(paymentApi.pay).mockResolvedValue(mockResponse);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => usePay(), { wrapper });

    act(() => { result.current.mutate({ orderId: 100 }); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(paymentApi.pay).toHaveBeenCalledWith({ orderId: 100 });
  });

  it('성공 시 orders 캐시를 무효화한다', async () => {
    vi.mocked(paymentApi.pay).mockResolvedValue(mockResponse);

    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => usePay(), { wrapper });

    act(() => { result.current.mutate({ orderId: 100 }); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['orders'] });
  });

  it('성공 시 onSuccess 콜백을 호출한다', async () => {
    vi.mocked(paymentApi.pay).mockResolvedValue(mockResponse);

    const onSuccess = vi.fn();
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => usePay({ onSuccess }), { wrapper });

    act(() => { result.current.mutate({ orderId: 100 }); });

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(mockResponse));
  });

  it('실패 시 onError 콜백을 호출한다', async () => {
    const error = new Error('결제 실패');
    vi.mocked(paymentApi.pay).mockRejectedValue(error);

    const onError = vi.fn();
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => usePay({ onError }), { wrapper });

    act(() => { result.current.mutate({ orderId: 100 }); });

    await waitFor(() => expect(onError).toHaveBeenCalled());
    expect(onError.mock.calls[0][0]).toEqual(error);
  });
});