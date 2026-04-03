package com.example.userservice.domain.event;

import java.time.LocalDateTime;

/**
 * 领域事件：用户注册事件
 * 聚合根创建时发起此事件，由应用层发布到消息总线
 */
public class UserRegisteredEvent {
    private final String userId;
    private final String email;
    private final LocalDateTime occurredAt;

    public UserRegisteredEvent(String userId, String email) {
        this.userId = userId;
        this.email = email;
        this.occurredAt = LocalDateTime.now();
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
