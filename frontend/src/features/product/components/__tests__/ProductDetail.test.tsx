import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductDetail } from '../ProductDetail';
import type { ProductDetail as ProductDetailType } from '../../types/product.types';

const mockAddToCart = vi.fn();

vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/features/cart/hooks/use-add-cart-item', () => ({
  useAddCartItem: ({ onSuccess }: { onSuccess?: () => void } = {}) => ({
    mutate: mockAddToCart.mockImplementation(() => onSuccess?.()),
    isPending: false,
  }),
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
  beforeEach(() => {
    vi.clearAllMocks();
  });

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

  it('ACTIVE 상품에 수량 입력 필드와 최대 수량이 표시된다', () => {
    render(<ProductDetail product={product} />);

    expect(screen.getByLabelText('수량')).toBeInTheDocument();
    expect(screen.getByText('최대 10개')).toBeInTheDocument();
  });

  it('재고 3개이면 최대 수량이 3개로 표시된다', () => {
    render(<ProductDetail product={{ ...product, stock: 3 }} />);
    expect(screen.getByText('최대 3개')).toBeInTheDocument();
  });

  it('SOLD_OUT 상품에는 수량 입력 필드가 표시되지 않는다', () => {
    render(<ProductDetail product={{ ...product, status: 'SOLD_OUT', stock: 0 }} />);
    expect(screen.queryByLabelText('수량')).not.toBeInTheDocument();
  });

  it('수량 입력 후 장바구니에 담기 시 선택한 수량으로 호출된다', async () => {
    render(<ProductDetail product={product} />);

    const input = screen.getByLabelText('수량');
    await userEvent.clear(input);
    await userEvent.type(input, '3');
    fireEvent.blur(input);

    await userEvent.click(screen.getByText('장바구니에 담기'));

    expect(mockAddToCart).toHaveBeenCalledWith({ productId: 1, quantity: 3 });
  });

  it('최대 수량 초과 입력 시 blur에서 최대값으로 보정된다', () => {
    render(<ProductDetail product={product} />);

    const input = screen.getByLabelText('수량') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '99' } });
    fireEvent.blur(input);

    expect(input.value).toBe('10');
  });

  it('0 또는 음수 입력 시 blur에서 1로 보정된다', () => {
    render(<ProductDetail product={product} />);

    const input = screen.getByLabelText('수량') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '0' } });
    fireEvent.blur(input);

    expect(input.value).toBe('1');
  });
});