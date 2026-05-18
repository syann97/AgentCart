import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OrderNewPage from '../page';

const mockRouterBack = vi.fn();
const mockRouterPush = vi.fn();
const mockCreateOrder = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ back: mockRouterBack, push: mockRouterPush }),
  useSearchParams: vi.fn(),
}));

vi.mock('@/features/order/hooks/use-create-order', () => ({
  useCreateOrder: ({ onSuccess }: { onSuccess?: (data: unknown) => void } = {}) => ({
    mutate: mockCreateOrder.mockImplementation(() =>
      onSuccess?.({ success: true, data: { id: 42 }, timestamp: '' }),
    ),
    isPending: false,
  }),
}));

import { useSearchParams } from 'next/navigation';

function renderWithParams(params: Record<string, string | null>) {
  vi.mocked(useSearchParams).mockReturnValue({
    get: (key: string) => params[key] ?? null,
  } as ReturnType<typeof useSearchParams>);
  return render(<OrderNewPage />);
}

describe('OrderNewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('잘못된 쿼리 파라미터면 에러 메시지를 표시한다', () => {
    renderWithParams({});
    expect(screen.getByText('잘못된 주문 요청입니다.')).toBeInTheDocument();
  });

  it('productId와 quantity가 있으면 바로구매 안내를 표시한다', () => {
    renderWithParams({ productId: '5', quantity: '2' });
    expect(screen.getByText('상품 2개를 바로 주문합니다.')).toBeInTheDocument();
  });

  it('cartItemIds가 있으면 장바구니 주문 안내를 표시한다', () => {
    renderWithParams({ cartItemIds: '1,2,3' });
    expect(screen.getByText('장바구니 상품 3개를 주문합니다.')).toBeInTheDocument();
  });

  it('필수 필드가 비어 있으면 에러 메시지를 표시한다', async () => {
    renderWithParams({ productId: '5', quantity: '2' });
    fireEvent.submit(screen.getByRole('button', { name: '주문하기' }).closest('form')!);
    expect(await screen.findByText('수령인 이름을 입력해주세요')).toBeInTheDocument();
    expect(screen.getByText('전화번호를 입력해주세요')).toBeInTheDocument();
    expect(screen.getByText('주소를 입력해주세요')).toBeInTheDocument();
  });

  it('유효한 폼을 제출하면 createOrder를 호출한다', async () => {
    renderWithParams({ productId: '5', quantity: '2' });

    await userEvent.type(screen.getByLabelText('수령인 이름'), '홍길동');
    await userEvent.type(screen.getByLabelText('전화번호'), '010-1234-5678');
    await userEvent.type(screen.getByLabelText('주소'), '서울시 강남구');
    await userEvent.click(screen.getByRole('button', { name: '주문하기' }));

    expect(mockCreateOrder).toHaveBeenCalledWith({
      productId: 5,
      quantity: 2,
      recipientName: '홍길동',
      phone: '010-1234-5678',
      address: '서울시 강남구',
      addressDetail: undefined,
    });
  });

  it('주문 성공 시 주문 상세 페이지로 이동한다', async () => {
    renderWithParams({ productId: '5', quantity: '2' });

    await userEvent.type(screen.getByLabelText('수령인 이름'), '홍길동');
    await userEvent.type(screen.getByLabelText('전화번호'), '010-1234-5678');
    await userEvent.type(screen.getByLabelText('주소'), '서울시');
    await userEvent.click(screen.getByRole('button', { name: '주문하기' }));

    await waitFor(() =>
      expect(mockRouterPush).toHaveBeenCalledWith('/orders/42'),
    );
  });

  it('취소 버튼 클릭 시 이전 페이지로 돌아간다', async () => {
    renderWithParams({ productId: '5', quantity: '2' });
    await userEvent.click(screen.getByRole('button', { name: '취소' }));
    expect(mockRouterBack).toHaveBeenCalled();
  });

  it('장바구니 주문 시 cartItemIds를 포함하여 createOrder를 호출한다', async () => {
    renderWithParams({ cartItemIds: '10,20' });

    await userEvent.type(screen.getByLabelText('수령인 이름'), '김철수');
    await userEvent.type(screen.getByLabelText('전화번호'), '010-9999-8888');
    await userEvent.type(screen.getByLabelText('주소'), '부산시 해운대구');
    await userEvent.click(screen.getByRole('button', { name: '주문하기' }));

    expect(mockCreateOrder).toHaveBeenCalledWith({
      cartItemIds: [10, 20],
      recipientName: '김철수',
      phone: '010-9999-8888',
      address: '부산시 해운대구',
      addressDetail: undefined,
    });
  });
});