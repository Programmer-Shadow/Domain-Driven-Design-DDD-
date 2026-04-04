package com.example.orderservice.domain.repository;

import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderId;
import java.util.Optional;
import java.util.List;

/**
 * 仓储接口 - 纯领域接口
 */
public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId orderId);

    List<Order> findByCustomerId(String customerId);
}
