package com.agentcart.payment.service;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.OrderException;
import com.agentcart.exception.PaymentException;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.order.domain.Order;
import com.agentcart.order.domain.OrderStatus;
import com.agentcart.order.repository.OrderRepository;
import com.agentcart.payment.domain.Payment;
import com.agentcart.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentFacadeService {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Payment pay(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        checkAccess(memberId, order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new PaymentException(ErrorCode.PAYMENT_ALREADY_COMPLETED);
        }

        Payment payment = paymentService.pay(order);
        order.changeStatus(OrderStatus.CONFIRMED);
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long memberId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));

        checkAccess(memberId, payment.getOrder());
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        checkAccess(memberId, order);

        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void checkAccess(Long memberId, Order order) {
        if (!order.getMember().getId().equals(memberId)) {
            throw new PaymentException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
    }
}