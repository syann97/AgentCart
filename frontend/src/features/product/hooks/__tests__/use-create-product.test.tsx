import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCreateProduct } from '../use-create-product';

vi.mock('../../api/product.api', () => ({
  productApi: { create: vi.fn() },
}));

import { productApi } from '../../api/product.api';

const validBody = { name: 'Laptop', price: 999, category: 'electronics', stock: 10 };
const mockResponse = {
  success: true,
  data: { id: 1, ...validBody, brand: null, status: 'ACTIVE' as const, description: null, createdAt: '', updatedAt: null },
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

describe('useCreateProduct', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('productApi.create를 호출한다', async () => {
    vi.mocked(productApi.create).mockResolvedValue(mockResponse);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateProduct(), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(productApi.create).toHaveBeenCalledWith(validBody);
  });

  it('성공 시 products 캐시를 무효화한다', async () => {
    vi.mocked(productApi.create).mockResolvedValue(mockResponse);

    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCreateProduct(), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['products'] });
  });

  it('성공 시 onSuccess 콜백을 호출한다', async () => {
    vi.mocked(productApi.create).mockResolvedValue(mockResponse);

    const onSuccess = vi.fn();
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateProduct({ onSuccess }), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(mockResponse));
  });

  it('실패 시 onError 콜백을 호출한다', async () => {
    const error = new Error('서버 오류');
    vi.mocked(productApi.create).mockRejectedValue(error);

    const onError = vi.fn();
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateProduct({ onError }), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(onError).toHaveBeenCalled());
    expect(onError.mock.calls[0][0]).toEqual(error);
  });
});
