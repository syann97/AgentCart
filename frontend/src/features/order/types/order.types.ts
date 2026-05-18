import { z } from 'zod';

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  priceAtOrder: number;
  quantity: number;
  subtotal: number;
}

export interface Order {
  id: number;
  status: OrderStatus;
  totalPrice: number;
  items: OrderItem[];
  recipientName: string;
  phone: string;
  address: string;
  addressDetail: string | null;
  createdAt: string;
}

export const orderCreateSchema = z.object({
  cartItemIds: z.array(z.number()).optional(),
  productId: z.number().optional(),
  quantity: z.number().int().min(1).optional(),
  recipientName: z.string().min(1, '수령인 이름을 입력해주세요'),
  phone: z.string().min(1, '전화번호를 입력해주세요'),
  address: z.string().min(1, '주소를 입력해주세요'),
  addressDetail: z.string().optional(),
});

export type OrderCreateInput = z.infer<typeof orderCreateSchema>;