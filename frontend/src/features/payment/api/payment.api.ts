import { apiClient } from '@/lib/axios';
import { API_ENDPOINTS } from '@/constants/api.constants';
import type { Payment, PaymentCreateInput } from '../types/payment.types';
import type { ApiResponse } from '@/types/api.types';

export const paymentApi = {
  pay: (body: PaymentCreateInput) =>
    apiClient
      .post<ApiResponse<Payment>>(API_ENDPOINTS.payments.root, body)
      .then((r) => r.data),

  getPayment: (id: number) =>
    apiClient
      .get<ApiResponse<Payment>>(API_ENDPOINTS.payments.detail(id))
      .then((r) => r.data),

  getPaymentByOrder: (orderId: number) =>
    apiClient
      .get<ApiResponse<Payment>>(API_ENDPOINTS.payments.byOrder(orderId))
      .then((r) => r.data),
};