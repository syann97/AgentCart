'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import type { AxiosError } from 'axios';
import { loginSchema, type LoginFormValues } from '../types/auth.types';
import { useLogin } from '../hooks/use-login';
import { FormField } from '@/components/ui/FormField';
import { Button } from '@/components/ui/Button';
import type { ApiError } from '@/types/api.types';

export function LoginForm() {
  const { mutate: login, isPending } = useLogin();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  function onSubmit(values: LoginFormValues) {
    login(values, {
      onError: (err) => {
        const axiosError = err as AxiosError<ApiError>;
        const status = axiosError.response?.status;
        const errorCode = axiosError.response?.data?.errorCode;

        if (status === 401 || errorCode === 'INVALID_CREDENTIALS') {
          setError('root', { message: '이메일 또는 비밀번호가 올바르지 않습니다.' });
        } else if (!axiosError.response) {
          setError('root', { message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' });
        } else {
          setError('root', { message: '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.' });
        }
      },
    });
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4 w-full max-w-sm">
      <FormField
        id="email"
        label="이메일"
        type="email"
        placeholder="email@example.com"
        error={errors.email}
        {...register('email')}
      />
      <FormField
        id="password"
        label="비밀번호"
        type="password"
        placeholder="비밀번호"
        error={errors.password}
        {...register('password')}
      />

      {errors.root && <p className="text-xs text-red-500">{errors.root.message}</p>}

      <Button type="submit" loading={isPending} loadingText="로그인 중...">
        로그인
      </Button>

      <p className="text-sm text-center text-gray-500">
        계정이 없으신가요?{' '}
        <Link href="/register" className="text-blue-600 hover:underline">
          회원가입
        </Link>
      </p>
    </form>
  );
}
