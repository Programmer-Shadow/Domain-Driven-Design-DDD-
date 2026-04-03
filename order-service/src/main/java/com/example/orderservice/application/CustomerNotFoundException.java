package com.example.orderservice.application;

/**
 * 异常：客户不存在
 */
public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String message) {
        super(message);
    }
}
