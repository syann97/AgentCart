import { apiClient } from '@/lib/axios';
import { API_ENDPOINTS } from '@/constants/api.constants';
import type { Cart, CartItem, CartItemAddInput, CartItemUpdateInput } from '../types/cart.types';
import type { ApiResponse } from '@/types/api.types';

export const cartApi = {
  getCart: () =>
    apiClient
      .get<ApiResponse<Cart>>(API_ENDPOINTS.cart.root)
      .then((r) => r.data),

  addItem: (body: CartItemAddInput) =>
    apiClient
      .post<ApiResponse<CartItem>>(API_ENDPOINTS.cart.items, body)
      .then((r) => r.data),

  updateItem: (itemId: number, body: CartItemUpdateInput) =>
    apiClient
      .put<ApiResponse<CartItem>>(API_ENDPOINTS.cart.item(itemId), body)
      .then((r) => r.data),

  removeItem: (itemId: number) =>
    apiClient
      .delete<ApiResponse<void>>(API_ENDPOINTS.cart.item(itemId))
      .then((r) => r.data),

  clearCart: () =>
    apiClient
      .delete<ApiResponse<void>>(API_ENDPOINTS.cart.root)
      .then((r) => r.data),
};