'use client';

import { use } from 'react';
import { OrderDetailContent } from './_components/OrderDetailContent';

interface Props {
  params: Promise<{ id: string }>;
}

export default function OrderDetailPage({ params }: Props) {
  const { id } = use(params);
  return <OrderDetailContent orderId={Number(id)} />;
}
