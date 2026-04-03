package com.example.userservice.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * 响应 DTO：用户信息（外部合同）
 * 这是 User Service 对外暴露的 API 合同
 * Order Service 的 ACL 必须处理这个结构，防止其污染内部领域模型
 */
public record UserResponse(
    String userId,
    String username,              // 注意：外部叫 "username"
    String email,
    String street,
    String city,
    String province,
    String zipCode,
    String country,
    LocalDateTime registeredAt
) {}
