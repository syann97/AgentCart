import { TOKEN_KEYS } from '@/constants/api.constants';

export const tokenUtils = {
  get(): string | null {
    if (typeof window === 'undefined') return null;
    return sessionStorage.getItem(TOKEN_KEYS.accessToken);
  },

  set(token: string): void {
    sessionStorage.setItem(TOKEN_KEYS.accessToken, token);
  },

  clear(): void {
    sessionStorage.removeItem(TOKEN_KEYS.accessToken);
  },
};
