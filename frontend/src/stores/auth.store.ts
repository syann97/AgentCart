import { create } from 'zustand';
import { tokenUtils } from '@/utils/token.utils';
import type { MemberInfo } from '@/features/auth/types/auth.types';

interface AuthState {
  member: MemberInfo | null;
  isAuthenticated: boolean;
  setAuth: (member: MemberInfo, accessToken: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  member: null,
  isAuthenticated: false,

  setAuth: (member, accessToken) => {
    tokenUtils.set(accessToken);
    set({ member, isAuthenticated: true });
  },

  clearAuth: () => {
    tokenUtils.clear();
    set({ member: null, isAuthenticated: false });
  },
}));