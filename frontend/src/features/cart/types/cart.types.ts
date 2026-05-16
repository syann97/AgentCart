import { z } from 'zod';

export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'SOLD_OUT';

export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productPrice: number;
  productStatus: ProductStatus;
  productStock: number;
  quantity: number;
  subtotal: number;
}

export interface Cart {
  id: number;
  items: CartItem[];
  totalPrice: number;
}

export const cartItemAddSchema = z.object({
  productId: z.number().int().positive('상품을 선택해주세요'),
  quantity: z.number().int().min(1, '수량은 1 이상이어야 합니다'),
});

export const cartItemUpdateSchema = z.object({
  quantity: z.number().int().min(1, '수량은 1 이상이어야 합니다'),
});

export type CartItemAddInput = z.infer<typeof cartItemAddSchema>;
export type CartItemUpdateInput = z.infer<typeof cartItemUpdateSchema>;
