package com.example.orderservice.application;

import com.example.orderservice.domain.model.*;
import com.example.orderservice.domain.repository.OrderRepository;
import com.example.orderservice.application.port.UserServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 应用层：订单应用服务
 *
 * 关键设计：
 * - 依赖 UserServicePort 接口，不依赖具体实现
 * - 返回的 CustomerInfo 已经是内部领域对象，不是外部 DTO
 * - 应用层对防腐层、Feign Client、外部模型一无所知
 */
@Service
@Transactional
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderRepository orderRepository;
    // 这是 ACL 端口接口，防腐层的门面
    // 注意：依赖接口，不依赖 UserServiceAdapter 实现类
    private final UserServicePort userServicePort;

    public OrderApplicationService(OrderRepository orderRepository, UserServicePort userServicePort) {
        this.orderRepository = orderRepository;
        this.userServicePort = userServicePort;
    }

    /**
     * 用例：创建订单
     *
     * 流程：
     * 1. 通过 ACL 获取客户信息（返回内部领域对象 CustomerInfo）
     * 2. 构建订单项值对象
     * 3. 创建订单聚合根
     * 4. 持久化
     *
     * 这个流程中，防腐层隐藏了所有外部服务细节：
     * - Feign Client 的 HTTP 调用
     * - ExternalUserDTO 的外部结构
     * - 模型转换的复杂性
     *
     * 应用层只和 CustomerInfo（内部值对象）打交道
     */
    public String createOrder(CreateOrderCommand command) {
        log.info("Creating order for customer: {}", command.customerId());

        // 1. 通过 ACL（防腐层）获取客户信息
        //    返回的是内部领域对象 CustomerInfo，完全隔离了外部 User Service 的细节
        CustomerInfo customerInfo = userServicePort
                .findCustomerById(command.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(
                    "Customer not found: " + command.customerId()
                ));

        // 2. 构建订单项（纯领域操作）
        List<OrderItem> items = command.items().stream()
                .map(item -> new OrderItem(
                    item.productId(),
                    item.productName(),
                    new Money(item.price()),
                    item.quantity()
                ))
                .collect(Collectors.toList());

        // 3. 创建订单聚合根（领域层核心逻辑）
        //    此时客户信息已经是内部快照，与外部 User Service 解耦
        Order order = Order.create(customerInfo, items);

        // 4. 持久化
        orderRepository.save(order);

        // 5. 清除并发布领域事件（实际项目中使用 Spring ApplicationEventPublisher）
        order.clearDomainEvents();

        log.info("Order created successfully. OrderId: {}", order.getId().getValue());
        return order.getId().getValue();
    }

    /**
     * 查询：获取订单信息
     */
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(String orderId) {
        return orderRepository.findById(OrderId.of(orderId));
    }

    /**
     * 查询：获取客户的所有订单
     */
    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    /**
     * 业务操作：支付订单
     */
    public void payOrder(String orderId) {
        Order order = orderRepository.findById(OrderId.of(orderId))
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        order.pay();
        orderRepository.save(order);
    }
}
