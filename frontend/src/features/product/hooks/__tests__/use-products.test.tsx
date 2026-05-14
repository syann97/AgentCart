import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useProducts } from '../use-products';

vi.mock('../../api/product.api', () => ({
  productApi: { list: vi.fn() },
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

describe('useProducts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('카테고리·페이지 파라미터를 productApi.list에 전달한다', async () => {
    const mockData = { success: true, data: { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0, last: true }, timestamp: '' };
    vi.mocked(productApi.list).mockResolvedValue(mockData);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useProducts({ category: 'electronics', page: 0 }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(productApi.list).toHaveBeenCalledWith({ category: 'electronics', page: 0 });
  });

  it('파라미터 없이 호출하면 전체 목록 데이터를 반환한다', async () => {
    const mockData = { success: true, data: { content: [], totalElements: 5, totalPages: 1, size: 20, number: 0, last: true }, timestamp: '' };
    vi.mocked(productApi.list).mockResolvedValue(mockData);

    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useProducts(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(productApi.list).toHaveBeenCalledWith(undefined);
    expect(result.current.data).toEqual(mockData);
  });
});
