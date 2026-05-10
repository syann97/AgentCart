import { apiClient } from '@/lib/axios';
import { API_ENDPOINTS } from '@/constants/api.constants';
import type {
  LoginFormValues,
  RegisterFormValues,
  LoginResponseData,
  RegisterResponseData,
} from '../types/auth.types';
import type { ApiResponse } from '@/types/api.types';

export const authApi = {
  login: (body: LoginFormValues) =>
    apiClient
      .post<ApiResponse<LoginResponseData>>(API_ENDPOINTS.auth.login, body)
      .then((r) => r.data),

  register: (body: Omit<RegisterFormValues, 'passwordConfirm'>) =>
    apiClient
      .post<ApiResponse<RegisterResponseData>>(API_ENDPOINTS.auth.register, body)
      .then((r) => r.data),

  logout: () => apiClient.post(API_ENDPOINTS.auth.logout),
};