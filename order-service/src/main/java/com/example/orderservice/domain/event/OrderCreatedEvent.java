package com.example.orderservice.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 领域事件：订单创建事件
 */
public class OrderCreatedEvent {
    private final String orderId;
    private final String customerId;
    private final BigDecimal totalAmount;
    private final LocalDateTime occurredAt;

    public OrderCreatedEvent(String orderId, String customerId, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.occurredAt = LocalDateTime.now();
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
