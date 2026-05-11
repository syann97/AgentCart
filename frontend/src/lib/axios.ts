import axios, { type AxiosError } from 'axios';
import { API_BASE_URL, API_ENDPOINTS } from '@/constants/api.constants';
import { tokenUtils } from '@/utils/token.utils';
import type { ApiResponse } from '@/types/api.types';
import type { TokenResponse } from '@/features/auth/types/auth.types';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    'ngrok-skip-browser-warning': '69420',
  },
  withCredentials: true,
});

apiClient.interceptors.request.use((config) => {
  const token = tokenUtils.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let isRefreshing = false;
let pendingQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = [];

const drainQueue = (token: string | null, error: unknown = null) => {
  pendingQueue.forEach((p) => (token ? p.resolve(token) : p.reject(error)));
  pendingQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config;
    if (!original || error.response?.status !== 401) return Promise.reject(error);

    const isAuthEndpoint =
      original.url === API_ENDPOINTS.auth.login || original.url === API_ENDPOINTS.auth.refresh;
    if (isAuthEndpoint) return Promise.reject(error);

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        pendingQueue.push({
          resolve: (token) => {
            original.headers.Authorization = `Bearer ${token}`;
            resolve(apiClient(original));
          },
          reject,
        });
      });
    }

    isRefreshing = true;
    try {
      const { data } = await apiClient.post<ApiResponse<TokenResponse>>(API_ENDPOINTS.auth.refresh);
      const newToken = data.data.accessToken;
      tokenUtils.set(newToken);
      drainQueue(newToken);
      original.headers.Authorization = `Bearer ${newToken}`;
      return apiClient(original);
    } catch (refreshError) {
      tokenUtils.clear();
      drainQueue(null, refreshError);
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);