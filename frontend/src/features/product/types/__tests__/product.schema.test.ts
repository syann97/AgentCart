import { describe, it, expect } from 'vitest';
import { productCreateSchema } from '../product.types';

describe('productCreateSchema', () => {
  const valid = {
    name: '테스트 상품',
    price: 9.99,
    category: 'electronics',
    stock: 10,
  };

  it('유효한 입력은 통과한다', () => {
    expect(productCreateSchema.safeParse(valid).success).toBe(true);
  });

  it('빈 이름은 실패한다', () => {
    const result = productCreateSchema.safeParse({ ...valid, name: '' });
    expect(result.success).toBe(false);
  });

  it('가격이 0 이하이면 실패한다', () => {
    const result = productCreateSchema.safeParse({ ...valid, price: 0 });
    expect(result.success).toBe(false);
  });

  it('빈 카테고리는 실패한다', () => {
    const result = productCreateSchema.safeParse({ ...valid, category: '' });
    expect(result.success).toBe(false);
  });

  it('음수 재고는 실패한다', () => {
    const result = productCreateSchema.safeParse({ ...valid, stock: -1 });
    expect(result.success).toBe(false);
  });
});
