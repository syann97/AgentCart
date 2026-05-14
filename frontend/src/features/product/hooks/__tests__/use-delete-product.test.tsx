import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useDeleteProduct } from '../use-delete-product';

vi.mock('../../api/product.api', () => ({
  productApi: { delete: vi.fn() },
}));

import { productApi } from '../../api/product.api';

const PRODUCT_ID = 1;
const mockResponse = { success: true, data: undefined, timestamp: '' };

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return { wrapper: Wrapper, queryClient };
}

describe('useDeleteProduct', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('productApi.delete를 id와 함께 호출한다', async () => {
    vi.mocked(productApi.delete).mockResolvedValue(mockResponse);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useDeleteProduct(), { wrapper });

    act(() => { result.current.mutate(PRODUCT_ID); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(productApi.delete).toHaveBeenCalledWith(PRODUCT_ID);
  });

  it('성공 시 products 캐시를 무효화한다', async () => {
    vi.mocked(productApi.delete).mockResolvedValue(mockResponse);

    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteProduct(), { wrapper });

    act(() => { result.current.mutate(PRODUCT_ID); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['products'] });
  });

  it('성공 시 onSuccess 콜백을 호출한다', async () => {
    vi.mocked(productApi.delete).mockResolvedValue(mockResponse);

    const onSuccess = vi.fn();
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useDeleteProduct({ onSuccess }), { wrapper });

    act(() => { result.current.mutate(PRODUCT_ID); });

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(mockResponse));
  });
});
