import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductForm } from '../ProductForm';

const onSubmit = vi.fn();
const onCancel = vi.fn();

const defaultProps = {
  onSubmit,
  onCancel,
  isPending: false,
};

describe('ProductForm', () => {
  beforeEach(() => {
    onSubmit.mockReset();
    onCancel.mockReset();
  });

  it('이름, 설명, 가격, 카테고리, 브랜드, 재고 필드와 취소·저장 버튼을 렌더링한다', () => {
    render(<ProductForm {...defaultProps} />);

    expect(screen.getByLabelText('이름 *')).toBeInTheDocument();
    expect(screen.getByLabelText('설명')).toBeInTheDocument();
    expect(screen.getByLabelText('가격 *')).toBeInTheDocument();
    expect(screen.getByLabelText('카테고리 *')).toBeInTheDocument();
    expect(screen.getByLabelText('브랜드')).toBeInTheDocument();
    expect(screen.getByLabelText('재고 *')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '취소' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument();
  });

  it('빈 폼 제출 시 필수 필드 에러를 표시한다', async () => {
    render(<ProductForm {...defaultProps} />);

    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(screen.getByText('상품명을 입력해주세요')).toBeInTheDocument();
      expect(screen.getByText('카테고리를 입력해주세요')).toBeInTheDocument();
    });
  });

  it('가격이 0이면 에러를 표시한다', async () => {
    render(<ProductForm {...defaultProps} />);

    await userEvent.type(screen.getByLabelText('이름 *'), '테스트');
    await userEvent.type(screen.getByLabelText('카테고리 *'), 'electronics');
    await userEvent.type(screen.getByLabelText('가격 *'), '0');
    await userEvent.type(screen.getByLabelText('재고 *'), '10');
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(screen.getByText('가격은 0보다 커야 합니다')).toBeInTheDocument();
    });
  });

  it('재고가 음수이면 에러를 표시한다', async () => {
    render(<ProductForm {...defaultProps} />);

    await userEvent.type(screen.getByLabelText('이름 *'), '테스트');
    await userEvent.type(screen.getByLabelText('카테고리 *'), 'electronics');
    await userEvent.type(screen.getByLabelText('가격 *'), '1000');
    await userEvent.type(screen.getByLabelText('재고 *'), '-1');
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(screen.getByText('재고는 0 이상이어야 합니다')).toBeInTheDocument();
    });
  });

  it('유효한 값 제출 시 onSubmit을 호출한다', async () => {
    render(<ProductForm {...defaultProps} />);

    await userEvent.type(screen.getByLabelText('이름 *'), '테스트 상품');
    await userEvent.type(screen.getByLabelText('가격 *'), '1000');
    await userEvent.type(screen.getByLabelText('카테고리 *'), 'electronics');
    await userEvent.type(screen.getByLabelText('재고 *'), '10');
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          name: '테스트 상품',
          price: 1000,
          category: 'electronics',
          stock: 10,
        }),
        expect.anything(),
      );
    });
  });

  it('defaultValues가 있으면 폼 필드를 preset한다', () => {
    render(
      <ProductForm
        {...defaultProps}
        defaultValues={{ name: 'Laptop', price: 999000, category: 'electronics', stock: 10 }}
      />,
    );

    expect(screen.getByLabelText('이름 *')).toHaveValue('Laptop');
    expect(screen.getByLabelText('카테고리 *')).toHaveValue('electronics');
  });

  it('취소 버튼 클릭 시 onCancel을 호출한다', async () => {
    render(<ProductForm {...defaultProps} />);

    await userEvent.click(screen.getByRole('button', { name: '취소' }));

    expect(onCancel).toHaveBeenCalledOnce();
  });

  it('serverError가 있으면 표시한다', () => {
    render(<ProductForm {...defaultProps} serverError="상품 등록에 실패했습니다." />);

    expect(screen.getByText('상품 등록에 실패했습니다.')).toBeInTheDocument();
  });
});