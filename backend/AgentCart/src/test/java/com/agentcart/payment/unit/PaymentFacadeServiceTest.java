package com.agentcart.payment.unit;

import com.agentcart.exception.ErrorCode;
import com.agentcart.exception.OrderException;
import com.agentcart.exception.PaymentException;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.order.domain.Order;
import com.agentcart.order.domain.OrderStatus;
import com.agentcart.order.repository.OrderRepository;
import com.agentcart.payment.domain.Payment;
import com.agentcart.payment.domain.PaymentStatus;
import com.agentcart.payment.repository.PaymentRepository;
import com.agentcart.payment.service.MockPaymentService;
import com.agentcart.payment.service.PaymentFacadeService;
import com.agentcart.payment.service.PaymentService;
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
class PaymentFacadeServiceTest {

    @Mock private PaymentService paymentService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private com.agentcart.member.repository.MemberRepository memberRepository;

    @InjectMocks
    private PaymentFacadeService paymentFacadeService;

    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 99L;

    private Member member;
    private Order order;

    @BeforeEach
    void setUp() {
        member = new Member("test@test.com", "테스터", "nick", "pass", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);

        order = new Order(member, BigDecimal.valueOf(10000), "홍길동", "010-1234-5678", "서울시", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
    }

    // ── pay ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pay - 결제 성공 시 Payment가 반환되고 Order 상태가 CONFIRMED로 변경된다")
    void pay_success() {
        Payment payment = new Payment(order, order.getTotalPrice());
        payment.complete("mock-key");

        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentService.pay(order)).willReturn(payment);

        Payment result = paymentFacadeService.pay(MEMBER_ID, ORDER_ID);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("pay - 주문이 없으면 ORDER_NOT_FOUND 예외가 발생한다")
    void pay_orderNotFound() {
        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacadeService.pay(MEMBER_ID, ORDER_ID))
                .isInstanceOf(OrderException.class)
                .satisfies(e -> assertThat(((OrderException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("pay - 본인 주문이 아니면 PAYMENT_ACCESS_DENIED 예외가 발생한다")
    void pay_accessDenied() {
        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentFacadeService.pay(OTHER_MEMBER_ID, ORDER_ID))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
    }

    @Test
    @DisplayName("pay - PENDING이 아닌 주문에 결제 시 PAYMENT_ALREADY_COMPLETED 예외가 발생한다")
    void pay_alreadyConfirmed() {
        order.changeStatus(OrderStatus.CONFIRMED);
        given(orderRepository.findByIdWithItems(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentFacadeService.pay(MEMBER_ID, ORDER_ID))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_ALREADY_COMPLETED));
    }

    // ── getPayment ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPayment - 본인 결제 정보를 조회한다")
    void getPayment_success() {
        Payment payment = new Payment(order, order.getTotalPrice());
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);

        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

        Payment result = paymentFacadeService.getPayment(MEMBER_ID, PAYMENT_ID);

        assertThat(result.getId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    @DisplayName("getPayment - 결제가 없으면 PAYMENT_NOT_FOUND 예외가 발생한다")
    void getPayment_notFound() {
        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacadeService.getPayment(MEMBER_ID, PAYMENT_ID))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("getPayment - 타인 결제 조회 시 PAYMENT_ACCESS_DENIED 예외가 발생한다")
    void getPayment_accessDenied() {
        Payment payment = new Payment(order, order.getTotalPrice());
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);

        given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentFacadeService.getPayment(OTHER_MEMBER_ID, PAYMENT_ID))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
    }

    // ── getPaymentByOrder ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentByOrder - 주문 ID로 결제 정보를 조회한다")
    void getPaymentByOrder_success() {
        Payment payment = new Payment(order, order.getTotalPrice());

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

        Payment result = paymentFacadeService.getPaymentByOrder(MEMBER_ID, ORDER_ID);

        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    @DisplayName("getPaymentByOrder - 결제가 없으면 PAYMENT_NOT_FOUND 예외가 발생한다")
    void getPaymentByOrder_paymentNotFound() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacadeService.getPaymentByOrder(MEMBER_ID, ORDER_ID))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("getPaymentByOrder - 주문이 없으면 ORDER_NOT_FOUND 예외가 발생한다")
    void getPaymentByOrder_orderNotFound() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacadeService.getPaymentByOrder(MEMBER_ID, ORDER_ID))
                .isInstanceOf(OrderException.class)
                .satisfies(e -> assertThat(((OrderException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("getPaymentByOrder - 타인 주문 결제 조회 시 PAYMENT_ACCESS_DENIED 예외가 발생한다")
    void getPaymentByOrder_accessDenied() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentFacadeService.getPaymentByOrder(OTHER_MEMBER_ID, ORDER_ID))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> assertThat(((PaymentException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_ACCESS_DENIED));
    }
}