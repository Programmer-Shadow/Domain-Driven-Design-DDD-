package com.example.userservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 值对象：用户ID
 * 通过强类型 ID 避免原始类型痴迷（Primitive Obsession）
 */
public final class UserId {
    private final String value;

    private UserId(String value) {
        this.value = Objects.requireNonNull(value, "UserId cannot be null");
    }

    public static UserId of(String value) {
        return new UserId(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserId)) return false;
        return value.equals(((UserId) o).value);
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
