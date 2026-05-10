'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import Link from 'next/link';
import type { AxiosError } from 'axios';
import { registerSchema, type RegisterFormValues } from '../types/auth.types';
import { useRegister } from '../hooks/use-register';
import { FormField } from '@/components/ui/FormField';
import { Button } from '@/components/ui/Button';
import type { ApiError } from '@/types/api.types';

export function RegisterForm() {
  const { mutate: register, isPending, isSuccess } = useRegister();

  const {
    register: field,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) });

  function onSubmit(values: RegisterFormValues) {
    register(values, {
      onError: (err) => {
        const axiosError = err as AxiosError<ApiError>;
        const errorCode = axiosError.response?.data?.errorCode;
        const message = axiosError.response?.data?.message;
        const fields = axiosError.response?.data?.fields;

        if (errorCode === 'DUPLICATE_EMAIL') {
          setError('email', { message: '이미 사용 중인 이메일입니다' });
          setFocus('email');
        } else if (errorCode === 'DUPLICATE_NICKNAME') {
          setError('nickname', { message: '이미 사용 중인 닉네임입니다' });
          setFocus('nickname');
        } else if (fields && Object.keys(fields).length > 0) {
          const formFields = ['email', 'password', 'name', 'nickname'] as const;
          let firstErrorField: (typeof formFields)[number] | undefined;
          formFields.forEach((f) => {
            if (fields[f]) {
              setError(f, { message: fields[f] });
              if (!firstErrorField) firstErrorField = f;
            }
          });
          if (firstErrorField) setFocus(firstErrorField);
        } else {
          setError('root', {
            message: message ?? '회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.',
          });
        }
      },
    });
  }

  if (isSuccess) {
    return (
      <div className="flex flex-col items-center gap-2 w-full max-w-sm py-8">
        <p className="text-sm font-semibold text-green-600">회원가입이 완료되었습니다!</p>
        <p className="text-sm text-gray-500">잠시 후 로그인 페이지로 이동합니다.</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4 w-full max-w-sm">
      <FormField
        id="email"
        label="이메일"
        type="email"
        placeholder="example@agentcart.com"
        error={errors.email}
        {...field('email')}
      />
      <FormField
        id="name"
        label="이름"
        type="text"
        placeholder="홍길동"
        error={errors.name}
        {...field('name')}
      />
      <FormField
        id="nickname"
        label="닉네임"
        type="text"
        placeholder="2~20자 입력"
        error={errors.nickname}
        {...field('nickname')}
      />
      <FormField
        id="password"
        label="비밀번호"
        type="password"
        placeholder="8자 이상"
        error={errors.password}
        {...field('password')}
      />
      <FormField
        id="passwordConfirm"
        label="비밀번호 확인"
        type="password"
        placeholder="비밀번호를 다시 입력해주세요"
        error={errors.passwordConfirm}
        {...field('passwordConfirm')}
      />

      {errors.root && <p className="text-xs text-red-500">{errors.root.message}</p>}

      <Button type="submit" loading={isPending} loadingText="처리 중...">
        회원가입
      </Button>

      <p className="text-sm text-center text-gray-500">
        이미 계정이 있으신가요?{' '}
        <Link href="/login" className="text-blue-600 hover:underline">
          로그인
        </Link>
      </p>
    </form>
  );
}
