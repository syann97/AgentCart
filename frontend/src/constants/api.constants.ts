export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export const API_ENDPOINTS = {
  auth: {
    login: '/api/auth/login',
    register: '/api/auth/register',
    refresh: '/api/auth/refresh',
    logout: '/api/auth/logout',
    me: '/api/auth/me',
  },
  products: {
    list: '/api/products',
    detail: (id: number) => `/api/products/${id}`,
  },
  recommendations: {
    stream: '/api/recommendations/stream',
    history: '/api/recommendations/history',
  },
  cart: {
    root: '/api/cart',
    items: '/api/cart/items',
    item: (id: number) => `/api/cart/items/${id}`,
  },
  orders: {
    root: '/api/orders',
    detail: (id: number) => `/api/orders/${id}`,
  },
} as const;

export const TOKEN_KEYS = {
  accessToken: 'access_token',
} as const;

export const QUERY_KEYS = {
  products: ['products'] as const,
  product: (id: number) => ['products', id] as const,
  cart: ['cart'] as const,
  orders: ['orders'] as const,
  recommendations: ['recommendations'] as const,
} as const;