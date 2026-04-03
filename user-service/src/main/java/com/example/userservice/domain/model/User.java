package com.example.userservice.domain.model;

import com.example.userservice.domain.event.UserRegisteredEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 聚合根：用户
 * 职责：
 * 1. 封装用户业务不变量
 * 2. 通过工厂方法和命令方法控制状态变更
 * 3. 收集领域事件
 * 4. 绝不暴露可修改的内部状态
 */
public class User {
    private final UserId id;
    private String name;
    private String email;
    private Address address;
    private final LocalDateTime createdAt;

    // 领域事件列表：由聚合根收集，应用层/基础设施层负责发布
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * 工厂方法：通过此方法创建新用户
     * 触发 UserRegisteredEvent 域事件
     */
    public static User create(String name, String email, Address address) {
        validateEmail(email);
        User user = new User(UserId.generate(), name, email, address, LocalDateTime.now());
        // 发起领域事件
        user.domainEvents.add(new UserRegisteredEvent(user.id.getValue(), email));
        return user;
    }

    /**
     * 私有构造器：强制使用工厂方法创建用户
     */
    private User(UserId id, String name, String email, Address address, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.address = address;
        this.createdAt = createdAt;
    }

    /**
     * 重建方法：供持久化层使用
     * 从数据库恢复时，不触发领域事件
     */
    public static User reconstitute(UserId id, String name, String email,
                                     Address address, LocalDateTime createdAt) {
        return new User(id, name, email, address, createdAt);
    }

    /**
     * 业务方法：修改地址
     */
    public void changeAddress(Address newAddress) {
        this.address = Objects.requireNonNull(newAddress, "Address cannot be null");
    }

    private static void validateEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }
    }

    // 事件管理
    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // Getters（无 Setters，状态只能通过命令方法变更）
    public UserId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Address getAddress() {
        return address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
