package com.example.orderservice.domain.model;

import com.example.orderservice.domain.event.OrderCreatedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 聚合根：订单
 * 职责：
 * 1. 管理订单及其行项的不变量
 * 2. 控制状态变更
 * 3. 收集领域事件
 */
public class Order {
    private final OrderId id;
    private final CustomerInfo customerInfo;  // 客户快照，与 User Service 解耦
    private List<OrderItem> items;
    private OrderStatus status;
    private Money totalAmount;
    private final LocalDateTime createdAt;
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * 工厂方法：创建订单
     */
    public static Order create(CustomerInfo customerInfo, List<OrderItem> items) {
        Objects.requireNonNull(customerInfo, "CustomerInfo is required");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }

        OrderId orderId = OrderId.generate();
        Money total = calculateTotal(items);
        Order order = new Order(orderId, customerInfo, new ArrayList<>(items),
                                OrderStatus.PENDING, total, LocalDateTime.now());

        // 创建领域事件
        order.domainEvents.add(new OrderCreatedEvent(
            orderId.getValue(),
            customerInfo.customerId(),
            total.amount()
        ));
        return order;
    }

    private static Money calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.ZERO, Money::add);
    }

    /**
     * 私有构造器
     */
    private Order(OrderId id, CustomerInfo customerInfo, List<OrderItem> items,
                  OrderStatus status, Money totalAmount, LocalDateTime createdAt) {
        this.id = id;
        this.customerInfo = customerInfo;
        this.items = items;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    /**
     * 重建方法：供持久化层使用，不触发领域事件
     */
    public static Order reconstitute(OrderId id, CustomerInfo customerInfo,
                                     List<OrderItem> items, OrderStatus status,
                                     Money totalAmount, LocalDateTime createdAt) {
        return new Order(id, customerInfo, new ArrayList<>(items), status, totalAmount, createdAt);
    }

    /**
     * 业务方法：支付订单
     */
    public void pay() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be paid");
        }
        this.status = OrderStatus.PAID;
        // 在此处可以添加 OrderPaidEvent
    }

    /**
     * 业务方法：发货
     */
    public void ship() {
        if (this.status != OrderStatus.PAID) {
            throw new IllegalStateException("Only PAID orders can be shipped");
        }
        this.status = OrderStatus.SHIPPED;
    }

    // 事件管理
    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // Getters
    public OrderId getId() { return id; }
    public CustomerInfo getCustomerInfo() { return customerInfo; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public OrderStatus getStatus() { return status; }
    public Money getTotalAmount() { return totalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
