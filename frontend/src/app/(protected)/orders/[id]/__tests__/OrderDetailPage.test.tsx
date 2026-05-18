import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrderDetailContent } from '../page';

const mockRouterPush = vi.fn();
const mockRouterRefresh = vi.fn();
const mockCancelOrder = vi.fn();
const mockPay = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockRouterPush, refresh: mockRouterRefresh }),
}));

vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/features/order/hooks/use-order', () => ({
  useOrder: vi.fn(),
}));

vi.mock('@/features/order/hooks/use-cancel-order', () => ({
  useCancelOrder: ({ onSuccess }: { onSuccess?: () => void } = {}) => ({
    mutate: mockCancelOrder.mockImplementation(() => onSuccess?.()),
    isPending: false,
  }),
}));

vi.mock('@/features/payment/hooks/use-pay', () => ({
  usePay: ({ onSuccess }: { onSuccess?: () => void } = {}) => ({
    mutate: mockPay.mockImplementation(() => onSuccess?.()),
    isPending: false,
  }),
}));

import { useOrder } from '@/features/order/hooks/use-order';

const baseOrder = {
  id: 1,
  status: 'PENDING' as const,
  totalPrice: 10000,
  items: [{ id: 1, productId: 10, productName: '노트북', priceAtOrder: 10000, quantity: 1, subtotal: 10000 }],
  recipientName: '홍길동',
  phone: '010-1234-5678',
  address: '서울시',
  addressDetail: null,
  createdAt: '2024-01-01T00:00:00',
};

function mockOrder(overrides = {}) {
  vi.mocked(useOrder).mockReturnValue({
    data: { success: true, data: { ...baseOrder, ...overrides }, timestamp: '' },
    isLoading: false,
    isError: false,
  } as ReturnType<typeof useOrder>);
}

function renderContent(orderId = 1) {
  return render(<OrderDetailContent orderId={orderId} />);
}

describe('OrderDetailContent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('주문 정보를 표시한다', () => {
    mockOrder();
    renderContent();

    expect(screen.getByText('주문 #1')).toBeInTheDocument();
    expect(screen.getByText('노트북')).toBeInTheDocument();
    expect(screen.getByText('홍길동')).toBeInTheDocument();
  });

  it('PENDING 상태이면 결제하기 버튼이 표시된다', () => {
    mockOrder({ status: 'PENDING' });
    renderContent();

    expect(screen.getByText('결제하기')).toBeInTheDocument();
  });

  it('CONFIRMED 상태이면 결제하기 버튼이 표시되지 않는다', () => {
    mockOrder({ status: 'CONFIRMED' });
    renderContent();

    expect(screen.queryByText('결제하기')).not.toBeInTheDocument();
    expect(screen.getByText('주문 취소')).toBeInTheDocument();
  });

  it('DELIVERED 상태이면 결제하기·주문취소 버튼이 모두 표시되지 않는다', () => {
    mockOrder({ status: 'DELIVERED' });
    renderContent();

    expect(screen.queryByText('결제하기')).not.toBeInTheDocument();
    expect(screen.queryByText('주문 취소')).not.toBeInTheDocument();
  });

  it('CANCELLED 상태이면 결제하기·주문취소 버튼이 모두 표시되지 않는다', () => {
    mockOrder({ status: 'CANCELLED' });
    renderContent();

    expect(screen.queryByText('결제하기')).not.toBeInTheDocument();
    expect(screen.queryByText('주문 취소')).not.toBeInTheDocument();
  });

  it('SHIPPED 상태이면 결제하기 버튼이 표시되지 않고 주문취소 버튼도 표시되지 않는다', () => {
    mockOrder({ status: 'SHIPPED' });
    renderContent();

    expect(screen.queryByText('결제하기')).not.toBeInTheDocument();
    expect(screen.queryByText('주문 취소')).not.toBeInTheDocument();
  });

  it('결제하기 버튼 클릭 시 pay를 orderId와 함께 호출한다', async () => {
    mockOrder({ status: 'PENDING' });
    renderContent();

    await userEvent.click(screen.getByText('결제하기'));

    expect(mockPay).toHaveBeenCalledWith({ orderId: 1 });
  });

  it('결제 성공 시 페이지를 갱신한다', async () => {
    mockOrder({ status: 'PENDING' });
    renderContent();

    await userEvent.click(screen.getByText('결제하기'));

    expect(mockRouterRefresh).toHaveBeenCalled();
  });

  it('주문 취소 버튼 클릭 시 cancelOrder를 호출한다', async () => {
    mockOrder({ status: 'PENDING' });
    renderContent();

    await userEvent.click(screen.getByText('주문 취소'));

    expect(mockCancelOrder).toHaveBeenCalledWith(1);
  });

  it('주문 취소 성공 시 /orders로 이동한다', async () => {
    mockOrder({ status: 'PENDING' });
    renderContent();

    await userEvent.click(screen.getByText('주문 취소'));

    expect(mockRouterPush).toHaveBeenCalledWith('/orders');
  });

  it('로딩 중이면 스켈레톤을 표시한다', () => {
    vi.mocked(useOrder).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as ReturnType<typeof useOrder>);

    const { container } = renderContent();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('에러 시 에러 메시지를 표시한다', () => {
    vi.mocked(useOrder).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as ReturnType<typeof useOrder>);

    renderContent();
    expect(screen.getByText('주문 정보를 불러오지 못했습니다.')).toBeInTheDocument();
  });
});