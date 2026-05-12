import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RegisterForm } from '../RegisterForm';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock('../../hooks/use-register', () => ({
  useRegister: () => ({
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
  }),
}));

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      {children}
    </QueryClientProvider>
  );
}

describe('RegisterForm', () => {
  it('이메일, 이름, 닉네임, 비밀번호, 비밀번호 확인 필드와 회원가입 버튼을 렌더링한다', () => {
    render(<RegisterForm />, { wrapper });

    expect(screen.getByLabelText('이메일')).toBeInTheDocument();
    expect(screen.getByLabelText('이름')).toBeInTheDocument();
    expect(screen.getByLabelText('닉네임')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호 확인')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '회원가입' })).toBeInTheDocument();
  });

  it('빈 폼 제출 시 필수 입력 에러 메시지를 표시한다', async () => {
    render(<RegisterForm />, { wrapper });

    await userEvent.click(screen.getByRole('button', { name: '회원가입' }));

    await waitFor(() => {
      expect(screen.getByText('이메일을 입력해주세요')).toBeInTheDocument();
      expect(screen.getByText('이름을 입력해주세요')).toBeInTheDocument();
      expect(screen.getByText('닉네임을 입력해주세요')).toBeInTheDocument();
      expect(screen.getByText('비밀번호를 입력해주세요')).toBeInTheDocument();
    });
  });

  it('비밀번호와 비밀번호 확인이 다르면 에러 메시지를 표시한다', async () => {
    render(<RegisterForm />, { wrapper });

    await userEvent.type(screen.getByLabelText('이메일'), 'test@example.com');
    await userEvent.type(screen.getByLabelText('이름'), '홍길동');
    await userEvent.type(screen.getByLabelText('닉네임'), 'tester');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password123');
    await userEvent.type(screen.getByLabelText('비밀번호 확인'), 'different123');
    await userEvent.click(screen.getByRole('button', { name: '회원가입' }));

    await waitFor(() => {
      expect(screen.getByText('비밀번호가 일치하지 않습니다')).toBeInTheDocument();
    });
  });

  it('유효한 값 제출 시 register mutate를 호출한다', async () => {
    const mutate = vi.fn();
    vi.mocked(await import('../../hooks/use-register')).useRegister = () => ({
      mutate,
      isPending: false,
      isSuccess: false,
    });

    render(<RegisterForm />, { wrapper });

    await userEvent.type(screen.getByLabelText('이메일'), 'test@example.com');
    await userEvent.type(screen.getByLabelText('이름'), '홍길동');
    await userEvent.type(screen.getByLabelText('닉네임'), 'tester');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'password123');
    await userEvent.type(screen.getByLabelText('비밀번호 확인'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: '회원가입' }));

    await waitFor(() => {
      expect(mutate).toHaveBeenCalledWith(
        expect.objectContaining({
          email: 'test@example.com',
          name: '홍길동',
          nickname: 'tester',
          password: 'password123',
        }),
        expect.anything(),
      );
    });
  });
});