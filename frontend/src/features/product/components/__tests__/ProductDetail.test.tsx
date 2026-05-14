import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProductDetail } from '../ProductDetail';
import type { ProductDetail as ProductDetailType } from '../../types/product.types';

vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const product: ProductDetailType = {
  id: 1,
  name: 'Laptop',
  price: 999000,
  category: 'electronics',
  brand: 'Samsung',
  status: 'ACTIVE',
  description: '고성능 노트북입니다.',
  stock: 10,
  createdAt: '2024-01-01T00:00:00',
  updatedAt: null,
};

describe('ProductDetail', () => {
  it('상품명, 브랜드·카테고리, 설명, 가격, 재고를 표시한다', () => {
    render(<ProductDetail product={product} />);

    expect(screen.getByText('Laptop')).toBeInTheDocument();
    expect(screen.getByText('Samsung · electronics')).toBeInTheDocument();
    expect(screen.getByText('고성능 노트북입니다.')).toBeInTheDocument();
    expect(screen.getByText(`₩${product.price.toLocaleString()}`)).toBeInTheDocument();
    expect(screen.getByText('재고: 10개')).toBeInTheDocument();
  });

  it('ACTIVE 상태 배지를 표시한다', () => {
    render(<ProductDetail product={product} />);
    expect(screen.getByText('판매중')).toBeInTheDocument();
  });

  it('SOLD_OUT 상태 배지를 표시하고 재고 텍스트를 강조한다', () => {
    render(<ProductDetail product={{ ...product, status: 'SOLD_OUT', stock: 0 }} />);

    expect(screen.getByText('품절')).toBeInTheDocument();
    expect(screen.getByText('재고: 0개')).toHaveClass('text-red-500');
  });

  it('재고가 0이면 재고 텍스트를 강조한다', () => {
    render(<ProductDetail product={{ ...product, stock: 0 }} />);
    expect(screen.getByText('재고: 0개')).toHaveClass('text-red-500');
  });

  it('description이 null이면 설명을 렌더링하지 않는다', () => {
    render(<ProductDetail product={{ ...product, description: null }} />);
    expect(screen.queryByText('고성능 노트북입니다.')).not.toBeInTheDocument();
  });

  it('brand가 null이면 카테고리만 표시한다', () => {
    render(<ProductDetail product={{ ...product, brand: null }} />);
    expect(screen.getByText('electronics')).toBeInTheDocument();
    expect(screen.queryByText('Samsung · electronics')).not.toBeInTheDocument();
  });

  it('목록으로 링크가 /products를 가리킨다', () => {
    render(<ProductDetail product={product} />);
    expect(screen.getByRole('link', { name: '목록으로' })).toHaveAttribute('href', '/products');
  });
});