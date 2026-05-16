import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CartItemRow } from '../CartItemRow';
import type { CartItem } from '../../types/cart.types';

const mockUpdateItem = vi.fn();
const mockRemoveItem = vi.fn();

vi.mock('../../hooks/use-update-cart-item', () => ({
  useUpdateCartItem: () => ({ mutate: mockUpdateItem, isPending: false }),
}));

vi.mock('../../hooks/use-remove-cart-item', () => ({
  useRemoveCartItem: () => ({ mutate: mockRemoveItem, isPending: false }),
}));

const activeItem: CartItem = {
  id: 1,
  productId: 10,
  productName: '테스트 상품',
  productPrice: 10000,
  productStatus: 'ACTIVE',
  productStock: 20,
  quantity: 2,
  subtotal: 20000,
};

describe('CartItemRow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('상품명, 단가, 수량, 소계를 표시한다', () => {
    render(<CartItemRow item={activeItem} />);

    expect(screen.getByText('테스트 상품')).toBeInTheDocument();
    expect(screen.getByText('₩10,000')).toBeInTheDocument();
    expect((screen.getByLabelText('수량') as HTMLInputElement).value).toBe('2');
    expect(screen.getByText('소계: ₩20,000')).toBeInTheDocument();
  });

  it('ACTIVE 상태 배지를 표시한다', () => {
    render(<CartItemRow item={activeItem} />);
    expect(screen.getByText('판매중')).toBeInTheDocument();
  });

  it('SOLD_OUT 상품은 수량 버튼과 입력 필드가 비활성화된다', () => {
    render(<CartItemRow item={{ ...activeItem, productStatus: 'SOLD_OUT' }} />);

    expect(screen.getByLabelText('수량 감소')).toBeDisabled();
    expect(screen.getByLabelText('수량 증가')).toBeDisabled();
    expect(screen.getByLabelText('수량')).toBeDisabled();
  });

  it('[+] 클릭 시 수량 +1로 updateItem을 호출한다', async () => {
    render(<CartItemRow item={activeItem} />);
    await userEvent.click(screen.getByLabelText('수량 증가'));
    expect(mockUpdateItem).toHaveBeenCalledWith({ quantity: 3 });
  });

  it('수량 > 1에서 [-] 클릭 시 수량 -1로 updateItem을 호출한다', async () => {
    render(<CartItemRow item={activeItem} />);
    await userEvent.click(screen.getByLabelText('수량 감소'));
    expect(mockUpdateItem).toHaveBeenCalledWith({ quantity: 1 });
  });

  it('수량 1에서 [-] 클릭 시 삭제 확인 다이얼로그가 표시된다', async () => {
    render(<CartItemRow item={{ ...activeItem, quantity: 1 }} />);
    await userEvent.click(screen.getByLabelText('수량 감소'));

    expect(screen.getByRole('heading', { name: '상품 삭제' })).toBeInTheDocument();
    expect(mockUpdateItem).not.toHaveBeenCalled();
  });

  it('텍스트 입력 후 blur 시 유효한 수량이면 updateItem을 호출한다', () => {
    render(<CartItemRow item={activeItem} />);

    const input = screen.getByLabelText('수량') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '5' } });
    fireEvent.blur(input);

    expect(mockUpdateItem).toHaveBeenCalledWith({ quantity: 5 });
    expect(screen.queryByText(/최대|이상/)).not.toBeInTheDocument();
  });

  it('재고 초과 입력 시 에러 메시지가 표시되고 updateItem이 호출되지 않는다', () => {
    render(<CartItemRow item={{ ...activeItem, productStock: 3 }} />);

    const input = screen.getByLabelText('수량') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '99' } });
    fireEvent.blur(input);

    expect(screen.getByText('최대 3개까지 주문 가능합니다.')).toBeInTheDocument();
    expect(mockUpdateItem).not.toHaveBeenCalled();
  });

  it('주문 상한(10개) 초과 시 에러 메시지가 표시된다', async () => {
    render(<CartItemRow item={{ ...activeItem, productStock: 20, quantity: 10 }} />);
    await userEvent.click(screen.getByLabelText('수량 증가'));

    expect(screen.getByText('최대 10개까지 주문 가능합니다.')).toBeInTheDocument();
    expect(mockUpdateItem).not.toHaveBeenCalled();
  });

  it('0 이하 입력 시 에러 메시지가 표시된다', () => {
    render(<CartItemRow item={activeItem} />);

    const input = screen.getByLabelText('수량') as HTMLInputElement;
    fireEvent.change(input, { target: { value: '0' } });
    fireEvent.blur(input);

    expect(screen.getByText('수량은 1개 이상이어야 합니다.')).toBeInTheDocument();
    expect(mockUpdateItem).not.toHaveBeenCalled();
  });

  it('[삭제] 버튼 클릭 시 삭제 확인 다이얼로그가 표시된다', async () => {
    render(<CartItemRow item={activeItem} />);
    await userEvent.click(screen.getByText('삭제'));
    expect(screen.getByRole('heading', { name: '상품 삭제' })).toBeInTheDocument();
  });

  it('삭제 확인 시 removeItem이 호출된다', async () => {
    render(<CartItemRow item={activeItem} />);
    await userEvent.click(screen.getByText('삭제'));

    const buttons = screen.getAllByRole('button', { name: '삭제' });
    await userEvent.click(buttons[buttons.length - 1]);

    expect(mockRemoveItem).toHaveBeenCalledWith(activeItem.id);
  });

  it('삭제 다이얼로그에서 취소 시 다이얼로그가 닫힌다', async () => {
    render(<CartItemRow item={activeItem} />);
    await userEvent.click(screen.getByText('삭제'));
    await userEvent.click(screen.getByText('취소'));

    expect(screen.queryByRole('heading', { name: '상품 삭제' })).not.toBeInTheDocument();
  });
});