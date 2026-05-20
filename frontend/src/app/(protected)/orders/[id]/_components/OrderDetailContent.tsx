'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useOrder } from '@/features/order/hooks/use-order';
import { useCancelOrder } from '@/features/order/hooks/use-cancel-order';
import { usePay } from '@/features/payment/hooks/use-pay';
import type { OrderStatus } from '@/features/order/types/order.types';

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: '결제 대기',
  CONFIRMED: '주문 확인',
  SHIPPED: '배송 중',
  DELIVERED: '배송 완료',
  CANCELLED: '취소됨',
};

const STATUS_CLASS: Record<OrderStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-700',
  CONFIRMED: 'bg-blue-100 text-blue-700',
  SHIPPED: 'bg-purple-100 text-purple-700',
  DELIVERED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-500',
};

const CANCELLABLE: OrderStatus[] = ['PENDING', 'CONFIRMED'];

export function OrderDetailContent({ orderId }: { orderId: number }) {
  const router = useRouter();

  const { data, isLoading, isError } = useOrder(orderId);
  const { mutate: cancelOrder, isPending: isCancelling } = useCancelOrder({
    onSuccess: () => router.push('/orders'),
  });
  const { mutate: pay, isPending: isPaying } = usePay({
    onSuccess: () => router.refresh(),
  });

  if (isLoading) {
    return (
      <div className="max-w-lg space-y-4 animate-pulse">
        <div className="h-8 bg-gray-100 rounded w-1/3" />
        <div className="h-32 bg-gray-100 rounded" />
        <div className="h-24 bg-gray-100 rounded" />
      </div>
    );
  }

  if (isError || !data) {
    return <p className="text-red-500 py-8 text-center">주문 정보를 불러오지 못했습니다.</p>;
  }

  const order = data.data;
  const isCancellable = CANCELLABLE.includes(order.status);

  return (
    <div className="max-w-lg">
      <div className="flex items-center gap-3 mb-6">
        <Link href="/orders" className="text-sm text-gray-500 hover:text-gray-700">
          ← 주문 내역
        </Link>
        <h1 className="text-2xl font-bold">주문 #{order.id}</h1>
        <span className={`text-xs px-2 py-0.5 rounded-full ${STATUS_CLASS[order.status]}`}>
          {STATUS_LABEL[order.status]}
        </span>
      </div>

      <section className="border rounded-lg p-4 mb-4">
        <h2 className="font-semibold text-gray-900 mb-3">주문 상품</h2>
        <div className="space-y-3">
          {order.items.map((item) => (
            <div key={item.id} className="flex justify-between items-start">
              <div>
                <p className="text-sm font-medium text-gray-900">{item.productName}</p>
                <p className="text-xs text-gray-500">
                  ₩{item.priceAtOrder.toLocaleString()} × {item.quantity}
                </p>
              </div>
              <p className="text-sm font-semibold text-gray-900">
                ₩{item.subtotal.toLocaleString()}
              </p>
            </div>
          ))}
        </div>
        <div className="border-t mt-3 pt-3 flex justify-between">
          <span className="text-sm font-medium text-gray-700">합계</span>
          <span className="text-base font-bold text-gray-900">
            ₩{order.totalPrice.toLocaleString()}
          </span>
        </div>
      </section>

      <section className="border rounded-lg p-4 mb-6">
        <h2 className="font-semibold text-gray-900 mb-3">배송지 정보</h2>
        <dl className="space-y-1 text-sm">
          <div className="flex gap-2">
            <dt className="text-gray-500 w-20 shrink-0">수령인</dt>
            <dd className="text-gray-900">{order.recipientName}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="text-gray-500 w-20 shrink-0">전화번호</dt>
            <dd className="text-gray-900">{order.phone}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="text-gray-500 w-20 shrink-0">주소</dt>
            <dd className="text-gray-900">
              {order.address}
              {order.addressDetail && ` ${order.addressDetail}`}
            </dd>
          </div>
          <div className="flex gap-2">
            <dt className="text-gray-500 w-20 shrink-0">주문 일시</dt>
            <dd className="text-gray-900">
              {new Date(order.createdAt).toLocaleString('ko-KR')}
            </dd>
          </div>
        </dl>
      </section>

      {isCancellable && (
        <div className="flex justify-end gap-2">
          {order.status === 'PENDING' && (
            <button
              onClick={() => pay({ orderId: order.id })}
              disabled={isPaying}
              className="px-4 py-2 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {isPaying ? '결제 중...' : '결제하기'}
            </button>
          )}
          <button
            onClick={() => cancelOrder(order.id)}
            disabled={isCancelling}
            className="px-4 py-2 rounded-md text-sm font-medium bg-red-600 text-white hover:bg-red-700 disabled:opacity-50 transition-colors"
          >
            {isCancelling ? '취소 중...' : '주문 취소'}
          </button>
        </div>
      )}
    </div>
  );
}
