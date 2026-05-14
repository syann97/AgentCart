import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductCard } from '../ProductCard';
import type { ProductSummary } from '../../types/product.types';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

const product: ProductSummary = {
  id: 1,
  name: 'Laptop',
  price: 999000,
  category: 'electronics',
  brand: 'Samsung',
  status: 'ACTIVE',
};

describe('ProductCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('상품명, 브랜드, 가격, 카테고리를 표시한다', () => {
    render(<ProductCard product={product} />);

    expect(screen.getByText('Laptop')).toBeInTheDocument();
    expect(screen.getByText('Samsung')).toBeInTheDocument();
    expect(screen.getByText(`₩${product.price.toLocaleString()}`)).toBeInTheDocument();
    expect(screen.getByText('electronics')).toBeInTheDocument();
  });

  it('ACTIVE 상태 배지를 표시한다', () => {
    render(<ProductCard product={product} />);
    expect(screen.getByText('판매중')).toBeInTheDocument();
  });

  it('SOLD_OUT 상태 배지를 표시한다', () => {
    render(<ProductCard product={{ ...product, status: 'SOLD_OUT' }} />);
    expect(screen.getByText('품절')).toBeInTheDocument();
  });

  it('INACTIVE 상태 배지를 표시한다', () => {
    render(<ProductCard product={{ ...product, status: 'INACTIVE' }} />);
    expect(screen.getByText('비활성')).toBeInTheDocument();
  });

  it('클릭 시 /products/{id}로 이동한다', async () => {
    render(<ProductCard product={product} />);
    await userEvent.click(screen.getByRole('article'));
    expect(mockPush).toHaveBeenCalledWith('/products/1');
  });

  it('brand가 null이면 브랜드를 렌더링하지 않는다', () => {
    render(<ProductCard product={{ ...product, brand: null }} />);
    expect(screen.queryByText('Samsung')).not.toBeInTheDocument();
  });
});
