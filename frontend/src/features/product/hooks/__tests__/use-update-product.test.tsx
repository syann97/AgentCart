import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUpdateProduct } from '../use-update-product';

vi.mock('../../api/product.api', () => ({
  productApi: { update: vi.fn() },
}));

import { productApi } from '../../api/product.api';

const PRODUCT_ID = 1;
const validBody = { name: 'Updated Laptop', price: 1099, category: 'electronics', stock: 5 };
const mockResponse = {
  success: true,
  data: { id: PRODUCT_ID, ...validBody, brand: null, status: 'ACTIVE', description: null, createdAt: '', updatedAt: '' },
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

describe('useUpdateProduct', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('productApi.update를 id와 함께 호출한다', async () => {
    vi.mocked(productApi.update).mockResolvedValue(mockResponse);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useUpdateProduct(PRODUCT_ID), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(productApi.update).toHaveBeenCalledWith(PRODUCT_ID, validBody);
  });

  it('성공 시 products와 product(id) 캐시를 모두 무효화한다', async () => {
    vi.mocked(productApi.update).mockResolvedValue(mockResponse);

    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateProduct(PRODUCT_ID), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['products'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['products', PRODUCT_ID] });
  });

  it('성공 시 onSuccess 콜백을 호출한다', async () => {
    vi.mocked(productApi.update).mockResolvedValue(mockResponse);

    const onSuccess = vi.fn();
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useUpdateProduct(PRODUCT_ID, { onSuccess }), { wrapper });

    act(() => { result.current.mutate(validBody); });

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(mockResponse));
  });
});
