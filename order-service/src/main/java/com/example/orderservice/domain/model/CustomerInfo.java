package com.example.orderservice.domain.model;

import java.util.Objects;

/**
 * 值对象：客户信息（Order 域的客户快照）
 *
 * 防腐层设计的关键体现：
 * - 这个类完全用 Order 领域的语言定义（不是 User Service 的语言）
 * - 只包含 Order 业务需要的客户信息（下单时的快照）
 * - 字段名遵循 Order 领域的通用语言
 * - 外部 User Service 模型的变化不会影响这个类
 *
 * 对比外部 ExternalUserDTO：
 *   外部: username   -> 内部: customerName（语义更明确）
 *   外部: 扁平地址    -> 内部: CustomerAddress 值对象（结构化）
 */
public record CustomerInfo(
    String customerId,
    String customerName,    // 不是 "username"，而是 Order 域的语言
    String email,
    CustomerAddress defaultAddress
) {
    public CustomerInfo {
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(customerName, "customerName is required");
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(defaultAddress, "defaultAddress is required");
    }
}
