package com.example.orderservice.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 响应 DTO：订单信息
 */
public record OrderResponse(
    String orderId,
    String customerId,
    String customerName,
    String email,
    List<OrderItemResponse> items,
    String status,
    BigDecimal totalAmount,
    LocalDateTime createdAt
) {
    public record OrderItemResponse(
        String productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal
    ) {}
}
