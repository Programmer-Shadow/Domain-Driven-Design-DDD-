package com.example.orderservice.domain.model;

/**
 * 值对象（枚举）：订单状态
 */
public enum OrderStatus {
    PENDING("待支付"),
    PAID("已支付"),
    SHIPPED("已发货"),
    DELIVERED("已交付"),
    CANCELLED("已取消");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
