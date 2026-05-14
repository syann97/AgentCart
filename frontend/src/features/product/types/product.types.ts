import { z } from 'zod';

export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'SOLD_OUT';

export interface ProductSummary {
  id: number;
  name: string;
  price: number;
  category: string;
  brand: string | null;
  status: ProductStatus;
}

export interface ProductDetail extends ProductSummary {
  description: string | null;
  stock: number;
  createdAt: string;
  updatedAt: string | null;
}

export const productCreateSchema = z.object({
  name: z.string().min(1, '상품명을 입력해주세요'),
  description: z.string().optional(),
  price: z.number().min(0.01, '가격은 0보다 커야 합니다'),
  category: z.string().min(1, '카테고리를 입력해주세요'),
  brand: z.string().optional(),
  stock: z.number().int().min(0, '재고는 0 이상이어야 합니다'),
});

export const productUpdateSchema = productCreateSchema;

export type ProductCreateInput = z.infer<typeof productCreateSchema>;
export type ProductUpdateInput = z.infer<typeof productUpdateSchema>;
