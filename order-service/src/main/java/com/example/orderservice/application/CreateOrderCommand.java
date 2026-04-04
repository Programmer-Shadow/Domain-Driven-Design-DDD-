package com.example.orderservice.application;

import java.util.List;

/**
 * 命令对象：创建订单
 */
public record CreateOrderCommand(
    String customerId,
    List<OrderItemDto> items
) {
    public record OrderItemDto(
        String productId,
        String productName,
        double price,
        int quantity
    ) {}
}
