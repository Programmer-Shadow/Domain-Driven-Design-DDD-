package com.example.orderservice.domain.model;

import java.util.Objects;

/**
 * 值对象：订单项
 * 订单的一条商品行项
 */
public record OrderItem(
    String productId,
    String productName,
    Money unitPrice,
    int quantity
) {
    public OrderItem {
        Objects.requireNonNull(productId, "ProductId is required");
        Objects.requireNonNull(productName, "ProductName is required");
        Objects.requireNonNull(unitPrice, "UnitPrice is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    /**
     * 计算行项小计
     */
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
