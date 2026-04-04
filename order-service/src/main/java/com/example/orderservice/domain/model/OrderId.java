package com.example.orderservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 值对象：订单 ID
 */
public final class OrderId {
    private final String value;

    private OrderId(String value) {
        this.value = Objects.requireNonNull(value, "OrderId cannot be null");
    }

    public static OrderId of(String value) {
        return new OrderId(value);
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderId)) return false;
        return value.equals(((OrderId) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
