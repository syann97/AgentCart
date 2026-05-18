package com.agentcart.payment.service;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.PaymentException;
import com.agentcart.order.domain.Order;
import com.agentcart.payment.domain.Payment;
import com.agentcart.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MockPaymentService implements PaymentService {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public Payment pay(Order order) {
        paymentRepository.findByOrderId(order.getId()).ifPresent(p -> {
            throw new PaymentException(ErrorCode.PAYMENT_ALREADY_COMPLETED);
        });

        Payment payment = new Payment(order, order.getTotalPrice());
        try {
            String mockKey = "mock-" + UUID.randomUUID();
            payment.complete(mockKey);
        } catch (Exception e) {
            payment.fail();
            paymentRepository.save(payment);
            throw new PaymentException(ErrorCode.PAYMENT_FAILED);
        }

        return paymentRepository.save(payment);
    }
}