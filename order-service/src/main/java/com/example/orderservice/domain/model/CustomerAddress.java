package com.example.orderservice.domain.model;

import java.util.Objects;

/**
 * 值对象：客户地址（Order 域中的地址表示）
 * 注意：这是 Order 领域对客户地址的内部表示
 * 与 User Service 的 Address 不同，是防腐层的转换目标
 */
public record CustomerAddress(
    String street,
    String city,
    String province,
    String zipCode,
    String country
) {
    public CustomerAddress {
        Objects.requireNonNull(city, "City is required");
        Objects.requireNonNull(country, "Country is required");
    }
}
