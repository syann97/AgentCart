package com.agentcart.payment.unit;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.PaymentException;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.order.domain.Order;
import com.agentcart.payment.domain.Payment;
import com.agentcart.payment.domain.PaymentStatus;
import com.agentcart.payment.repository.PaymentRepository;
import com.agentcart.payment.service.MockPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MockPaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private MockPaymentService mockPaymentService;

    private Order order;

    @BeforeEach
    void setUp() {
        Member member = new Member("test@test.com", "테스터", "nick", "pass", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 1L);

        order = new Order(member, BigDecimal.valueOf(10000), "홍길동", "010-1234-5678", "서울시", null);
        ReflectionTestUtils.setField(order, "id", 100L);
    }

    @Test
    @DisplayName("결제 성공 시 COMPLETED 상태의 Payment를 반환한다")
    void pay_success() {
        given(paymentRepository.findByOrderId(100L)).willReturn(Optional.empty());
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

        Payment result = mockPaymentService.pay(order);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getPaymentKey()).startsWith("mock-");
        assertThat(result.getPaidAt()).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    @DisplayName("이미 결제된 주문에 재결제 시 PAYMENT_ALREADY_COMPLETED 예외가 발생한다")
    void pay_alreadyCompleted() {
        Payment existing = new Payment(order, order.getTotalPrice());
        existing.complete("mock-existing-key");
        given(paymentRepository.findByOrderId(100L)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> mockPaymentService.pay(order))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_ALREADY_COMPLETED));
    }
}