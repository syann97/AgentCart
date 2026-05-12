import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LoginForm } from '../LoginForm';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock('../../hooks/use-login', () => ({
  useLogin: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      {children}
    </QueryClientProvider>
  );
}

describe('LoginForm', () => {
  it('이메일, 비밀번호 필드와 로그인 버튼을 렌더링한다', () => {
    render(<LoginForm />, { wrapper });

    expect(screen.getByLabelText('이메일')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  });

  it('이메일 형식이 잘못되면 에러 메시지를 표시한다', async () => {
    render(<LoginForm />, { wrapper });

    await userEvent.type(screen.getByLabelText('이메일'), 'invalid-email');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(screen.getByText('올바른 이메일을 입력해주세요')).toBeInTheDocument();
    });
  });

  it('비밀번호가 8자 미만이면 에러 메시지를 표시한다', async () => {
    render(<LoginForm />, { wrapper });

    await userEvent.type(screen.getByLabelText('이메일'), 'test@example.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'short');
    await userEvent.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(screen.getByText('비밀번호는 8자 이상이어야 합니다')).toBeInTheDocument();
    });
  });

  it('유효한 값 제출 시 login mutate를 호출한다', async () => {
    const mutate = vi.fn();
    vi.mocked(await import('../../hooks/use-login')).useLogin = () => ({
      mutate,
      isPending: false,
    });

    render(<LoginForm />, { wrapper });

    await userEvent.type(screen.getByLabelText('이메일'), 'test@example.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(mutate).toHaveBeenCalledWith(
        { email: 'test@example.com', password: 'password123' },
        expect.anything(),
      );
    });
  });
});
