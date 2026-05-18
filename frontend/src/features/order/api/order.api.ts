import { apiClient } from '@/lib/axios';
import { API_ENDPOINTS } from '@/constants/api.constants';
import type { Order, OrderCreateInput } from '../types/order.types';
import type { ApiResponse, PageResponse } from '@/types/api.types';

export const orderApi = {
  createOrder: (body: OrderCreateInput) =>
    apiClient
      .post<ApiResponse<Order>>(API_ENDPOINTS.orders.root, body)
      .then((r) => r.data),

  getOrders: (page = 0, size = 10) =>
    apiClient
      .get<ApiResponse<PageResponse<Order>>>(API_ENDPOINTS.orders.root, {
        params: { page, size, sort: 'createdAt,desc' },
      })
      .then((r) => r.data),

  getOrder: (id: number) =>
    apiClient
      .get<ApiResponse<Order>>(API_ENDPOINTS.orders.detail(id))
      .then((r) => r.data),

  cancelOrder: (id: number) =>
    apiClient
      .delete<ApiResponse<void>>(API_ENDPOINTS.orders.detail(id))
      .then((r) => r.data),
};