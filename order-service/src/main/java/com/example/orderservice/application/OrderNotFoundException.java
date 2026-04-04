package com.example.orderservice.application;

/**
 * 异常：订单不存在
 */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
