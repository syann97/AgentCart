'use client';

import type { ReactNode } from 'react';
import { useAuthGuard } from '@/hooks/use-auth-guard';

export function ProtectedLayout({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuthGuard();

  if (!isAuthenticated) {
    return null;
  }

  return <>{children}</>;
}
