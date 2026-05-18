package com.agentcart.payment.service;

import com.agentcart.order.domain.Order;
import com.agentcart.payment.domain.Payment;

public interface PaymentService {

    Payment pay(Order order);
}