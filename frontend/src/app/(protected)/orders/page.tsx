'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useOrders } from '@/features/order/hooks/use-orders';
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

export default function OrdersPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useOrders(page);

  const orders = data?.data.content ?? [];
  const totalPages = data?.data.totalPages ?? 0;

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold mb-6">주문 내역</h1>

      {isLoading && (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="border rounded-lg p-4 space-y-3 animate-pulse">
              <div className="h-5 bg-gray-100 rounded w-1/3" />
              <div className="h-4 bg-gray-100 rounded w-1/4" />
              <div className="h-4 bg-gray-100 rounded w-1/2" />
            </div>
          ))}
        </div>
      )}

      {isError && (
        <p className="text-red-500 py-8 text-center">주문 내역을 불러오지 못했습니다.</p>
      )}

      {data && orders.length === 0 && (
        <div className="py-16 text-center">
          <p className="text-gray-500 mb-4">주문 내역이 없습니다.</p>
          <Link
            href="/products"
            className="inline-block px-4 py-2 rounded-md border text-sm text-gray-700 hover:bg-gray-50 transition-colors"
          >
            쇼핑하러 가기
          </Link>
        </div>
      )}

      {orders.length > 0 && (
        <div className="space-y-4">
          {orders.map((order) => (
            <Link
              key={order.id}
              href={`/orders/${order.id}`}
              className="block border rounded-lg p-4 hover:bg-gray-50 transition-colors"
            >
              <div className="flex justify-between items-start mb-2">
                <span className="text-sm text-gray-500">주문 #{order.id}</span>
                <span className={`text-xs px-2 py-0.5 rounded-full ${STATUS_CLASS[order.status]}`}>
                  {STATUS_LABEL[order.status]}
                </span>
              </div>
              <p className="font-semibold text-gray-900 mb-1">
                {order.items[0]?.productName ?? '상품 정보 없음'}
                {order.items.length > 1 && ` 외 ${order.items.length - 1}건`}
              </p>
              <div className="flex justify-between items-center">
                <p className="text-sm text-gray-500">
                  {new Date(order.createdAt).toLocaleDateString('ko-KR')}
                </p>
                <p className="text-sm font-semibold text-gray-900">
                  ₩{order.totalPrice.toLocaleString()}
                </p>
              </div>
            </Link>
          ))}

          {totalPages > 1 && (
            <div className="flex justify-center gap-2 pt-4">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 rounded border text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                이전
              </button>
              <span className="px-3 py-1.5 text-sm text-gray-600">
                {page + 1} / {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1.5 rounded border text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                다음
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}