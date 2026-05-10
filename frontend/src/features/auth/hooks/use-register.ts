'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { authApi } from '../api/auth.api';
import type { RegisterFormValues } from '../types/auth.types';

export function useRegister() {
  const router = useRouter();

  return useMutation({
    mutationFn: ({ passwordConfirm: _, ...values }: RegisterFormValues) =>
      authApi.register(values),
    onSuccess: () => {
      setTimeout(() => router.push('/login'), 1500);
    },
  });
}
