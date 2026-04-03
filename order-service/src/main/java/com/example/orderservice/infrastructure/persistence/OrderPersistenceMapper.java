package com.example.orderservice.infrastructure.persistence;

import com.example.orderservice.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 持久化映射器：Order <-> OrderJpaEntity
 */
@Component
public class OrderPersistenceMapper {

    public OrderJpaEntity toEntity(Order order) {
        CustomerAddress customerAddress = order.getCustomerInfo().defaultAddress();
        CustomerAddressEmbeddable addressEmbeddable = new CustomerAddressEmbeddable(
            customerAddress.street(),
            customerAddress.city(),
            customerAddress.province(),
            customerAddress.zipCode(),
            customerAddress.country()
        );

        List<OrderItemJpaEntity> itemEntities = order.getItems().stream()
                .map(item -> new OrderItemJpaEntity(
                    item.productId(),
                    item.productName(),
                    item.unitPrice().amount(),
                    item.quantity()
                ))
                .collect(Collectors.toList());

        return new OrderJpaEntity(
            order.getId().getValue(),
            order.getCustomerInfo().customerId(),
            order.getCustomerInfo().customerName(),
            order.getCustomerInfo().email(),
            addressEmbeddable,
            itemEntities,
            order.getStatus().name(),
            order.getTotalAmount().amount(),
            order.getCreatedAt()
        );
    }

    public Order toDomain(OrderJpaEntity entity) {
        CustomerAddressEmbeddable addressEmbeddable = entity.getAddress();
        CustomerAddress customerAddress = new CustomerAddress(
            addressEmbeddable.getStreet(),
            addressEmbeddable.getCity(),
            addressEmbeddable.getProvince(),
            addressEmbeddable.getZipCode(),
            addressEmbeddable.getCountry()
        );

        CustomerInfo customerInfo = new CustomerInfo(
            entity.getCustomerId(),
            entity.getCustomerName(),
            entity.getEmail(),
            customerAddress
        );

        List<OrderItem> items = entity.getItems().stream()
                .map(itemEntity -> new OrderItem(
                    itemEntity.getProductId(),
                    itemEntity.getProductName(),
                    new Money(itemEntity.getUnitPrice()),
                    itemEntity.getQuantity()
                ))
                .collect(Collectors.toList());

        OrderStatus status = OrderStatus.valueOf(entity.getStatus());

        return Order.reconstitute(
            OrderId.of(entity.getId()),
            customerInfo,
            items,
            status,
            new Money(entity.getTotalAmount()),
            entity.getCreatedAt()
        );
    }
}
