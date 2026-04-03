package com.example.orderservice.infrastructure.acl.userservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * 外部 DTO：镜像 User Service 的 UserResponse 结构
 *
 * 防腐层关键设计：
 * - @JsonIgnoreProperties(ignoreUnknown=true)：忽略外部新增字段，防止外部变更破坏本服务
 * - 这个类只存在于 infrastructure/acl 包内，绝不外泄到 domain 或 application 层
 * - 此类会被 UserTranslator 转换为内部的 CustomerInfo 值对象
 *
 * 字段命名遵循外部 API 合同（来自 User Service）：
 * - "username" 而非 "name"
 * - 扁平的地址字段
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalUserDTO(
    String userId,
    String username,
    String email,
    String street,
    String city,
    String province,
    String zipCode,
    String country,
    LocalDateTime registeredAt
) {}
