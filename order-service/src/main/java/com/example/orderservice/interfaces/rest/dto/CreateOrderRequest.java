package com.example.orderservice.interfaces.rest.dto;

import java.util.List;

/**
 * 请求 DTO：创建订单
 */
public record CreateOrderRequest(
    String customerId,
    List<OrderItemRequest> items
) {
    public record OrderItemRequest(
        String productId,
        String productName,
        double price,
        int quantity
    ) {}
}
