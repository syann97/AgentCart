import { apiClient } from '@/lib/axios';
import { API_ENDPOINTS } from '@/constants/api.constants';
import type { ProductSummary, ProductDetail, ProductCreateInput, ProductUpdateInput } from '../types/product.types';
import type { ApiResponse, PageResponse } from '@/types/api.types';

export const productApi = {
  list: (params?: { search?: string; page?: number; size?: number }) =>
    apiClient
      .get<ApiResponse<PageResponse<ProductSummary>>>(API_ENDPOINTS.products.list, { params })
      .then((r) => r.data),

  detail: (id: number) =>
    apiClient
      .get<ApiResponse<ProductDetail>>(API_ENDPOINTS.products.detail(id))
      .then((r) => r.data),

  create: (body: ProductCreateInput) =>
    apiClient
      .post<ApiResponse<ProductDetail>>(API_ENDPOINTS.products.list, body)
      .then((r) => r.data),

  update: (id: number, body: ProductUpdateInput) =>
    apiClient
      .put<ApiResponse<ProductDetail>>(API_ENDPOINTS.products.detail(id), body)
      .then((r) => r.data),

  delete: (id: number) =>
    apiClient
      .delete<ApiResponse<void>>(API_ENDPOINTS.products.detail(id))
      .then((r) => r.data),
};