export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface Payment {
  id: number;
  orderId: number;
  status: PaymentStatus;
  amount: number;
  paymentKey: string | null;
  paidAt: string | null;
  createdAt: string;
}

export interface PaymentCreateInput {
  orderId: number;
}