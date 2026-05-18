'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { useCreateOrder } from '@/features/order/hooks/use-create-order';

export default function OrderNewPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const cartItemIdsParam = searchParams.get('cartItemIds');
  const productIdParam = searchParams.get('productId');
  const quantityParam = searchParams.get('quantity');

  const cartItemIds = cartItemIdsParam
    ? cartItemIdsParam.split(',').map(Number).filter(Boolean)
    : undefined;
  const productId = productIdParam ? Number(productIdParam) : undefined;
  const quantity = quantityParam ? Number(quantityParam) : undefined;

  const isCartOrder = cartItemIds && cartItemIds.length > 0;
  const isDirectOrder = !isCartOrder && productId && quantity;

  const [recipientName, setRecipientName] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [addressDetail, setAddressDetail] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const { mutate: createOrder, isPending } = useCreateOrder({
    onSuccess: (data) => {
      router.push(`/orders/${data.data.id}`);
    },
  });

  if (!isCartOrder && !isDirectOrder) {
    return (
      <div className="max-w-lg py-16 text-center">
        <p className="text-gray-500">잘못된 주문 요청입니다.</p>
      </div>
    );
  }

  function validate() {
    const next: Record<string, string> = {};
    if (!recipientName.trim()) next.recipientName = '수령인 이름을 입력해주세요';
    if (!phone.trim()) next.phone = '전화번호를 입력해주세요';
    if (!address.trim()) next.address = '주소를 입력해주세요';
    return next;
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const next = validate();
    if (Object.keys(next).length > 0) {
      setErrors(next);
      return;
    }
    setErrors({});

    const body = {
      ...(isCartOrder ? { cartItemIds } : { productId, quantity }),
      recipientName: recipientName.trim(),
      phone: phone.trim(),
      address: address.trim(),
      addressDetail: addressDetail.trim() || undefined,
    };

    createOrder(body);
  }

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-bold mb-6">주문서 작성</h1>

      <div className="border rounded-lg p-4 mb-6 bg-gray-50 text-sm text-gray-700">
        {isCartOrder && (
          <p>장바구니 상품 {cartItemIds.length}개를 주문합니다.</p>
        )}
        {isDirectOrder && (
          <p>상품 {quantity}개를 바로 주문합니다.</p>
        )}
      </div>

      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <h2 className="text-lg font-semibold">배송지 정보</h2>

        <div>
          <label htmlFor="recipientName" className="block text-sm font-medium text-gray-700 mb-1">
            수령인 이름
          </label>
          <input
            id="recipientName"
            type="text"
            value={recipientName}
            onChange={(e) => setRecipientName(e.target.value)}
            className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {errors.recipientName && (
            <p className="mt-1 text-xs text-red-500">{errors.recipientName}</p>
          )}
        </div>

        <div>
          <label htmlFor="phone" className="block text-sm font-medium text-gray-700 mb-1">
            전화번호
          </label>
          <input
            id="phone"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {errors.phone && <p className="mt-1 text-xs text-red-500">{errors.phone}</p>}
        </div>

        <div>
          <label htmlFor="address" className="block text-sm font-medium text-gray-700 mb-1">
            주소
          </label>
          <input
            id="address"
            type="text"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {errors.address && <p className="mt-1 text-xs text-red-500">{errors.address}</p>}
        </div>

        <div>
          <label htmlFor="addressDetail" className="block text-sm font-medium text-gray-700 mb-1">
            상세 주소 <span className="text-gray-400 font-normal">(선택)</span>
          </label>
          <input
            id="addressDetail"
            type="text"
            value={addressDetail}
            onChange={(e) => setAddressDetail(e.target.value)}
            className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="pt-2 flex gap-2 justify-end">
          <button
            type="button"
            onClick={() => router.back()}
            className="px-4 py-2 rounded-md border text-sm text-gray-700 hover:bg-gray-50 transition-colors"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={isPending}
            className="px-4 py-2 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            {isPending ? '주문 중...' : '주문하기'}
          </button>
        </div>
      </form>
    </div>
  );
}