package com.example.userservice.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA 实体：与领域模型 User.java 完全分离
 * 仅负责持久化关注点，不承载业务逻辑
 * 各自独立变化，遵循分离问题关注点原则
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // 地址作为嵌入值对象持久化
    @Embedded
    private AddressEmbeddable address;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // JPA 需要无参构造器
    protected UserJpaEntity() {}

    public UserJpaEntity(String id, String name, String email, AddressEmbeddable address, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.address = address;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public AddressEmbeddable getAddress() { return address; }
    public void setAddress(AddressEmbeddable address) { this.address = address; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
