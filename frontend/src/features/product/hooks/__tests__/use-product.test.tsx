import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useProduct } from '../use-product';

vi.mock('../../api/product.api', () => ({
  productApi: { detail: vi.fn() },
}));

import { productApi } from '../../api/product.api';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return { wrapper: Wrapper, queryClient };
}

describe('useProduct', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('id로 productApi.detail을 호출하고 상세 데이터를 반환한다', async () => {
    const mockData = {
      success: true,
      data: { id: 1, name: 'Laptop', price: 999, category: 'electronics', brand: null, status: 'ACTIVE' as const, description: null, stock: 5, createdAt: '', updatedAt: null },
      timestamp: '',
    };
    vi.mocked(productApi.detail).mockResolvedValue(mockData);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useProduct(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(productApi.detail).toHaveBeenCalledWith(1);
    expect(result.current.data).toEqual(mockData);
  });
});
