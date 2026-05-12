import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useAuthGuard } from '../use-auth-guard';

const mockReplace = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

vi.mock('@/stores/auth.store', () => ({
  useAuthStore: vi.fn(),
}));

import { useAuthStore } from '@/stores/auth.store';

describe('useAuthGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('미인증 상태이면 /login으로 리다이렉트한다', () => {
    vi.mocked(useAuthStore).mockImplementation((selector) =>
      selector({ isAuthenticated: false, member: null, setAuth: vi.fn(), clearAuth: vi.fn() }),
    );

    renderHook(() => useAuthGuard());

    expect(mockReplace).toHaveBeenCalledWith('/login');
  });

  it('인증 상태이면 리다이렉트하지 않는다', () => {
    vi.mocked(useAuthStore).mockImplementation((selector) =>
      selector({ isAuthenticated: true, member: null, setAuth: vi.fn(), clearAuth: vi.fn() }),
    );

    renderHook(() => useAuthGuard());

    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('isAuthenticated 값을 반환한다', () => {
    vi.mocked(useAuthStore).mockImplementation((selector) =>
      selector({ isAuthenticated: true, member: null, setAuth: vi.fn(), clearAuth: vi.fn() }),
    );

    const { result } = renderHook(() => useAuthGuard());

    expect(result.current.isAuthenticated).toBe(true);
  });
});
